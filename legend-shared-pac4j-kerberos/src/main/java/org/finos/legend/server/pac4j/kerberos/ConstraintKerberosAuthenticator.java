// Copyright 2026 Goldman Sachs
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
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.BadCredentialsException;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.kerberos.credentials.KerberosCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Base64;
import javax.security.auth.Subject;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

public class ConstraintKerberosAuthenticator implements Authenticator<KerberosCredentials>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConstraintKerberosAuthenticator.class);
    private final String servicePrincipal;
    private final String serviceKeytabLocation;

    public ConstraintKerberosAuthenticator(String servicePrincipal, String serviceKeyTabLocation)
    {
        this.servicePrincipal = servicePrincipal;
        this.serviceKeytabLocation = serviceKeyTabLocation;
    }

    Subject getServiceSubject() throws LoginException
    {
        Configuration config = new SystemAccountLoginConfiguration(this.serviceKeytabLocation, this.servicePrincipal, true,true);
        LoginContext loginContext = new LoginContext("", null, null, config);
        loginContext.login();
        return loginContext.getSubject();
    }

    @Override
    public void validate(KerberosCredentials userCredentials, WebContext webContext)
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
            Subject serviceSubject = getServiceSubject();
            LOGGER.debug("validate serviceSubject {} userCredentials {}", serviceSubject.getPrincipals().iterator().next().getName(), userCredentials.getKerberosTicketAsString());
            GSSCredential serviceCreds = Subject.doAs(serviceSubject, action);

            GSSContext serverContext = manager.createContext(serviceCreds);

            serverContext.requestCredDeleg(true);
            PrivilegedExceptionAction<byte[]> acceptAction = () ->
            {
                byte[] resToken = null;
                while (!serverContext.isEstablished())
                {
                    resToken = serverContext.acceptSecContext(userCredentials.getKerberosTicket(), 0, userCredentials.getKerberosTicket().length);
                    serverContext.getCredDelegState();
                }

                // delegation is required, so reject userCredentials without delegation
                if (!serverContext.getCredDelegState())
                {
                    String baseMessage = "Delegation is turned off";
                    String message;
                    try
                    {
                        String name = serverContext.getSrcName().toString();
                        message = baseMessage + " in userCredentials for " + name;
                    }
                    catch (Exception ignore)
                    {
                        // ignore errors building the message
                        message = baseMessage;
                    }
                    writeToResponse(webContext, baseMessage);

                    LOGGER.error("validate failed: serverContext has no delegate {}", message);
                    throw new BadCredentialsException(message);
                }
                KerberosProfile userProfile = createProfile(serverContext,serviceSubject);
                userCredentials.setUserProfile(userProfile);
                return resToken;
            };

            byte[] resToken = Subject.doAs(serviceSubject, acceptAction);

            webContext.setResponseHeader("WWW-Authenticate", "Negotiate " + Base64.getEncoder().encodeToString(resToken));
        }
        catch (CredentialsException e)
        {
            LOGGER.error("validate failed {}", e.getMessage());
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
            LOGGER.error("Validate failed: {}",message,cause);
            throw new BadCredentialsException(message, cause);
        }
    }

    private void writeToResponse(WebContext webContext, String baseMessage) throws IOException
    {
        if (webContext instanceof JEEContext)
        {
            ((JEEContext) webContext).getNativeResponse().getWriter().write(baseMessage);
        }
    }

    private KerberosProfile createProfile(GSSContext context, Subject serviceSubject) throws GSSException
    {
        GSSName userGssName = context.getSrcName();
        Subject delegationSubject = GSSUtil.createSubject(userGssName, context.getDelegCred());
        // If private credentials are empty, add delegCred to public credentials
        if (delegationSubject.getPrivateCredentials().isEmpty() && context.getDelegCred() != null)
        {
            delegationSubject.getPublicCredentials().add(context.getDelegCred());
        }

        KerberosProfile profile = new KerberosProfile(delegationSubject, serviceSubject,context);

        String userName = userGssName.toString();
        int atIndex = userName.indexOf('@');
        String profileId = (atIndex < 0) ? userName : userName.substring(0, atIndex);
        profile.setId(profileId);
        return profile;
    }
}
