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

import io.dropwizard.Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

@SuppressWarnings("unused")
public class HtmlRouterRedirectBundle implements Bundle
{

  private final String uiPath;
  private final List<String> excludePaths;
  private final String htmlPath;

  public HtmlRouterRedirectBundle(String uiPath, List<String> excludePaths, String htmlPath)
  {
    this.uiPath = uiPath;
    this.excludePaths = excludePaths;
    this.htmlPath = htmlPath;
  }

  @Override
  public void initialize(Bootstrap<?> bootstrap)
  {
  }

  @Override
  public void run(Environment environment)
  {
    environment
        .servlets()
        .addFilter(getClass().getName(), new Enricher())
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, uiPath + "/*");
  }

  class Enricher implements Filter
  {
    @Override
    public void init(FilterConfig filterConfig)
    {
    }

    private boolean excludePath(String uri)
    {
      for (String testPath : excludePaths)
      {
        if (uri.startsWith(uiPath + testPath))
        {
          return true;
        }
      }
      return false;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException
    {
      HttpServletRequest httpServletRequest = (HttpServletRequest) request;
      String uri = httpServletRequest.getRequestURI();
      if (uri.startsWith(uiPath) && !excludePath(uri) && !uri.equals(htmlPath))
      {
        httpServletRequest.getRequestDispatcher(htmlPath).forward(request, response);
      } else
      {
        chain.doFilter(request, response);
      }
    }

    @Override
    public void destroy()
    {
    }
  }
}
