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

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.jaxrs2.internal.SpanWrapper;
import org.finos.legend.opentracing.OpenTracingFilter;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static io.opentracing.contrib.jaxrs2.internal.SpanWrapper.PROPERTY_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OpenTracingFilterTest
{

  @Test
  public void filterTest() throws IOException, ServletException
  {
    HttpServletResponse httpResponse = mock(HttpServletResponse.class);
    HttpServletRequest httpRequest = mock(HttpServletRequest.class);
    FilterChain chain = mock(FilterChain.class);
    when(httpRequest.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    Tracer tracer = mock(Tracer.class);
    Tracer.SpanBuilder builder = mock(Tracer.SpanBuilder.class);
    when(tracer.buildSpan(any())).thenReturn(builder);
    when(builder.ignoreActiveSpan()).thenReturn(builder);
    when(builder.withTag((String) any(), (String) any())).thenReturn(builder);
    Scope scope = mock(Scope.class);
    Span span = mock(Span.class);
    when(builder.startActive(false)).thenReturn(scope);
    when(scope.span()).thenReturn(span);

    OpenTracingFilter filter = new OpenTracingFilter(tracer);
    filter.doFilter(httpRequest, httpResponse, chain);
    verify(chain).doFilter(httpRequest, httpResponse);
    ArgumentCaptor<SpanWrapper> spanCaptor = ArgumentCaptor.forClass(SpanWrapper.class);
    verify(httpRequest).setAttribute(eq(PROPERTY_NAME), spanCaptor.capture());
    Assert.assertEquals(span, spanCaptor.getValue().get());
  }

  @Test
  public void filterExceptionTest() throws IOException, ServletException
  {
    HttpServletResponse httpResponse = mock(HttpServletResponse.class);
    HttpServletRequest httpRequest = mock(HttpServletRequest.class);
    FilterChain chain = mock(FilterChain.class);
    when(httpRequest.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    Tracer tracer = mock(Tracer.class);
    Tracer.SpanBuilder builder = mock(Tracer.SpanBuilder.class);
    when(tracer.buildSpan(any())).thenReturn(builder);
    when(builder.ignoreActiveSpan()).thenReturn(builder);
    when(builder.withTag((String) any(), (String) any())).thenReturn(builder);
    Scope scope = mock(Scope.class);
    Span span = mock(Span.class);
    when(builder.startActive(false)).thenReturn(scope);
    when(scope.span()).thenReturn(span);

    ServletException exception = new ServletException("Stuff went wrong");
    doThrow(exception)
        .when(chain)
        .doFilter(httpRequest, httpResponse);

    OpenTracingFilter filter = new OpenTracingFilter(tracer);
    try
    {
      filter.doFilter(httpRequest, httpResponse, chain);
    } catch (ServletException ignored)
    {
    }
    verify(chain).doFilter(httpRequest, httpResponse);
    ArgumentCaptor<SpanWrapper> spanCaptor = ArgumentCaptor.forClass(SpanWrapper.class);
    verify(httpRequest).setAttribute(eq(PROPERTY_NAME), spanCaptor.capture());
    Assert.assertEquals(span, spanCaptor.getValue().get());
    verify(span).setTag("error", true);
    ArgumentCaptor<Map<String, Object>> logMsgCaptor = ArgumentCaptor.forClass(Map.class);
    verify(span).log(logMsgCaptor.capture());
    Assert.assertEquals(exception, logMsgCaptor.getValue().get("error.object"));
  }
}
