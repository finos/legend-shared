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
import org.finos.legend.server.pac4j.kerberos.local.SystemAccountLoginConfiguration;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.BadCredentialsException;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.kerberos.credentials.KerberosCredentials;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Base64;
import javax.security.auth.Subject;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

public class DelegationKerberosAuthenticator implements Authenticator<KerberosCredentials>
{
    private final String servicePrincipal;
    private final String keytabLocation;

    public DelegationKerberosAuthenticator(String servicePrincipal, String keytabLocation, boolean debug)
    {
        this.servicePrincipal = servicePrincipal;
        this.keytabLocation = keytabLocation;
    }

    private Subject getSubject() throws LoginException
    {
        Configuration config = new SystemAccountLoginConfiguration(this.keytabLocation, this.servicePrincipal, false);
        LoginContext loginContext = new LoginContext("", null, null, config);
        loginContext.login();
        return loginContext.getSubject();
    }

    @Override
    public void validate(KerberosCredentials credentials, WebContext webContext)
    {
        try
        {
            GSSManager manager = GSSManager.getInstance();

            PrivilegedExceptionAction<GSSCredential> action = () ->
                    manager.createCredential(
                            null,
                            GSSCredential.DEFAULT_LIFETIME,
                            new Oid("1.3.6.1.5.5.2"),
                            GSSCredential.ACCEPT_ONLY);
            GSSCredential cred = Subject.doAs(getSubject(), action);

            GSSContext context = manager.createContext(cred);
            context.requestCredDeleg(true);
            byte[] resToken = context.acceptSecContext(credentials.getKerberosTicket(), 0, credentials.getKerberosTicket().length);

            // delegation is required, so reject credentials without delegation
            if (!context.getCredDelegState())
            {
                String baseMessage = "Delegation is turned off";
                String message;
                try
                {
                    String name = context.getSrcName().toString();
                    message = baseMessage + " in credentials for " + name;
                }
                catch (Exception ignore)
                {
                    // ignore errors building the message
                    message = baseMessage;
                }
                webContext.writeResponseContent(baseMessage);
                throw new BadCredentialsException(message);
            }

            KerberosProfile profile = createProfile(context);
            credentials.setUserProfile(profile);
            webContext.setResponseHeader("WWW-Authenticate", "Negotiate " + Base64.getEncoder().encodeToString(resToken));
        }
        catch (CredentialsException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            Throwable cause = (e instanceof PrivilegedActionException) ? e.getCause() : e;
            String message = "Kerberos validation not successful";
            String exMessage = cause.getMessage();
            if (exMessage != null)
            {
                message = message + ": " + exMessage;
            }
            throw new BadCredentialsException(message, cause);
        }
    }

    private KerberosProfile createProfile(GSSContext context) throws GSSException
    {
        GSSName gssName = context.getSrcName();
        Subject delegationSubject = GSSUtil.createSubject(gssName, context.getDelegCred());
        KerberosProfile profile = new KerberosProfile(delegationSubject, context);

        String nameString = gssName.toString();
        int atIndex = nameString.indexOf('@');
        String profileId = (atIndex < 0) ? nameString : nameString.substring(0, atIndex);
        profile.setId(profileId);
        return profile;
    }
}
