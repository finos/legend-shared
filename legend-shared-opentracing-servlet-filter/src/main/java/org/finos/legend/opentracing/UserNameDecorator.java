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

import io.opentracing.Span;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class UserNameDecorator implements org.finos.legend.opentracing.ServerSpanDecorator
{

  private static final String SERVER_HOST = getServerHost();

  public void decorateRequest(HttpServletRequest request, Span span)
  {
    Principal principal = request.getUserPrincipal();
    if (principal != null)
    {
      String user = principal.getName();
      span.setTag("user", user);
    }

    span.setTag("serverHost", SERVER_HOST);
  }

  public void decorateResponse(HttpServletResponse response, Span span)
  {
  }

  private static String getServerHost()
  {
    try
    {
      return InetAddress.getLocalHost().getCanonicalHostName();
    } catch (UnknownHostException ignored)
    {
      //ignored
    }
    return "";
  }
}