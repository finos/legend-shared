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

import io.dropwizard.core.Configuration;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import java.io.IOException;
import java.net.InetAddress;
import java.util.EnumSet;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

public class HostnameHeaderBundle implements ConfiguredBundle<Configuration>
{

  @Override
  public void initialize(Bootstrap<?> bootstrap)
  {

  }

  @Override
  public void run(Configuration configuration, Environment environment)
  {
    environment.servlets().addFilter(getClass().getName(), Enricher.class)
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }

  public static class Enricher implements Filter
  {

    @Override
    public void init(FilterConfig filterConfig)
    {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException
    {
      HttpServletResponse httpResponse = (HttpServletResponse) response;
      httpResponse.addHeader("X-Served-By", InetAddress.getLocalHost().getCanonicalHostName());
      chain.doFilter(request, response);
    }

    @Override
    public void destroy()
    {
    }
  }
}
