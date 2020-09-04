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

package org.finos.legend.server.pac4j.internal;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.security.DefaultUserIdentity;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.UserIdentity;

public class UsernameFilter implements Filter
{
  @Override
  public void init(FilterConfig filterConfig)
  {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
  {
    HttpServletRequest httpServletRequest = (HttpServletRequest) request;
    Request httpRequest = HttpConnection.getCurrentConnection().getHttpChannel().getRequest();
    if (httpRequest != null && httpServletRequest.getUserPrincipal() != null
        && httpServletRequest.getUserPrincipal().getName() != null)
    {
      UserIdentity userId =
          new DefaultUserIdentity(null, httpServletRequest.getUserPrincipal(), null);
      httpRequest.setAuthentication(new UserAuthentication(null, userId));
    }
    chain.doFilter(request, response);
  }

  @Override
  public void destroy()
  {
  }
}
