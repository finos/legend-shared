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

import com.google.common.collect.ImmutableList;
import io.dropwizard.Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.opentracing.Span;
import io.opentracing.contrib.jaxrs2.internal.CastUtils;
import io.opentracing.contrib.jaxrs2.internal.SpanWrapper;
import org.finos.legend.opentracing.jaxrs2.InterceptorSpanDecorator;
import org.finos.legend.opentracing.jaxrs2.ServerTracingInterceptor;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.ext.InterceptorContext;
import javax.ws.rs.ext.ReaderInterceptorContext;
import javax.ws.rs.ext.WriterInterceptorContext;

import org.finos.legend.opentracing.OpenTracingFilter;
import org.finos.legend.opentracing.ServerSpanDecorator;
import org.finos.legend.opentracing.StandardSpanDecorator;

import static io.opentracing.contrib.jaxrs2.internal.SpanWrapper.PROPERTY_NAME;

@SuppressWarnings("unused")
public class OpenTracingBundle implements Bundle
{

  private final List<ServerSpanDecorator> decorators;
  private final List<String> skipUrls;

  public OpenTracingBundle()
  {
    this(ImmutableList.of(), ImmutableList.of());
  }

  /**
   * Create OpenTracingBundle.
   *
   * @param decorators Additional decorators to add
   * @param skipUrls   URLs to skip tracing on
   */
  @SuppressWarnings("WeakerAccess")
  public OpenTracingBundle(Iterable<ServerSpanDecorator> decorators, List<String> skipUrls)
  {
    this.decorators =
        ImmutableList.<ServerSpanDecorator>builder()
            .addAll(decorators)
            .add(new StandardSpanDecorator())
            .add(new UserNameDecorator())
            .build();
    this.skipUrls = skipUrls;
  }

  @Override
  public void initialize(Bootstrap<?> bootstrap)
  {
  }

  @Override
  public void run(Environment environment)
  {
    if (GlobalTracer.isRegistered())
    {
      final FilterRegistration.Dynamic openTracing =
          environment
              .servlets()
              .addFilter(
                  "OpenTracing",
                  new OpenTracingFilter(GlobalTracer.get(), this.decorators, this.skipUrls));
      openTracing.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/*");
      environment
          .jersey()
          .register(
              (DynamicFeature) (resourceInfo, context) ->
                  context.register(
                      new ServerTracingInterceptor(
                          GlobalTracer.get(),
                          Collections.singletonList(new LegendInterceptorSpanDecorator())),
                      Priorities.ENTITY_CODER));
    }
  }

  private static class LegendInterceptorSpanDecorator implements InterceptorSpanDecorator
  {
    @Override
    public void decorateRead(InterceptorContext context, Span span)
    {
      InterceptorSpanDecorator.STANDARD_TAGS.decorateRead(context, span);
    }

    @Override
    public void decorateWrite(InterceptorContext context, Span span)
    {
      InterceptorSpanDecorator.STANDARD_TAGS.decorateWrite(context, span);
    }

    @Override
    public void decorateReadException(Exception e, ReaderInterceptorContext context, Span span)
    {
      InterceptorSpanDecorator.STANDARD_TAGS.decorateReadException(e, context, span);
      this.logException(span, e);
      this.errorRootSpan("deserialize", context);
    }

    @Override
    public void decorateWriteException(Exception e, WriterInterceptorContext context, Span span)
    {
      InterceptorSpanDecorator.STANDARD_TAGS.decorateWriteException(e, context, span);
      this.logException(span, e);
      this.errorRootSpan("serialize", context);
    }

    private void logException(Span span, Exception e)
    {
      Map<String, Object> errorLogs = new HashMap<>(2);
      errorLogs.put(Fields.EVENT, Tags.ERROR.getKey());
      errorLogs.put(Fields.ERROR_OBJECT, e);
      span.log(errorLogs);
    }

    private void errorRootSpan(String reason, InterceptorContext context)
    {
      SpanWrapper spanWrapper = CastUtils.cast(context.getProperty(PROPERTY_NAME), SpanWrapper.class);
      if (spanWrapper != null && !spanWrapper.isFinished())
      {
        Span rootSpan = spanWrapper.get();
        Tags.ERROR.set(rootSpan, true);
        Map<String, Object> errorLogs = new HashMap<>(2);
        errorLogs.put(Fields.EVENT, Tags.ERROR.getKey());
        errorLogs.put(Fields.MESSAGE, "Error on " + reason);
        rootSpan.log(errorLogs);
      }
    }
  }

  private static class UserNameDecorator implements ServerSpanDecorator
  {

    @Override
    public void decorateRequest(HttpServletRequest request, Span span)
    {
      Principal principal = request.getUserPrincipal();

      if (principal != null)
      {
        String user = principal.getName();
        span.setTag("user", user);
      }

      try
      {
        span.setTag("serverHost", InetAddress.getLocalHost().getCanonicalHostName());
      } catch (UnknownHostException e)
      {
        // Ignore
      }
    }

    @Override
    public void decorateResponse(HttpServletResponse response, Span span)
    {
    }
  }
}
