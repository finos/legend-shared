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

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Locale;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.http.adapter.HttpActionAdapter;
import org.pac4j.jax.rs.filters.DefaultJaxRsHttpActionAdapter;
import org.pac4j.jax.rs.pac4j.JaxRsContext;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.http.adapter.JEEHttpActionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads what is left of a servlet request's body, then answers as the wrapped adapter would.
 *
 * <p>pac4j answers without reading the body: a 401 challenge, a 403, a redirect to log in. Jetty 12
 * then makes a few non-blocking reads of the unread body and, if it has not all arrived, closes the
 * connection outright. The unread bytes turn the close into a TCP reset, so a client still sending a
 * large body, such as Apache HttpClient 4, which writes the whole body before reading, fails with
 * "Broken pipe" and never reads the 401 it should authenticate after. Jetty 9.4 read the rest of
 * the body itself. It has to be read before the response is written: for {@code sendError}, Jetty's
 * own attempt comes first and, when it fails, replaces the rest of the body with an error.
 *
 * <p>Left unread: a body the client holds back until it gets {@code 100 Continue}, as Jetty
 * closes that connection cleanly, and a body over {@link #MAX_DRAINED_BYTES}, past which Jetty
 * closes the connection as before, rather than let an unauthenticated client hold a thread for an
 * unbounded upload.
 */
public final class RequestBodyDrainingHttpActionAdapter implements HttpActionAdapter
{
  public static final long MAX_DRAINED_BYTES = 256L * 1024 * 1024;

  private static final Logger LOGGER = LoggerFactory.getLogger(RequestBodyDrainingHttpActionAdapter.class);

  private final HttpActionAdapter delegate;
  private final long maxDrainedBytes;

  /**
   * Answers with {@code delegate} once the body is read.
   *
   * @param delegate the adapter to answer with, or null for dropwizard-pac4j's default
   */
  public RequestBodyDrainingHttpActionAdapter(HttpActionAdapter delegate)
  {
    this(delegate, MAX_DRAINED_BYTES);
  }

  RequestBodyDrainingHttpActionAdapter(HttpActionAdapter delegate, long maxDrainedBytes)
  {
    this.delegate = delegate;
    this.maxDrainedBytes = maxDrainedBytes;
  }

  @Override
  public Object adapt(HttpAction action, WebContext context)
  {
    if (context instanceof JEEContext)
    {
      drain(((JEEContext) context).getNativeRequest(), maxDrainedBytes);
    }
    if (delegate != null)
    {
      return delegate.adapt(action, context);
    }
    // dropwizard-pac4j's default, which a configured adapter takes the place of
    return context instanceof JaxRsContext
        ? DefaultJaxRsHttpActionAdapter.INSTANCE.adapt(action, context)
        : JEEHttpActionAdapter.INSTANCE.adapt(action, context);
  }

  static void drain(HttpServletRequest request, long maxBytes)
  {
    String expect = request.getHeader("Expect");
    if (expect != null && expect.toLowerCase(Locale.ROOT).contains("100-continue"))
    {
      return;
    }
    long declared = request.getContentLengthLong();
    boolean chunked = declared < 0 && request.getHeader("Transfer-Encoding") != null;
    if (declared == 0 || (declared < 0 && !chunked) || declared > maxBytes)
    {
      return;
    }
    try
    {
      long read;
      try
      {
        read = skip(request.getInputStream(), maxBytes);
      }
      catch (IllegalStateException readerTaken)
      {
        read = skip(request.getReader(), maxBytes);
      }
      LOGGER.debug("Read {} unread request body bytes of {} {} before answering", read, request.getMethod(), request.getRequestURI());
    }
    catch (IOException e)
    {
      // The client went away; Jetty deals with the connection.
      LOGGER.debug("Could not read the rest of the request body of {} {}", request.getMethod(), request.getRequestURI(), e);
    }
  }

  private static long skip(InputStream in, long max) throws IOException
  {
    byte[] buffer = new byte[8192];
    long read = 0;
    for (int n; read < max && (n = in.read(buffer, 0, (int) Math.min(buffer.length, max - read))) >= 0; )
    {
      read += n;
    }
    return read;
  }

  private static long skip(Reader in, long max) throws IOException
  {
    char[] buffer = new char[8192];
    long read = 0;
    for (int n; read < max && (n = in.read(buffer, 0, (int) Math.min(buffer.length, max - read))) >= 0; )
    {
      read += n;
    }
    return read;
  }
}
