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

package org.finos.legend.server.pac4j.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.pac4j.core.exception.http.UnauthorizedAction;
import org.pac4j.core.http.adapter.HttpActionAdapter;
import org.pac4j.jax.rs.pac4j.JaxRsContext;
import org.pac4j.jee.context.JEEContext;

public class RequestBodyDrainingHttpActionAdapterTest
{
  private static final Object ANSWERED = new Object();

  @Test
  public void theBodyIsReadBeforeTheWrappedAdapterAnswers()
  {
    Body body = new Body(100_000);
    HttpServletRequest request = request(100_000, null, null, body);
    AtomicInteger leftWhenAnswered = new AtomicInteger(-1);
    HttpActionAdapter delegate = (action, context) ->
    {
      leftWhenAnswered.set(body.available());
      return ANSWERED;
    };

    Object answer = new RequestBodyDrainingHttpActionAdapter(delegate).adapt(new UnauthorizedAction(), context(request));

    assertSame(ANSWERED, answer);
    assertEquals(0, leftWhenAnswered.get());
  }

  @Test
  public void aChunkedBodyIsReadToo()
  {
    Body body = new Body(100_000);
    HttpServletRequest request = request(-1, "chunked", null, body);

    adapt(request, RequestBodyDrainingHttpActionAdapter.MAX_DRAINED_BYTES);

    assertEquals(0, body.available());
  }

  @Test
  public void aBodyHeldBackFor100ContinueIsNotAskedFor() throws IOException
  {
    HttpServletRequest request = request(100_000, null, "100-continue", new Body(100_000));

    adapt(request, RequestBodyDrainingHttpActionAdapter.MAX_DRAINED_BYTES);

    verify(request, never()).getInputStream();
  }

  @Test
  public void aRequestWithoutABodyIsNotRead() throws IOException
  {
    HttpServletRequest noLength = request(-1, null, null, new Body(0));
    HttpServletRequest emptyBody = request(0, null, null, new Body(0));

    adapt(noLength, RequestBodyDrainingHttpActionAdapter.MAX_DRAINED_BYTES);
    adapt(emptyBody, RequestBodyDrainingHttpActionAdapter.MAX_DRAINED_BYTES);

    verify(noLength, never()).getInputStream();
    verify(emptyBody, never()).getInputStream();
  }

  @Test
  public void aBodyDeclaredOverTheCapIsLeftToJetty() throws IOException
  {
    HttpServletRequest request = request(10_000, null, null, new Body(10_000));

    adapt(request, 4_096);

    verify(request, never()).getInputStream();
  }

  @Test
  public void aChunkedBodyIsReadNoFurtherThanTheCap()
  {
    Body body = new Body(10_000);
    HttpServletRequest request = request(-1, "chunked", null, body);

    adapt(request, 4_096);

    assertEquals(10_000 - 4_096, body.available());
  }

  @Test
  public void aBodyAFilterTookAsAReaderIsReadThroughTheReader() throws IOException
  {
    StringReader chars = new StringReader("a".repeat(100_000));
    HttpServletRequest request = request(100_000, null, null, null);
    when(request.getInputStream()).thenThrow(new IllegalStateException("getReader() was called"));
    when(request.getReader()).thenReturn(new BufferedReader(chars));

    adapt(request, RequestBodyDrainingHttpActionAdapter.MAX_DRAINED_BYTES);

    assertEquals(-1, chars.read());
  }

  @Test
  public void aClientThatGoesAwayStillGetsAnAnswer() throws IOException
  {
    HttpServletRequest request = request(100_000, null, null, null);
    ServletInputStream failing = mock(ServletInputStream.class);
    when(failing.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IOException("Connection reset"));
    when(request.getInputStream()).thenReturn(failing);

    Object answer = new RequestBodyDrainingHttpActionAdapter((action, context) -> ANSWERED).adapt(new UnauthorizedAction(), context(request));

    assertSame(ANSWERED, answer);
  }

  @Test
  public void withoutAWrappedAdapterAServletRequestIsAnsweredAsDropwizardPac4jWould() throws IOException
  {
    HttpServletRequest request = request(100_000, null, null, new Body(100_000));
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    new RequestBodyDrainingHttpActionAdapter(null).adapt(new UnauthorizedAction(), new JEEContext(request, response));

    verify(response).sendError(401);
  }

  @Test
  public void aJaxRsContextIsHandedOnUntouched()
  {
    JaxRsContext context = mock(JaxRsContext.class);
    AtomicInteger calls = new AtomicInteger();

    Object answer = new RequestBodyDrainingHttpActionAdapter((action, c) ->
    {
      assertSame(context, c);
      calls.incrementAndGet();
      return ANSWERED;
    }).adapt(new UnauthorizedAction(), context);

    assertSame(ANSWERED, answer);
    assertEquals(1, calls.get());
  }

  private static void adapt(HttpServletRequest request, long maxDrainedBytes)
  {
    new RequestBodyDrainingHttpActionAdapter((action, context) -> ANSWERED, maxDrainedBytes).adapt(new UnauthorizedAction(), context(request));
  }

  private static JEEContext context(HttpServletRequest request)
  {
    return new JEEContext(request, mock(HttpServletResponse.class));
  }

  private static HttpServletRequest request(long contentLength, String transferEncoding, String expect, Body body)
  {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/api/user/x");
    when(request.getContentLengthLong()).thenReturn(contentLength);
    when(request.getHeader("Transfer-Encoding")).thenReturn(transferEncoding);
    when(request.getHeader("Expect")).thenReturn(expect);
    if (body != null)
    {
      try
      {
        when(request.getInputStream()).thenReturn(body);
      }
      catch (IOException e)
      {
        throw new AssertionError(e);
      }
    }
    return request;
  }

  private static final class Body extends ServletInputStream
  {
    private final ByteArrayInputStream bytes;

    private Body(int length)
    {
      this.bytes = new ByteArrayInputStream(new byte[length]);
    }

    @Override
    public int read()
    {
      return bytes.read();
    }

    @Override
    public int read(byte[] b, int off, int len)
    {
      return bytes.read(b, off, len);
    }

    @Override
    public int available()
    {
      return bytes.available();
    }

    @Override
    public boolean isFinished()
    {
      return bytes.available() == 0;
    }

    @Override
    public boolean isReady()
    {
      return true;
    }

    @Override
    public void setReadListener(ReadListener readListener)
    {
      throw new UnsupportedOperationException();
    }
  }
}
