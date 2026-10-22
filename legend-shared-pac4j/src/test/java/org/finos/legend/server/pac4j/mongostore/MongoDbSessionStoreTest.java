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

package org.finos.legend.server.pac4j.mongostore;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.dropwizard.core.Configuration;
import org.finos.legend.server.pac4j.LegendPac4jBundle;
import org.finos.legend.server.pac4j.LegendPac4jConfiguration;
import org.finos.legend.server.pac4j.SessionStoreTestUtil;
import org.finos.legend.server.pac4j.TestClient;
import org.finos.legend.server.pac4j.sessionutil.SessionToken;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pac4j.core.client.Client;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

import static org.junit.Assert.*;

public class MongoDbSessionStoreTest
{

    private static MongoServer server;
    private static InetSocketAddress serverAddress;
    private static MongoClient client;
    private static MongoDatabase db;
    private MongoDbSessionStore store;
    private static final String SESSION_COLLECTION = "sessionData";

    @BeforeClass
    public static void setup()
    {
        server = new MongoServer(new MemoryBackend());
        serverAddress = server.bind();

        client = MongoClients.create(new ConnectionString("mongodb://"+serverAddress.getHostName() +":"+ serverAddress.getPort()));
        db = client.getDatabase("test");
    }

    @AfterClass
    public static void teardown()
    {
        server.shutdown();
        client.close();
    }

    @Before
    public void before()
    {
        List<String> testTrustedPackages = new ArrayList<>();
        testTrustedPackages.add("test.trusted.package");
        store = new MongoDbSessionStore("AES", 100, db.getCollection(SESSION_COLLECTION), ImmutableMap.of(JEEContext.class, new JEESessionStore()), testTrustedPackages, "LegendSSOTest");
    }

    public void emptySessionData()
    {
        this.db.getCollection(SESSION_COLLECTION).drop();

    }

    @Test
    public void testSetCreatesCookie()
    {
        SessionStoreTestUtil.testSetCreatesCookie(store);
    }

    @Test
    public void testMultipleSetsOnlyCreateOneCookie()
    {
        SessionStoreTestUtil.testMultipleSetsOnlyCreateOneCookie(store);
    }

    @Test
    public void testTrustedPackageAdded()
    {
        assertTrue(this.store.getSerializationHelper().getTrustedPackages().contains("test.trusted.package"));
    }

    @Test
    public void testSetThenGetFromSession()
    {
        SessionStoreTestUtil.testSetThenGetFromSession(store);
    }

    @Test
    public void testSetThenGetFromMongo()
    {
        SessionStoreTestUtil.testSetThenGetFromStore(store);
    }

    @Test
    public void testSimulateCookieExpiryThenGetFromSession()
    {
        SessionStoreTestUtil.testSimulateCookieExpiryThenGetFromSession(store);
    }

    @Test
    public void testCanStoreSessionWithoutUsingUnderlyingStore()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(request, response);
        store.set(requestContext, "testKey", "testValue");

        assertEquals("testValue", store.get(requestContext, "testKey").get());
        Cookie[] initialResponseCookies = SessionStoreTestUtil.responseCookies(response);
        // Copy the SSO cookie to the new request but not the underlying j2ESession.
        // The cookie alone should be able to manage sessions in case of non-browser clients
        MockHttpServletRequest newRequest = new MockHttpServletRequest();

        newRequest.setCookies(initialResponseCookies);
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        requestContext = new JEEContext(newRequest, newResponse);

