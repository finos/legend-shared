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

import com.sun.security.jgss.GSSUtil;
import java.security.PrivilegedExceptionAction;
import java.util.Base64;
import javax.security.auth.Subject;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import org.finos.legend.server.pac4j.kerberos.local.SystemAccountLoginConfiguration;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.BadCredentialsException;
import org.pac4j.kerberos.credentials.KerberosCredentials;

public class DelegationKerberosAuthenticator implements Authenticator<KerberosCredentials>
{
  private final String servicePrincipal;
  private final String keytabLocation;

  public DelegationKerberosAuthenticator(
      String servicePrincipal, String keytabLocation, boolean debug)
  {
    this.servicePrincipal = servicePrincipal;
    this.keytabLocation = keytabLocation;
  }

  private Subject getSubject()
  {
    try
    {
      Configuration config =
          new SystemAccountLoginConfiguration(keytabLocation, servicePrincipal, false);
      LoginContext loginContext = new LoginContext("", null, null, config);
      loginContext.login();
      return loginContext.getSubject();
    } catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void validate(KerberosCredentials credentials, WebContext webContext)
  {
    try
    {
      final GSSManager MANAGER = GSSManager.getInstance();

      PrivilegedExceptionAction<GSSCredential> action =
          () ->
              MANAGER.createCredential(
                  null,
                  GSSCredential.DEFAULT_LIFETIME,
                  new Oid("1.3.6.1.5.5.2"),
                  GSSCredential.ACCEPT_ONLY);
      GSSCredential cred = Subject.doAs(getSubject(), action);

      GSSContext context = MANAGER.createContext(cred);
      context.requestCredDeleg(true);
      byte[] resToken = context.acceptSecContext(
          credentials.getKerberosTicket(), 0, credentials.getKerberosTicket().length);
      String name = context.getSrcName().toString();
      name = name.substring(0, name.indexOf('@'));
      if (context.getCredDelegState())
      {
        Subject delegationSubject =
            GSSUtil.createSubject(context.getSrcName(), context.getDelegCred());
        KerberosProfile profile = new KerberosProfile(delegationSubject, context);
        profile.setId(name);
        credentials.setUserProfile(profile);
        webContext.setResponseHeader("WWW-Authenticate",
            "Negotiate " + Base64.getEncoder().encodeToString(resToken));
      } else
      {
        webContext.writeResponseContent("Delegation is turned off");
        throw new BadCredentialsException("Delegation is turned off");
      }
    } catch (Exception e)
    {
      throw new BadCredentialsException("Kerberos validation not successful", e);
    }
  }
}
