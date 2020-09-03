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

import java.util.Iterator;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import org.finos.legend.server.pac4j.kerberos.local.LocalLoginConfiguration;
import org.pac4j.core.credentials.Credentials;
import org.slf4j.LoggerFactory;

public class LocalCredentials extends Credentials
{

  public static final LocalCredentials INSTANCE = new LocalCredentials();
  private Subject subject;

  private LocalCredentials()
  {
    try
    {
      Configuration config = new LocalLoginConfiguration();
      LoginContext ctx = new LoginContext("", null, null, config);
      ctx.login();
      subject = ctx.getSubject();
    } catch (Exception e)
    {
      // Dont use a static property for the logger as it might not be initialized
      LoggerFactory.getLogger(LocalCredentials.class)
          .warn("Unable to complete local kerberos login, returning no user", e);
    }
  }

  public Subject getSubject()
  {
    return subject;
  }

  String getUserId()
  {
    if (subject == null)
    {
      return "(unknown)";
    }
    Iterator<KerberosPrincipal> iterator =
        subject.getPrincipals(KerberosPrincipal.class).iterator();
    if (iterator.hasNext())
    {
      return iterator.next().getName().split("@")[0];
    } else
    {
      throw new RuntimeException("Cannot find principal in Subject[" + subject.toString() + "]");
    }
  }

  @Override
  public boolean equals(Object o)
  {
    return o == this;
  }

  @Override
  public int hashCode()
  {
    return 0;
  }
}
