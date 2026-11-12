// Copyright 2026 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.server.shared.staticserver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * pac4j answers before it reads the request body. Jetty 12 then gives up on the unread body and
 * closes the connection with a reset, so a client still sending a large body, such as Apache
 * HttpClient 4, which writes the whole body before reading, fails with "Broken pipe" and never
 * sees the 401 it should authenticate after. These tests write requests over a plain socket, body
 * first, the way such a client does.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class ServerUnreadRequestBodyTest
{
  private static final int LARGE_BODY = 32 * 1024 * 1024;
  private static final int ATTEMPTS = 3;

  private static final FakeIdentityProvider IDENTITY_PROVIDER = startIdentityProvider();

  public static final DropwizardAppExtension<StaticServerConfiguration> APP = new DropwizardAppExtension<>(
      Server.class, writeConfiguration());

  @AfterAll
  public static void stopIdentityProvider()
  {
    IDENTITY_PROVIDER.close();
  }

  @Test
  public void aScriptPostingALargeBodyIsToldItIsUnauthorized() throws Exception
  {
    for (int i = 0; i < ATTEMPTS; i++)
    {
      try (Connection connection = connect())
      {
        assertEquals("401", connection.post("/ui/studio/edit", "application/json", LARGE_BODY, false));
      }
    }
  }

  @Test
  public void aScriptPostingALargeChunkedBodyIsToldItIsUnauthorized() throws Exception
  {
    for (int i = 0; i < ATTEMPTS; i++)
    {
      try (Connection connection = connect())
      {
        assertEquals("401", connection.post("/ui/studio/edit", "application/json", LARGE_BODY, true));
      }
    }
  }

  @Test
  public void aBrowserPostingALargeBodyIsSentToLogIn() throws Exception
  {
    for (int i = 0; i < ATTEMPTS; i++)
    {
      try (Connection connection = connect())
      {
        // pac4j redirects anything but a GET with 303 See Other
        assertEquals("303", connection.post("/ui/studio/edit", ServerIndirectLoginTest.BROWSER_ACCEPT, LARGE_BODY, false));
      }
    }
  }

  @Test
  public void theConnectionCarriesTheNextRequestAfterTheChallenge() throws Exception
  {
    try (Connection connection = connect())
    {
      assertEquals("401", connection.post("/ui/studio/edit", "application/json", LARGE_BODY, false));
      assertEquals("401", connection.post("/ui/studio/edit", "application/json", 2, false));
    }
  }

  @Test
  public void aBrowserFormPostReadAsParametersIsSentToLogIn() throws Exception
  {
    // pac4j reads client_name with getParameter, which takes a form body before the adapter runs
    try (Connection connection = connect())
    {
      assertEquals("303", connection.post("/ui/studio/edit", ServerIndirectLoginTest.BROWSER_ACCEPT,
          "application/x-www-form-urlencoded", 100 * 1024, false));
    }
  }

  @Test
  public void aSmallBodyIsToldItIsUnauthorized() throws Exception
  {
    try (Connection connection = connect())
    {
      assertEquals("401", connection.post("/ui/studio/edit", "application/json", 2, false));
    }
  }

  @Test
  public void aClientExpecting100ContinueIsToldItIsUnauthorizedWithoutSendingItsBody() throws Exception
  {
    try (Connection connection = connect())
    {
      connection.writeHead("POST", "/ui/studio/edit", "application/json", "application/json",
          "Content-Length: " + LARGE_BODY + "\r\nExpect: 100-continue\r\n");
      assertEquals("401", connection.readResponse());
    }
  }

  private static Connection connect() throws IOException
  {
    return new Connection(new Socket("localhost", APP.getLocalPort()));
  }

  private static final class Connection implements AutoCloseable
  {
    private final Socket socket;
    private final OutputStream out;
    private final InputStream in;

    private Connection(Socket socket) throws IOException
    {
      this.socket = socket;
      socket.setSoTimeout(30_000);
      this.out = socket.getOutputStream();
      this.in = new BufferedInputStream(socket.getInputStream());
    }

    /**
     * @return the response's status code, or what the connection failed with
     */
    String post(String path, String accept, int bodyLength, boolean chunked) throws IOException
    {
      return post(path, accept, "application/json", bodyLength, chunked);
    }

    String post(String path, String accept, String contentType, int bodyLength, boolean chunked) throws IOException
    {
      try
      {
        writeHead("POST", path, accept, contentType, chunked ? "Transfer-Encoding: chunked\r\n" : "Content-Length: " + bodyLength + "\r\n");
        byte[] block = new byte[8192];
        Arrays.fill(block, (byte) 'a');
        for (int written = 0; written < bodyLength; written += block.length)
        {
          int length = Math.min(block.length, bodyLength - written);
          if (chunked)
          {
            out.write((Integer.toHexString(length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(block, 0, length);
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
          }
          else
          {
            out.write(block, 0, length);
          }
        }
        if (chunked)
        {
          out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        out.flush();
      }
      catch (IOException e)
      {
        return "failed writing the body: " + e;
      }
      try
      {
        return readResponse();
      }
      catch (IOException e)
      {
        return "failed reading the response: " + e;
      }
    }

    void writeHead(String method, String path, String accept, String contentType, String bodyHeaders) throws IOException
    {
      out.write((method + " " + path + " HTTP/1.1\r\n"
          + "Host: localhost\r\n"
          + "Accept: " + accept + "\r\n"
          + "Content-Type: " + contentType + "\r\n"
          + bodyHeaders
          + "\r\n").getBytes(StandardCharsets.US_ASCII));
      out.flush();
    }

    /**
     * @return the status code of the next final response, after reading its body, so the connection
     *     is ready for another request
     */
    String readResponse() throws IOException
    {
      String statusLine = readLine();
      if (statusLine == null)
      {
        return "connection closed before a response";
      }
      long contentLength = 0;
      boolean chunked = false;
      for (String header = readLine(); header != null && !header.isEmpty(); header = readLine())
      {
        String lower = header.toLowerCase(Locale.ROOT);
        if (lower.startsWith("content-length:"))
        {
          contentLength = Long.parseLong(header.substring("content-length:".length()).trim());
        }
        else if (lower.startsWith("transfer-encoding:") && lower.contains("chunked"))
        {
          chunked = true;
        }
      }
      if (chunked)
      {
        for (long size = Long.parseLong(readLine().trim(), 16); size > 0; size = Long.parseLong(readLine().trim(), 16))
        {
          skip(size);
          readLine();
        }
        readLine();
      }
      else
      {
        skip(contentLength);
      }
      return statusLine.split(" ")[1];
    }

    private void skip(long count) throws IOException
    {
      for (long left = count; left > 0; left--)
      {
        if (in.read() < 0)
        {
          throw new IOException("response body ended early");
        }
      }
    }

    private String readLine() throws IOException
    {
      ByteArrayOutputStream line = new ByteArrayOutputStream();
      for (int b = in.read(); b != '\n'; b = in.read())
      {
        if (b < 0)
        {
          return line.size() == 0 ? null : line.toString(StandardCharsets.US_ASCII);
        }
        if (b != '\r')
        {
          line.write(b);
        }
      }
      return line.toString(StandardCharsets.US_ASCII);
    }

    @Override
    public void close() throws IOException
    {
      socket.close();
    }
  }

  private static FakeIdentityProvider startIdentityProvider()
  {
    try
    {
      return new FakeIdentityProvider();
    }
    catch (IOException e)
    {
      throw new UncheckedIOException(e);
    }
  }

  private static String writeConfiguration()
  {
    try
    {
      return IDENTITY_PROVIDER.writeConfiguration(Files.createTempDirectory("unread-request-body"), null).toString();
    }
    catch (IOException e)
    {
      throw new UncheckedIOException(e);
    }
  }
}
