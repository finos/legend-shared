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

package org.finos.legend.server.pac4j.kerberos;

import org.pac4j.core.client.DirectClient;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.CredentialsException;

public class LocalKerberosClient extends DirectClient<LocalCredentials>
{
  private static final String X_FORWARDED_FOR_HEADER = "x-forwarded-for";

  private static String getRealRemoteAddress(WebContext context)
  {
      return context.getRequestHeader(X_FORWARDED_FOR_HEADER).orElseGet(context::getRemoteAddr);
  }

  @Override
  protected void clientInit()
  {
    setAuthenticator(
        (credentials, context) ->
        {
          JEEContext jeeContext = (JEEContext) context;
          javax.servlet.http.HttpServletRequest request = jeeContext.getNativeRequest();
          if (!request.getLocalAddr().equals(getRealRemoteAddress(context)))
          {
            throw new CredentialsException("LocalKerberosClient only works with local requests");
          }
        });
    setCredentialsExtractor(context -> java.util.Optional.of(LocalCredentials.INSTANCE));
    setProfileCreator((credentials, context) -> java.util.Optional.of(new KerberosProfile(credentials)));
  }
}
