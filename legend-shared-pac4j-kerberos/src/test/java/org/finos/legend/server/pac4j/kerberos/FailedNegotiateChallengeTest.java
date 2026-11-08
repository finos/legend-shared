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

import org.junit.Test;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.util.HttpActionHelper;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.http.adapter.JEEHttpActionAdapter;
import org.pac4j.kerberos.client.direct.DirectKerberosClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Base64;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A ticket the service cannot validate, e.g. one for another host's service principal, must still be answered
 * with a {@code Negotiate} challenge. pac4j 6 blanks {@code WWW-Authenticate} once credentials have been
 * extracted, and a 401 without a challenge gives the client nothing to act on.
 */
public class FailedNegotiateChallengeTest
{
    private static final String NO_SUCH_KEYTAB = "/no/such/keytab";

    @Test
    public void aTicketTheUnconstrainedAuthenticatorRejectsIsChallengedAgain()
    {
        assertChallenged(new UnconstraintKerberosAuthenticator("HTTP/service@REALM", NO_SUCH_KEYTAB, null));
    }

    @Test
    public void aTicketTheConstrainedAuthenticatorRejectsIsChallengedAgain()
    {
        assertChallenged(new ConstraintKerberosAuthenticator("HTTP/service@REALM", NO_SUCH_KEYTAB));
    }

    @Test
    public void noTicketIsChallengedToo()
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext context = new JEEContext(new MockHttpServletRequest(), response);
        DirectKerberosClient client = new DirectKerberosClient(new UnconstraintKerberosAuthenticator("HTTP/service@REALM", NO_SUCH_KEYTAB, null));

        assertFalse(client.getCredentials(new CallContext(context, null)).isPresent());
        JEEHttpActionAdapter.INSTANCE.adapt(HttpActionHelper.buildUnauthenticatedAction(context), context);

        assertEquals(401, response.getStatus());
        assertEquals("Negotiate", response.getHeader("WWW-Authenticate"));
    }

    private static void assertChallenged(Authenticator authenticator)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Negotiate " + Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext context = new JEEContext(request, response);
        CallContext callContext = new CallContext(context, null);
        DirectKerberosClient client = new DirectKerberosClient(authenticator);

        Optional<Credentials> credentials = client.getCredentials(callContext);
        assertTrue("the ticket is extracted", credentials.isPresent());
        assertFalse("and not accepted", client.validateCredentials(callContext, credentials.get()).isPresent());
        JEEHttpActionAdapter.INSTANCE.adapt(HttpActionHelper.buildUnauthenticatedAction(context), context);

        assertEquals(401, response.getStatus());
        assertEquals("Negotiate", response.getHeader("WWW-Authenticate"));
    }
}