        assertEquals("testValue", store.get(requestContext, "testKey").get()); //should be able to retrieve value
        Cookie[] secondaryResponseCookies = SessionStoreTestUtil.responseCookies(newResponse);
        assertEquals(secondaryResponseCookies.length, 0);
    }

    @Test
    public void getSessionIdWithoutCreateSessionHasNoSideEffects()
    {
        MongoCollection<Document> isolated = db.getCollection("getSessionIdNoCreate");
        isolated.drop();
        MongoDbSessionStore isolatedStore = new MongoDbSessionStore("AES", 100, isolated,
                ImmutableMap.of(JEEContext.class, new JEESessionStore()), new ArrayList<>(), "LegendSSOTest");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext context = new JEEContext(request, response);

        assertEquals(0, isolated.countDocuments());

        Optional<String> sessionId = isolatedStore.getSessionId(context, false);

        assertFalse("createSession=false must not invent a session id", sessionId.isPresent());
        assertEquals("createSession=false must not insert a backing-store row",
                0, isolated.countDocuments());
        assertEquals("createSession=false must not write a Set-Cookie",
                0, SessionStoreTestUtil.responseCookies(response).length);
    }

    @Test
    public void getSessionIdWithCreateSessionStillCreates()
    {
        MongoCollection<Document> isolated = db.getCollection("getSessionIdCreate");
        isolated.drop();
        MongoDbSessionStore isolatedStore = new MongoDbSessionStore("AES", 100, isolated,
                ImmutableMap.of(JEEContext.class, new JEESessionStore()), new ArrayList<>(), "LegendSSOTest");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext context = new JEEContext(request, response);

        isolatedStore.getSessionId(context, true);

        assertEquals("createSession=true must insert the backing-store row",
                1, isolated.countDocuments());
        assertEquals("createSession=true must write the SSO cookie",
                1, SessionStoreTestUtil.responseCookies(response).length);
    }

    @Test
    public void readingProfilesForAnAnonymousRequestHasNoSideEffects()
    {
        MongoCollection<Document> isolated = db.getCollection("anonymousProfileRead");
        isolated.drop();
        MongoDbSessionStore isolatedStore = new MongoDbSessionStore("AES", 100, isolated,
                ImmutableMap.of(JEEContext.class, new JEESessionStore()), new ArrayList<>(), "LegendSSOTest");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(isolatedStore.get(new JEEContext(request, response), Pac4jConstants.USER_PROFILES).isPresent());

        assertEquals("a profile read for a request with no session must not insert a row",
                0, isolated.countDocuments());
        assertEquals("a profile read for a request with no session must not write a Set-Cookie",
                0, SessionStoreTestUtil.responseCookies(response).length);
    }

    @Test
    public void readingProfilesWithAnSsoCookieStillReachesMongo()
    {
        MongoCollection<Document> isolated = db.getCollection("cookieBearingProfileRead");
        isolated.drop();
        MongoDbSessionStore isolatedStore = new MongoDbSessionStore("AES", 100, isolated,
                ImmutableMap.of(JEEContext.class, new JEESessionStore()), new ArrayList<>(), "LegendSSOTest");

        MockHttpServletResponse response = new MockHttpServletResponse();
        isolatedStore.set(new JEEContext(new MockHttpServletRequest(), response), "testKey", "testValue");

        MockHttpServletRequest next = new MockHttpServletRequest();
        next.setCookies(SessionStoreTestUtil.responseCookies(response));

        assertEquals("testValue",
                isolatedStore.get(new JEEContext(next, new MockHttpServletResponse()), "testKey").orElse(null));
    }

    @Test
    public void aFailingHttpSessionWriteThroughDoesNotEvictMongoData()
    {
        MongoCollection<Document> isolated = db.getCollection("failingWriteThrough");
        isolated.drop();

        FailableJeeSessionStore underlying = new FailableJeeSessionStore();
        MongoDbSessionStore isolatedStore = new MongoDbSessionStore("AES", 100, isolated,
                ImmutableMap.of(JEEContext.class, underlying), new ArrayList<>(), "LegendSSOTest");

        MockHttpServletResponse response = new MockHttpServletResponse();
        isolatedStore.set(new JEEContext(new MockHttpServletRequest(), response), "testKey", "testValue");

        Document before = isolated.find(new Document()).first();
        assertNotNull(before);
        assertNotNull(before.getString("testKey"));

        underlying.failOnSet = true;
        MockHttpServletRequest next = new MockHttpServletRequest();
        next.setCookies(SessionStoreTestUtil.responseCookies(response));
        JEEContext nextContext = new JEEContext(next, new MockHttpServletResponse());

        try
        {
            isolatedStore.get(nextContext, "testKey");
        }
        catch (IllegalStateException expected)
        {
            // Propagating is acceptable; silently evicting good data is not.
        }

        Document after = isolated.find(new Document()).first();
        assertNotNull("the session document must survive", after);
        assertEquals("a failure writing through to the HTTP session must not evict the persisted "
                        + "session data", before.getString("testKey"), after.getString("testKey"));
    }

    @Test
    public void ssoCookieDefaultsToSameSiteLax()
    {
        assertEquals("Lax", SessionToken.DEFAULT_SAME_SITE_POLICY);
        assertEquals("Lax", store.getSameSitePolicy());

        String setCookie = setCookieWrittenBy(store);
        assertTrue("an unconfigured SSO cookie must be SameSite=Lax: " + setCookie, setCookie.contains("SameSite=Lax"));
    }

    /** Strict, not Lax: the default is Lax, so only a non-Lax value proves the setting is applied. */
    @Test
    public void ssoCookieCarriesTheConfiguredSameSitePolicy()
    {
        store.setSameSitePolicy("strict");
        assertEquals("Strict", store.getSameSitePolicy());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        store.set(new JEEContext(request, response), "testKey", "testValue");
        String minted = response.getHeaders("Set-Cookie").get(0);
        assertTrue("the minted SSO cookie must be SameSite=Strict: " + minted, minted.contains("SameSite=Strict"));

        MockHttpServletResponse destroyResponse = new MockHttpServletResponse();
        MockHttpServletRequest destroyRequest = new MockHttpServletRequest();
        destroyRequest.setCookies(new Cookie("LegendSSOTest", minted.substring("LegendSSOTest=".length(), minted.indexOf(';'))));
        store.destroySession(new JEEContext(destroyRequest, destroyResponse));
        String expired = destroyResponse.getHeaders("Set-Cookie").get(0);
        assertTrue("the expiring SSO cookie must be SameSite=Strict too: " + expired, expired.contains("SameSite=Strict"));
    }

    @Test
    public void anUnsupportedSameSitePolicyIsRejected()
    {
        assertEquals("Lax", SessionToken.sameSitePolicy(null));
        assertEquals("Lax", SessionToken.sameSitePolicy(""));
        assertEquals("None", SessionToken.sameSitePolicy(" NONE "));
        try
        {
            store.setSameSitePolicy("Strictt");
            fail("a SameSite typo must not silently become Lax");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Strictt"));
        }
    }

    @Test
    public void theBundleHandsTheConfiguredSameSitePolicyToTheStore()
    {
        LegendPac4jConfiguration config = new LegendPac4jConfiguration();
        config.setCallbackPrefix("/test");
        config.setClients(ImmutableList.<Client>of(new TestClient()));
        config.setMongoUri("mongodb://" + serverAddress.getHostName() + ":" + serverAddress.getPort());
        config.setMongoDb("test");
        config.getMongoSession().setEnabled(true);
        config.getMongoSession().setCollection("bundleSameSite");
        config.setSessionTokenSameSite("Strict");

        Config builtConfig = new LegendPac4jBundle<>(c -> config).getPac4jFactory(new Configuration()).build();

        SessionStore built = builtConfig.getSessionStoreFactory().newSessionStore(null);
        assertTrue(built instanceof MongoDbSessionStore);
        assertEquals("Strict", ((MongoDbSessionStore) built).getSameSitePolicy());
    }

    private static String setCookieWrittenBy(MongoDbSessionStore sessionStore)
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        sessionStore.set(new JEEContext(new MockHttpServletRequest(), response), "testKey", "testValue");
        List<String> setCookie = response.getHeaders("Set-Cookie");
        assertEquals(1, setCookie.size());
        return setCookie.get(0);
    }

    private static class FailableJeeSessionStore extends JEESessionStore
    {
        boolean failOnSet;

        @Override
        public void set(WebContext context, String key, Object value)
        {
            if (failOnSet)
            {
                throw new IllegalStateException("Session already invalidated");
            }
            super.set(context, key, value);
        }
    }

    @Test
    public void testForceExpiryOfSessionCookie()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(request, response);
        store.set(requestContext, "testKey", "testValue");

        assertEquals("testValue", store.get(requestContext, "testKey").get());
        Cookie[] initialResponseCookies = SessionStoreTestUtil.responseCookies(response);
        //delete all session data: simulates expiry of stored sessions.
        this.emptySessionData();
        // Copy the SSO cookie to the new request but not the underlying j2ESession: simulates usage of SSO cookie from client
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setCookies(initialResponseCookies);
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        requestContext = new JEEContext(newRequest, newResponse);

        assertFalse(store.get(requestContext, "testKey").isPresent());
        Cookie[] secondaryResponseCookies = SessionStoreTestUtil.responseCookies(newResponse);
        assertEquals(secondaryResponseCookies.length, initialResponseCookies.length);
        assertEquals(secondaryResponseCookies[0].getValue(), initialResponseCookies[0].getValue());
        assertNotEquals(secondaryResponseCookies[0].getMaxAge(), initialResponseCookies[0].getMaxAge());
        assertEquals(secondaryResponseCookies[0].getMaxAge(), 0); //maxAge is now zero which should expire the cookie the moment it goes to client
    }
}
