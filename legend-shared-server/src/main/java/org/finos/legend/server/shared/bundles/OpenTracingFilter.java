// Copyright 2020 Goldman Sachs
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

package org.finos.legend.server.shared.bundles;

import static io.opentracing.contrib.jaxrs2.internal.SpanWrapper.PROPERTY_NAME;

import com.google.common.collect.ImmutableList;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.jaxrs2.internal.SpanWrapper;
import io.opentracing.contrib.jaxrs2.server.ServerHeadersExtractTextMap;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MultivaluedHashMap;

@SuppressWarnings("WeakerAccess")
public class OpenTracingFilter implements Filter
{
  public static final String SCOPE_PROPERTY = OpenTracingFilter.class.getName() + ".Scope";
  public static ServerSpanDecorator STANDARD_TAGS =
      new ServerSpanDecorator()
      {
        @Override
        public void decorateRequest(HttpServletRequest request, Span span)
        {
          Tags.HTTP_METHOD.set(span, request.getMethod());

          String url = request.getRequestURI();
          if (url != null)
          {
            Tags.HTTP_URL.set(span, url);
          }
        }

        @Override
        public void decorateResponse(HttpServletResponse response, Span span)
        {
          Tags.HTTP_STATUS.set(span, response.getStatus());
        }
      };
  private final Tracer tracer;
  private final List<ServerSpanDecorator> spanDecorators;
  private List<String> skipUrls = ImmutableList.of();

  public OpenTracingFilter(Tracer tracer, List<ServerSpanDecorator> spanDecorators)
  {
    this.tracer = tracer;
    this.spanDecorators = spanDecorators;
  }

  private static void addExceptionLogs(Span span, Throwable throwable)
  {
    Tags.ERROR.set(span, true);
    Map<String, Object> errorLogs = new HashMap<>(2);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.object", throwable);
    span.log(errorLogs);
  }

  @Override
  public void init(FilterConfig filterConfig)
  {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
  {

    HttpServletResponse httpResponse = (HttpServletResponse) response;
    HttpServletRequest httpRequest = (HttpServletRequest) request;

    String uri = httpRequest.getRequestURI();
    if (skipUrls.contains(uri))
    {
      chain.doFilter(request, response);
      return;
    }

    Tracer.SpanBuilder spanBuilder =
        tracer
            .buildSpan(httpRequest.getPathInfo())
            .ignoreActiveSpan()
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);

    MultivaluedHashMap<String, String> headerMap = new MultivaluedHashMap<>();
    Collections.list(httpRequest.getHeaderNames())
        .forEach(header -> headerMap.put(header, Collections.list(httpRequest.getHeaders(header))));

    spanBuilder.asChildOf(
        tracer.extract(Format.Builtin.HTTP_HEADERS, new ServerHeadersExtractTextMap(headerMap)));

    Scope scope = spanBuilder.startActive(false);
    Span span = scope.span();

    if (spanDecorators != null)
    {
      for (ServerSpanDecorator decorator : spanDecorators)
      {
        decorator.decorateRequest(httpRequest, span);
      }
    }

    httpRequest.setAttribute(SCOPE_PROPERTY, scope);
    httpRequest.setAttribute(PROPERTY_NAME, new SpanWrapper(span, scope));

    Map<String, String> props = new HashMap<>();
    tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMapAdapter(props));
    props.forEach(((HttpServletResponse) response)::addHeader);

    try
    {
      chain.doFilter(request, response);

      if (spanDecorators != null)
      {
        for (ServerSpanDecorator decorator : spanDecorators)
        {
          decorator.decorateResponse(httpResponse, span);
        }
      }

    } catch (Exception ex)
    {
      Tags.HTTP_STATUS.set(span, httpResponse.getStatus());
      addExceptionLogs(span, ex);
      throw ex;
    } finally
    {
      scope.close();
      if (request.isAsyncStarted())
      {
        request.getAsyncContext().addListener(new SpanFinisher(span), request, response);
      } else
      {
        span.finish();

        if (tracer.activeSpan() != null)
        {
          throw new RuntimeException(
              "There is still an open ActiveTracing span. "
                  + "This probably means a scope is unclosed.");
        }
      }
    }
  }

  @Override
  public void destroy()
  {
  }

  public OpenTracingFilter withSkipPaths(List<String> skipUrls)
  {
    this.skipUrls = skipUrls;
    return this;
  }

  static class SpanFinisher implements AsyncListener
  {
    private final Span span;

    SpanFinisher(Span span)
    {
      this.span = span;
    }

    @Override
    public void onComplete(AsyncEvent event)
    {
      span.finish();
    }

    @Override
    public void onTimeout(AsyncEvent event)
    {
    }

    @Override
    public void onError(AsyncEvent event)
    {
      // this handler is called when exception is thrown in async handler
      // note that exception logs are added in filter not here
    }

    @Override
    public void onStartAsync(AsyncEvent event)
    {
    }
  }
}
