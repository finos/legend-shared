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

import com.google.common.collect.ImmutableMap;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.finos.legend.server.pac4j.SessionStoreTestUtil;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.session.JEESessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.Cookie;

import static org.junit.Assert.*;

public class MongoDbSessionStoreTest
{

    private static MongoServer server;
    private static MongoClient client;
    private static MongoDatabase db;
    private MongoDbSessionStore store;
    private static final String SESSION_COLLECTION = "sessionData";

    @BeforeClass
    public static void setup()
    {
        server = new MongoServer(new MemoryBackend());
        InetSocketAddress serverAddress = server.bind();

        client = new MongoClient(new ServerAddress(serverAddress));
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
        Cookie[] initialResponseCookies = response.getCookies();
        // Copy the SSO cookie to the new request but not the underlying j2ESession.
        // The cookie alone should be able to manage sessions in case of non-browser clients
        MockHttpServletRequest newRequest = new MockHttpServletRequest();

        newRequest.setCookies(initialResponseCookies);
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        requestContext = new JEEContext(newRequest, newResponse);

        assertEquals("testValue", store.get(requestContext, "testKey").get()); //should be able to retrieve value
        Cookie[] secondaryResponseCookies = newResponse.getCookies();
        assertEquals(secondaryResponseCookies.length, 0);
    }

    @Test
    public void testForceExpiryOfSessionCookie()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(request, response);
        store.set(requestContext, "testKey", "testValue");

        assertEquals("testValue", store.get(requestContext, "testKey").get());
        Cookie[] initialResponseCookies = response.getCookies();
        //delete all session data: simulates expiry of stored sessions.
        this.emptySessionData();
        // Copy the SSO cookie to the new request but not the underlying j2ESession: simulates usage of SSO cookie from client
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setCookies(initialResponseCookies);
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        requestContext = new JEEContext(newRequest, newResponse);

        assertFalse(store.get(requestContext, "testKey").isPresent());
        Cookie[] secondaryResponseCookies = newResponse.getCookies();
        assertEquals(secondaryResponseCookies.length, initialResponseCookies.length);
        assertEquals(secondaryResponseCookies[0].getValue(), initialResponseCookies[0].getValue());
        assertNotEquals(secondaryResponseCookies[0].getMaxAge(), initialResponseCookies[0].getMaxAge());
        assertEquals(secondaryResponseCookies[0].getMaxAge(), 0); //maxAge is now zero which should expire the cookie the moment it goes to client
    }
}
