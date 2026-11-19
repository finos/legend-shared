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

import com.google.common.collect.ImmutableMap;
import com.hazelcast.core.Hazelcast;
import org.finos.legend.server.pac4j.SessionStoreTestUtil;
import org.finos.legend.server.pac4j.hazelcaststore.HazelcastSessionStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class KerberosProfileHazelcastSessionStoreTest
{
    private static final String HAZELCAST_CONFIG_FILE_PATH = "src/test/resources/hazelcast.yaml";
    private static final String SSO_COOKIE_NAME = "LegendSSOTest";

    private HazelcastSessionStore store;

    @Before
    public void before()
    {
        store = new HazelcastSessionStore(HAZELCAST_CONFIG_FILE_PATH,
                ImmutableMap.of(JEEContext.class, new JEESessionStore()), SSO_COOKIE_NAME);
    }

    @After
    public void after()
    {
        Hazelcast.shutdownAll();
    }

    @Test
    public void kerberosProfileRoundTripsThroughHazelcastSessionStore()
    {
        Instant now = Instant.now();
        KerberosTicket ticket = createTicket(now.minusSeconds(1), now.plusSeconds(3600));
        Subject subject = new Subject();
        subject.getPrincipals().add(ticket.getClient());
        subject.getPrivateCredentials().add(ticket);
        KerberosProfile profile = new KerberosProfile(subject, null);
        profile.setId("testKerberosUser");

        MockHttpServletResponse response = new MockHttpServletResponse();
        store.set(new JEEContext(new MockHttpServletRequest(), response), Pac4jConstants.USER_PROFILES, profile);

        Cookie[] cookies = SessionStoreTestUtil.responseCookies(response);
        assertNotNull(cookies);
        assertTrue(cookies.length > 0);

        // New request carrying only the SSO cookie (no HTTP session), so the profile must come from Hazelcast
        MockHttpServletRequest nextRequest = new MockHttpServletRequest();
        nextRequest.setCookies(cookies);
        Optional<Object> retrieved = store.get(new JEEContext(nextRequest, new MockHttpServletResponse()), Pac4jConstants.USER_PROFILES);

        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get() instanceof KerberosProfile);
        KerberosProfile retrievedProfile = (KerberosProfile) retrieved.get();
        assertNotSame(profile, retrievedProfile);
        assertEquals("testKerberosUser", retrievedProfile.getId());
        assertNotNull(retrievedProfile.getSubject());
        assertEquals("user@REALM",
                retrievedProfile.getSubject().getPrincipals(KerberosPrincipal.class).iterator().next().getName());
        assertEquals(1, retrievedProfile.getSubject().getPrivateCredentials(KerberosTicket.class).size());
        assertFalse(retrievedProfile.isExpired());
    }

    private static KerberosTicket createTicket(Instant start, Instant end)
    {
        KerberosPrincipal client = new KerberosPrincipal("user@REALM");
        KerberosPrincipal server = new KerberosPrincipal("krbtgt/REALM@REALM");
        return new KerberosTicket(new byte[] {1}, client, server, new byte[] {}, 1, new boolean[1],
                Date.from(start.minusSeconds(1)), Date.from(start), Date.from(end),
                Date.from(end.plus(Duration.ofMinutes(1))), null);
    }
}

