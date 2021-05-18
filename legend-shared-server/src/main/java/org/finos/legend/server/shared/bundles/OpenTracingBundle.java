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
import io.opentracing.contrib.jaxrs2.serialization.InterceptorSpanDecorator;
import io.opentracing.contrib.jaxrs2.server.ServerTracingInterceptor;
import io.opentracing.util.GlobalTracer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;
import java.util.EnumSet;
import java.util.List;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.DynamicFeature;
import org.finos.legend.opentracing.OpenTracingFilter;
import org.finos.legend.opentracing.ServerSpanDecorator;
import org.finos.legend.opentracing.StandardSpanDecorator;

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
                          ImmutableList.of(InterceptorSpanDecorator.STANDARD_TAGS)),
                      Priorities.ENTITY_CODER));
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
