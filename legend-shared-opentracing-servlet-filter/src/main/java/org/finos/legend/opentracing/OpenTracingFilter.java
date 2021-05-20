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

package org.finos.legend.opentracing;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.jaxrs2.internal.SpanWrapper;
import io.opentracing.contrib.jaxrs2.server.ServerHeadersExtractTextMap;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;
import io.prometheus.client.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String SCOPE_PROPERTY = OpenTracingFilter.class.getName() + ".Scope";
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTracingFilter.class);
    private static final Gauge ACTIVE_SPAN_EXCEPTION = Gauge.build().name("activeSpan_exceptions").help("Metric for open active span errors").register();

    private final Tracer tracer;
    private final List<ServerSpanDecorator> spanDecorators;
    private final Set<String> skipUrls;

    public OpenTracingFilter(Tracer tracer, List<ServerSpanDecorator> spanDecorators, Collection<String> skipUrls)
    {
        this.tracer = tracer;
        this.spanDecorators = (spanDecorators == null) ? Collections.emptyList() : spanDecorators;
        this.skipUrls = (skipUrls == null) ? Collections.emptySet() : new HashSet<>(skipUrls);
    }

    public OpenTracingFilter(Tracer tracer, List<ServerSpanDecorator> spanDecorators)
    {
        this(tracer, spanDecorators, null);
    }

    public OpenTracingFilter(Tracer tracer)
    {
        this(tracer, null, null);
    }

    @Override
    public void init(FilterConfig filterConfig)
    {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String uri = httpRequest.getRequestURI();
        if (this.skipUrls.contains(uri))
        {
            chain.doFilter(request, response);
            return;
        }

        Tracer.SpanBuilder spanBuilder = this.tracer.buildSpan(httpRequest.getPathInfo())
                .ignoreActiveSpan()
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);

        MultivaluedHashMap<String, String> headerMap = new MultivaluedHashMap<>();
        Collections.list(httpRequest.getHeaderNames())
                .forEach(header -> headerMap.put(header, Collections.list(httpRequest.getHeaders(header))));

        spanBuilder.asChildOf(this.tracer.extract(Format.Builtin.HTTP_HEADERS, new ServerHeadersExtractTextMap(headerMap)));

        try (Scope scope = spanBuilder.startActive(false))
        {
            Span span = scope.span();
            try
            {
                // Update request
                try
                {
                    this.spanDecorators.forEach(d -> d.decorateRequest(httpRequest, span));

                    httpRequest.setAttribute(SCOPE_PROPERTY, scope);
                    httpRequest.setAttribute(SpanWrapper.PROPERTY_NAME, new SpanWrapper(span, scope));

                    Map<String, String> props = new HashMap<>();
                    this.tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMapAdapter(props));
                    props.forEach(httpResponse::addHeader);
                }
                catch (Throwable t)
                {
                    LOGGER.warn("Error updating request (trace id: {})", span.context().toTraceId(), t);
                    throw t;
                }

                // Handle request
                chain.doFilter(request, response);

                // Update response
                try
                {
                    this.spanDecorators.forEach(d -> d.decorateResponse(httpResponse, span));
                }
                catch (Throwable t)
                {
                    LOGGER.warn("Error updating response (trace id: {})", span.context().toTraceId(), t);
                    throw t;
                }
            }
            catch (Throwable t)
            {
                Tags.HTTP_STATUS.set(span, httpResponse.getStatus());
                addExceptionLogs(span, t);
                throw t;
            }
            finally
            {
                if (request.isAsyncStarted())
                {
                    LOGGER.debug("Async request, not finishing the span now (trace id: {})", span.context().toTraceId());
                    request.getAsyncContext().addListener(new SpanFinisher(span), request, response);
                }
                else
                {
                    span.finish();
                }
            }
        }

        // Check if there is a lingering active span
        Span activeSpan = this.tracer.activeSpan();
        if (activeSpan != null)
        {
            LOGGER.error("There is still an open ActiveTracing span (trace id: {}). This probably means a scope is unclosed.", activeSpan.context().toTraceId());
            try
            {
                ACTIVE_SPAN_EXCEPTION.inc();
            }
            catch (Exception e)
            {
                LOGGER.error("Failed to update activeSpanException gauge", e);
            }
        }
    }

    @Override
    public void destroy()
    {
    }

    private static void addExceptionLogs(Span span, Throwable throwable)
    {
        Tags.ERROR.set(span, true);
        Map<String, Object> errorLogs = new HashMap<>(2);
        errorLogs.put("event", Tags.ERROR.getKey());
        errorLogs.put("error.object", throwable);
        span.log(errorLogs);
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
