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
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalKerberosClientTest
{
    private static final String SERVER_ADDR = "10.1.2.3";
    private static final String REMOTE_ADDR = "203.0.113.50";

    @Test
    public void callFromThisHostIsAccepted()
    {
        assertTrue(authenticate(request(SERVER_ADDR)).isPresent());
    }

    @Test
    public void callFromAnotherHostIsRejected()
    {
        assertFalse(authenticate(request(REMOTE_ADDR)).isPresent());
    }

    @Test
    public void callFromAnotherHostClaimingThisHostInXForwardedForIsRejected()
    {
        MockHttpServletRequest request = request(REMOTE_ADDR);
        request.addHeader("X-Forwarded-For", SERVER_ADDR);
        assertFalse(authenticate(request).isPresent());
    }

    @Test
    public void forwardedCallIsRejectedEvenFromThisHost()
    {
        MockHttpServletRequest xForwardedFor = request(SERVER_ADDR);
        xForwardedFor.addHeader("X-Forwarded-For", SERVER_ADDR);
        assertFalse(authenticate(xForwardedFor).isPresent());

        MockHttpServletRequest forwarded = request(SERVER_ADDR);
        forwarded.addHeader("Forwarded", "for=" + SERVER_ADDR);
        assertFalse(authenticate(forwarded).isPresent());
    }

    private static MockHttpServletRequest request(String remoteAddr)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.setLocalAddr(SERVER_ADDR);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static Optional<UserProfile> authenticate(MockHttpServletRequest request)
    {
        LocalKerberosClient client = new LocalKerberosClient();
        client.setName("LocalKerberosClient");
        CallContext ctx = new CallContext(new JEEContext(request, new MockHttpServletResponse()), new JEESessionStore());

        Optional<Credentials> credentials = client.getCredentials(ctx);
        assertTrue(credentials.isPresent());
        try
        {
            return client.validateCredentials(ctx, credentials.get()).flatMap(c -> client.getUserProfile(ctx, c));
        }
        catch (CredentialsException e)
        {
            return Optional.empty();
        }
    }
}
