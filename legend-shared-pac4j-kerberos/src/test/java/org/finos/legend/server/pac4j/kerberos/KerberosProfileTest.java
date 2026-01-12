// Copyright 2025 Goldman Sachs
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
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.finos.legend.server.pac4j.mongostore.MongoDbSessionStore;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.session.JEESessionStore;
import org.pac4j.core.util.Pac4jConstants;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import javax.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KerberosProfileTest
{

    private static MongoServer server;
    private static MongoClient client;
    private static MongoDatabase db;
    private static final String SESSION_COLLECTION = "sessionData";

    @BeforeClass
    public static void setup()
    {
        server = new MongoServer(new MemoryBackend());
        InetSocketAddress serverAddress = server.bind();

        client = MongoClients.create(new ConnectionString("mongodb://"+serverAddress.getHostName() +":"+ serverAddress.getPort()));
        db = client.getDatabase("test");
    }

    @AfterClass
    public static void teardown()
    {
        server.shutdown();
        client.close();
    }

    @Test
    public void testIsExpiredFalseWhenTicketCurrent()
    {
        Instant now = Instant.now();
        KerberosTicket current = createTicket(now.minusSeconds(1), now.plusSeconds(60));
        KerberosProfile profile = buildProfileWithTicket(current);
        assertFalse(profile.isExpired());
    }

    @Test
    public void testIsExpiredTrueWhenTicketExpired()
    {
        Instant now = Instant.now();
        KerberosTicket expired = createTicket(now.minusSeconds(60), now.minusSeconds(1));
        KerberosProfile profile = buildProfileWithTicket(expired);
        assertTrue(profile.isExpired());
    }

    @Test
    public void testIsExpiredWhenSubjectNull()
    {
        KerberosProfile profile = new KerberosProfile(); // subject remains null
        assertFalse(profile.isExpired());
    }

    @Test
    public void testIsExpiredWhenPublicCredsEmpty()
    {
        Subject subject = new Subject();
        KerberosProfile profile = new KerberosProfile(subject,null);
        assertTrue(profile.isExpired());
    }

    @Test
    public void testIsExpiredWhenPublicCredsExpired() throws GSSException
    {
        Subject subject = new Subject();
        GSSCredential testCred = mock(GSSCredential.class);
        when(testCred.getRemainingLifetime()).thenReturn(0);
        subject.getPublicCredentials().add(testCred);
        KerberosProfile profile = new KerberosProfile(subject,null);
        assertTrue(profile.isExpired());
    }

    @Test
    public void testIsExpiredWhenPublicCredsAreValid() throws GSSException
    {
        Subject subject = new Subject();
        GSSCredential testCred = mock(GSSCredential.class);
        when(testCred.getRemainingLifetime()).thenReturn(Integer.MAX_VALUE);
        subject.getPublicCredentials().add(testCred);
        KerberosProfile profile = new KerberosProfile(subject,null);
        assertFalse(profile.isExpired());
    }

    @Test
    public void testIsExpiredTrueWhenKerberosTicketNull()
    {
        Subject subject = new Subject();
        KerberosProfile profile = new KerberosProfile(subject, null);
        assertTrue(profile.isExpired());
    }

    private KerberosTicket createTicket(Instant start, Instant end)
    {
        KerberosPrincipal client = new KerberosPrincipal("user@REALM");
        KerberosPrincipal server = new KerberosPrincipal("krbtgt/REALM@REALM");
        Date authTime = Date.from(start.minusSeconds(1));
        Date startTime = Date.from(start);
        Date endTime = Date.from(end);
        Date renewTill = Date.from(end.plus(Duration.ofMinutes(1)));

        return new KerberosTicket(new byte[] {1}, client, server, new byte[] {}, 1, new boolean[1],
                authTime, startTime, endTime, renewTill, null);
    }

    private KerberosProfile buildProfileWithTicket(KerberosTicket ticket)
    {
        Subject subject = new Subject();
        subject.getPrincipals().add(ticket.getClient());
        subject.getPrivateCredentials().add(ticket);
        return new KerberosProfile(subject, null);
    }

    private KerberosProfile buildProfileWithDelegatedCreds(String principalName)
    {
        KerberosPrincipal principal = new KerberosPrincipal(principalName);
        Subject subject = new Subject();
        subject.getPrincipals().add(principal);
        subject.getPublicCredentials().add(principal);
        return new KerberosProfile(subject, null);
    }

    @Test
    public void testKerberosProfileWithTGTAndRetrievalInMongoStore()
    {
        // Setup trusted packages for KerberosProfile serialization
        List<String> trustedPackages = new ArrayList<>();
        trustedPackages.add("org.pac4j.core.profile");
        trustedPackages.add("org.finos.legend.server.pac4j.kerberos");

        MongoDbSessionStore kerberosStore = new MongoDbSessionStore(
                "AES",
                3600,
                db.getCollection("kerberosSessionData"),
                ImmutableMap.of(JEEContext.class, new JEESessionStore()),
                trustedPackages,
                "LegendSSOTest"
        );

        // Create KerberosProfile with a valid ticket
        Instant now = Instant.now();
        KerberosTicket ticket = createTicket(now.minusSeconds(1), now.plusSeconds(3600));
        KerberosProfile kerberosProfile = buildProfileWithTicket(ticket);
        kerberosProfile.setId("testKerberosUser");

        // First call: Store the profile
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(request, response);

        kerberosStore.set(requestContext, Pac4jConstants.USER_PROFILES, kerberosProfile);

        // Verify profile was stored in session
        Optional<Object> retrievedFromSession = kerberosStore.get(requestContext, Pac4jConstants.USER_PROFILES);
        assertTrue(retrievedFromSession.isPresent());
        assertEquals(kerberosProfile.getId(), ((KerberosProfile) retrievedFromSession.get()).getId());

        // Extract SSO cookie
        Cookie[] cookies = response.getCookies();
        assertNotNull(cookies);
        assertTrue(cookies.length > 0);

        // Second call: Simulate new request with SSO cookie
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setCookies(cookies);
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        JEEContext newRequestContext = new JEEContext(newRequest, newResponse);

        // Retrieve profile from MongoDB
        Optional<Object> retrievedFromMongo = kerberosStore.get(newRequestContext, Pac4jConstants.USER_PROFILES);
        assertTrue(retrievedFromMongo.isPresent());
        assertTrue(retrievedFromMongo.get() instanceof KerberosProfile);

        // Verify the KerberosProfile data
        KerberosProfile retrievedProfile = (KerberosProfile) retrievedFromMongo.get();
        assertEquals("testKerberosUser", retrievedProfile.getId());
        assertNotNull(retrievedProfile.getSubject());
        assertFalse(retrievedProfile.isExpired());

        // Verify the Kerberos ticket is preserved
        assertEquals(1, retrievedProfile.getSubject().getPrivateCredentials(KerberosTicket.class).size());
    }

    @Test
    public void testKerberosProfileWithDelegatedCredsOnlyAndRetrievalInMongoStoreForDiffSessionStore()
    {
        List<String> trustedPackages = new ArrayList<>();
        trustedPackages.add("org.pac4j.core.profile");
        trustedPackages.add("org.finos.legend.server.pac4j.kerberos");

        MongoDbSessionStore kerberosStore = new MongoDbSessionStore(
                "AES",
                3600,
                db.getCollection(SESSION_COLLECTION),
                ImmutableMap.of(JEEContext.class, new JEESessionStore()),
                trustedPackages,
                "LegendSSOTest"
        );


        KerberosProfile kerberosProfile = buildProfileWithDelegatedCreds("delegatedUser@REALM");
        kerberosProfile.setId("testDelegatedUser");

        // First call: Store the profile
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(request, response);

        kerberosStore.set(requestContext, Pac4jConstants.USER_PROFILES, kerberosProfile);

        // Verify profile was stored in session
        Optional<Object> retrievedFromSession = kerberosStore.get(requestContext, Pac4jConstants.USER_PROFILES);
        assertTrue(retrievedFromSession.isPresent());
        assertEquals(kerberosProfile.getId(), ((KerberosProfile) retrievedFromSession.get()).getId());

        // Extract SSO cookie
        Cookie[] cookies = response.getCookies();
        assertNotNull(cookies);
        assertTrue(cookies.length > 0);

        // Second call: Simulate new request with SSO cookie
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setCookies(cookies);
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        JEEContext newRequestContext = new JEEContext(newRequest, newResponse);

        // Retrieve profile from MongoDB
        Optional<Object> retrievedFromMongo = kerberosStore.get(newRequestContext, Pac4jConstants.USER_PROFILES);
        assertTrue(retrievedFromMongo.isPresent());
        assertTrue(retrievedFromMongo.get() instanceof KerberosProfile);

        // Verify the KerberosProfile data
        KerberosProfile retrievedProfile = (KerberosProfile) retrievedFromMongo.get();
        assertEquals("testDelegatedUser", retrievedProfile.getId());
        assertNotNull(retrievedProfile.getSubject());

        //verify public creds empty
        assertTrue(retrievedProfile.getSubject().getPublicCredentials().isEmpty());

        // Verify no KerberosTicket is present
        assertEquals(0, retrievedProfile.getSubject().getPrivateCredentials(KerberosTicket.class).size());

        // Verify the principal is preserved
        assertEquals(1, retrievedProfile.getSubject().getPrincipals(KerberosPrincipal.class).size());
        assertEquals("delegatedUser@REALM",
                retrievedProfile.getSubject().getPrincipals(KerberosPrincipal.class).iterator().next().getName());


        assertTrue(retrievedProfile.isExpired());
    }

    @Test
    public void testKerberosProfileWithDelegatedCredsOnlyAndRetrievalInMongoStoreForSameSessionStore()
    {
        List<String> trustedPackages = new ArrayList<>();
        trustedPackages.add("org.pac4j.core.profile");
        trustedPackages.add("org.finos.legend.server.pac4j.kerberos");

        JEESessionStore sessionStore = new JEESessionStore();
        MongoDbSessionStore kerberosStore = new MongoDbSessionStore(
                "AES",
                3600,
                db.getCollection(SESSION_COLLECTION),
                ImmutableMap.of(JEEContext.class, sessionStore),
                trustedPackages,
                "LegendSSOTest"
        );


        KerberosProfile kerberosProfile = buildProfileWithDelegatedCreds("delegatedUser@REALM");
        kerberosProfile.setId("testDelegatedUser");

        // First call: Store the profile
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(request, response);

        kerberosStore.set(requestContext, Pac4jConstants.USER_PROFILES, kerberosProfile);

        // Verify profile was stored in session
        Optional<Object> retrievedFromSession = kerberosStore.get(requestContext, Pac4jConstants.USER_PROFILES);
        assertTrue(retrievedFromSession.isPresent());
        assertEquals(kerberosProfile.getId(), ((KerberosProfile) retrievedFromSession.get()).getId());

        // Extract SSO cookie
        Cookie[] cookies = response.getCookies();
        assertNotNull(cookies);
        assertTrue(cookies.length > 0);

        // Second call: Simulate new request with SSO cookie
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setCookies(cookies);
        newRequest.setSession(request.getSession());
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        JEEContext newRequestContext = new JEEContext(newRequest, newResponse);

        // Retrieve profile from MongoDB
        Optional<Object> retrievedFromMongo = kerberosStore.get(newRequestContext, Pac4jConstants.USER_PROFILES);
        assertTrue(retrievedFromMongo.isPresent());
        assertTrue(retrievedFromMongo.get() instanceof KerberosProfile);

        // Verify the KerberosProfile data
        KerberosProfile retrievedProfile = (KerberosProfile) retrievedFromMongo.get();
        assertEquals("testDelegatedUser", retrievedProfile.getId());
        assertNotNull(retrievedProfile.getSubject());

        //verify public creds present
        assertFalse(retrievedProfile.getSubject().getPublicCredentials().isEmpty());
        assertEquals(new KerberosPrincipal("delegatedUser@REALM"),retrievedProfile.getSubject().getPublicCredentials(KerberosPrincipal.class).iterator().next());

        // Verify no KerberosTicket is present
        assertEquals(0, retrievedProfile.getSubject().getPrivateCredentials(KerberosTicket.class).size());

        // Verify the principal is preserved
        assertEquals(1, retrievedProfile.getSubject().getPrincipals(KerberosPrincipal.class).size());
        assertEquals("delegatedUser@REALM",
                retrievedProfile.getSubject().getPrincipals(KerberosPrincipal.class).iterator().next().getName());


        assertTrue(retrievedProfile.isExpired());
    }

}