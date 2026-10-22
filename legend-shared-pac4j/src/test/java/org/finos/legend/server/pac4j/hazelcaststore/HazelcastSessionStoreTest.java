// Copyright 2023 Goldman Sachs
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

package org.finos.legend.server.pac4j.hazelcaststore;

import com.google.common.collect.ImmutableMap;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.finos.legend.server.pac4j.SessionStoreTestUtil;
import org.finos.legend.server.pac4j.sessionutil.UuidUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.pac4j.jax.rs.pac4j.JaxRsContext;
import org.pac4j.jax.rs.servlet.pac4j.ServletSessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HazelcastSessionStoreTest
{

    private static final String HAZELCAST_CONFIG_FILE_PATH = "src/test/resources/hazelcast.yaml";
    private static final String HAZELCAST_INSTANCE_NAME = "legend-hazelcast-session-store";
    private static final String HAZELCAST_MAP_NAME = "session-store";
    private static final String SSO_COOKIE_NAME = "LegendSSOTest";

    private HazelcastSessionStore store;

    @Before
    public void before()
    {
        store = new HazelcastSessionStore(HAZELCAST_CONFIG_FILE_PATH, ImmutableMap.of(
                JEEContext.class, new JEESessionStore(), JaxRsContext.class, ServletSessionStore.INSTANCE), SSO_COOKIE_NAME);
    }

    @After
    public void after()
    {
        Hazelcast.shutdownAll();
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
    public void testSetThenGetFromHazelcast()
    {
        SessionStoreTestUtil.testSetThenGetFromStore(store);
    }

    @Test
    public void getSessionIdWithoutCreateSessionHasNoSideEffects()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext context = new JEEContext(request, response);

        IMap<UUID, Map<String, Object>> imap = hazelcastImap();
        int before = imap.size();

        Optional<String> sessionId = store.getSessionId(context, false);

        assertFalse("createSession=false must not invent a session id", sessionId.isPresent());
        assertEquals("createSession=false must not insert a Hazelcast entry", before, imap.size());
        assertEquals("createSession=false must not write a Set-Cookie",
                0, SessionStoreTestUtil.responseCookies(response).length);
    }

    @Test
    public void readingProfilesForAnAnonymousRequestHasNoSideEffects()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        IMap<UUID, Map<String, Object>> imap = hazelcastImap();
        int before = imap.size();

        assertFalse(store.get(new JEEContext(request, response), "userProfiles").isPresent());

        assertEquals("a profile read for a request with no session must not insert an entry",
                before, imap.size());
        assertEquals("a profile read for a request with no session must not write a Set-Cookie",
                0, SessionStoreTestUtil.responseCookies(response).length);
    }

    @Test
    public void readingProfilesWithAnSsoCookieStillReachesHazelcast()
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        store.set(new JEEContext(new MockHttpServletRequest(), response), "userProfiles", "alice-profile");

        MockHttpServletRequest next = new MockHttpServletRequest();
        next.setCookies(SessionStoreTestUtil.responseCookies(response));

        assertEquals("alice-profile",
                store.get(new JEEContext(next, new MockHttpServletResponse()), "userProfiles").orElse(null));
    }

    @Test
    public void ssoCookieCarriesTheSameSitePolicy()
    {
        MockHttpServletResponse unconfigured = new MockHttpServletResponse();
        store.set(new JEEContext(new MockHttpServletRequest(), unconfigured), "testKey", "testValue");
        assertTrue(unconfigured.getHeader("Set-Cookie"), unconfigured.getHeader("Set-Cookie").contains("SameSite=Lax"));

        store.setSameSitePolicy("Strict");
        MockHttpServletResponse configured = new MockHttpServletResponse();
        store.set(new JEEContext(new MockHttpServletRequest(), configured), "testKey", "testValue");
        assertTrue(configured.getHeader("Set-Cookie"), configured.getHeader("Set-Cookie").contains("SameSite=Strict"));
    }

    @Test
    public void getSessionIdWithCreateSessionStillCreates()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext context = new JEEContext(request, response);

        IMap<UUID, Map<String, Object>> imap = hazelcastImap();
        int before = imap.size();

        store.getSessionId(context, true);

        assertEquals("createSession=true must insert the Hazelcast entry", before + 1, imap.size());
        assertEquals("createSession=true must write the SSO cookie",
                1, SessionStoreTestUtil.responseCookies(response).length);
    }

    @Test
    public void getAfterEviction_returnsEmptyAndReSeedsHazelcastEntry()
    {
        MockHttpServletRequest nodeARequest = new MockHttpServletRequest();
        MockHttpServletResponse nodeAResponse = new MockHttpServletResponse();
        JEEContext nodeAContext = new JEEContext(nodeARequest, nodeAResponse);
        store.set(nodeAContext, "userProfiles", "alice-profile");

        Cookie ssoCookie = findSsoCookie(SessionStoreTestUtil.responseCookies(nodeAResponse));
        assertNotNull(ssoCookie);
        UUID sessionId = sessionIdFromCookie(ssoCookie);

        IMap<UUID, Map<String, Object>> imap = hazelcastImap();
        assertTrue(imap.containsKey(sessionId));

        imap.evict(sessionId);
        assertFalse(imap.containsKey(sessionId));

        MockHttpServletRequest nodeBRequest = new MockHttpServletRequest();
        nodeBRequest.setCookies(SessionStoreTestUtil.responseCookies(nodeAResponse));
        MockHttpServletResponse nodeBResponse = new MockHttpServletResponse();
        JEEContext nodeBContext = new JEEContext(nodeBRequest, nodeBResponse);

        Optional<Object> result = store.get(nodeBContext, "userProfiles");

        assertFalse("get() should return empty for the evicted key", result.isPresent());
        assertTrue("get() must re-seed the Hazelcast entry after eviction", imap.containsKey(sessionId));
        assertEquals("re-seeded entry must be empty", 0, imap.get(sessionId).size());
    }

    @Test
    public void setAfterEviction_upsertsHazelcastEntry_andValueIsVisibleClusterWide()
    {
        MockHttpServletRequest nodeARequest = new MockHttpServletRequest();
        MockHttpServletResponse nodeAResponse = new MockHttpServletResponse();
        JEEContext nodeAContext = new JEEContext(nodeARequest, nodeAResponse);
        store.set(nodeAContext, "userProfiles", "alice-profile");

        UUID sessionId = sessionIdFromCookie(findSsoCookie(SessionStoreTestUtil.responseCookies(nodeAResponse)));
        IMap<UUID, Map<String, Object>> imap = hazelcastImap();

        imap.evict(sessionId);
        assertFalse(imap.containsKey(sessionId));

        MockHttpServletRequest nodeBRequest = new MockHttpServletRequest();
        nodeBRequest.setCookies(SessionStoreTestUtil.responseCookies(nodeAResponse));
        MockHttpServletResponse nodeBResponse = new MockHttpServletResponse();
        JEEContext nodeBContext = new JEEContext(nodeBRequest, nodeBResponse);
        store.set(nodeBContext, "csrfToken", "node-b-token");

        assertTrue("set() must upsert the missing Hazelcast entry", imap.containsKey(sessionId));
        assertEquals("node-b-token", imap.get(sessionId).get("csrfToken"));

        MockHttpServletRequest nodeCRequest = new MockHttpServletRequest();
        nodeCRequest.setCookies(SessionStoreTestUtil.responseCookies(nodeAResponse));
        JEEContext nodeCContext = new JEEContext(nodeCRequest, new MockHttpServletResponse());
        assertEquals("node-b-token", store.get(nodeCContext, "csrfToken").orElse(null));
    }

    @Test
    public void evictionFollowedByReAuth_restoresSessionUnderSameSessionId()
    {
        MockHttpServletRequest loginReq = new MockHttpServletRequest();
        MockHttpServletResponse loginResp = new MockHttpServletResponse();
        JEEContext loginCtx = new JEEContext(loginReq, loginResp);
        store.set(loginCtx, "userProfiles", "alice-profile");

        Cookie originalCookie = findSsoCookie(SessionStoreTestUtil.responseCookies(loginResp));
        UUID sessionId = sessionIdFromCookie(originalCookie);
        IMap<UUID, Map<String, Object>> imap = hazelcastImap();

        imap.evict(sessionId);

        MockHttpServletRequest protectedReq = new MockHttpServletRequest();
        protectedReq.setCookies(SessionStoreTestUtil.responseCookies(loginResp));
        MockHttpServletResponse protectedResp = new MockHttpServletResponse();
        JEEContext protectedCtx = new JEEContext(protectedReq, protectedResp);
        assertFalse(store.get(protectedCtx, "userProfiles").isPresent());

        MockHttpServletRequest callbackReq = new MockHttpServletRequest();
        callbackReq.setCookies(SessionStoreTestUtil.responseCookies(loginResp));
        MockHttpServletResponse callbackResp = new MockHttpServletResponse();
        JEEContext callbackCtx = new JEEContext(callbackReq, callbackResp);
        store.set(callbackCtx, "userProfiles", "alice-profile-after-reauth");

        Cookie cookieAfterCallback = findSsoCookie(SessionStoreTestUtil.responseCookies(callbackResp));
        if (cookieAfterCallback != null)
        {
            assertEquals(sessionId, sessionIdFromCookie(cookieAfterCallback));
        }
        assertEquals("alice-profile-after-reauth", imap.get(sessionId).get("userProfiles"));

        MockHttpServletRequest afterReq = new MockHttpServletRequest();
        afterReq.setCookies(SessionStoreTestUtil.responseCookies(loginResp));
        JEEContext afterCtx = new JEEContext(afterReq, new MockHttpServletResponse());
        assertEquals("alice-profile-after-reauth", store.get(afterCtx, "userProfiles").orElse(null));
    }

    @Test
    public void requestWithNoCookie_createsFreshSession()
    {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        JEEContext ctx = new JEEContext(req, resp);

        store.set(ctx, "userProfiles", "alice-profile");

        Cookie cookie = findSsoCookie(SessionStoreTestUtil.responseCookies(resp));
        assertNotNull(cookie);
        UUID sessionId = sessionIdFromCookie(cookie);

        IMap<UUID, Map<String, Object>> imap = hazelcastImap();
        assertTrue(imap.containsKey(sessionId));
        assertEquals("alice-profile", imap.get(sessionId).get("userProfiles"));
    }

    @Test
    public void clearingCookieStillCreatesBrandNewSession()
    {
        MockHttpServletRequest origReq = new MockHttpServletRequest();
        MockHttpServletResponse origResp = new MockHttpServletResponse();
        JEEContext origCtx = new JEEContext(origReq, origResp);
        store.set(origCtx, "userProfiles", "alice-profile");
        UUID oldSessionId = sessionIdFromCookie(findSsoCookie(SessionStoreTestUtil.responseCookies(origResp)));

        MockHttpServletRequest freshReq = new MockHttpServletRequest();
        MockHttpServletResponse freshResp = new MockHttpServletResponse();
        JEEContext freshCtx = new JEEContext(freshReq, freshResp);
        store.set(freshCtx, "userProfiles", "alice-after-cookie-clear");

        UUID newSessionId = sessionIdFromCookie(findSsoCookie(SessionStoreTestUtil.responseCookies(freshResp)));
        assertNotEquals(oldSessionId, newSessionId);

        IMap<UUID, Map<String, Object>> imap = hazelcastImap();
        assertEquals("alice-after-cookie-clear", imap.get(newSessionId).get("userProfiles"));
        assertNotNull(imap.get(oldSessionId));
    }

    @Test
    public void concurrentSetsOnSameSession_doNotLoseUpdates() throws Exception
    {
        MockHttpServletRequest loginReq = new MockHttpServletRequest();
        MockHttpServletResponse loginResp = new MockHttpServletResponse();
        JEEContext loginCtx = new JEEContext(loginReq, loginResp);
        store.set(loginCtx, "seed", "seed");

        Cookie[] cookies = SessionStoreTestUtil.responseCookies(loginResp);
        UUID sessionId = sessionIdFromCookie(findSsoCookie(cookies));
        IMap<UUID, Map<String, Object>> imap = hazelcastImap();

        int writers = 16;
        int writesPerThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        try
        {
            for (int t = 0; t < writers; t++)
            {
                final int threadId = t;
                pool.submit(() ->
                {
                    try
                    {
                        start.await();
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < writesPerThread; i++)
                    {
                        MockHttpServletRequest req = new MockHttpServletRequest();
                        req.setCookies(cookies);
                        MockHttpServletResponse resp = new MockHttpServletResponse();
                        store.set(new JEEContext(req, resp),
                                "t" + threadId + "-i" + i,
                                "v" + threadId + "-" + i);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        }
        finally
        {
            if (!pool.isTerminated())
            {
                pool.shutdownNow();
            }
        }

        Map<String, Object> finalData = new HashMap<>(imap.get(sessionId));
        for (int t = 0; t < writers; t++)
        {
            for (int i = 0; i < writesPerThread; i++)
            {
                String expectedKey = "t" + t + "-i" + i;
                assertEquals("v" + t + "-" + i, finalData.get(expectedKey));
            }
        }
        assertEquals(writers * writesPerThread + 1, finalData.size());
    }

    private static Cookie findSsoCookie(Cookie[] cookies)
    {
        if (cookies == null)
        {
            return null;
        }
        for (Cookie c : cookies)
        {
            if (SSO_COOKIE_NAME.equals(c.getName()))
            {
                return c;
            }
        }
        return null;
    }

    private static UUID sessionIdFromCookie(Cookie cookie)
    {
        String[] parts = cookie.getValue().split("/", 2);
        return UuidUtils.fromHexString(parts[0]);
    }

    @SuppressWarnings("unchecked")
    private static IMap<UUID, Map<String, Object>> hazelcastImap()
    {
        HazelcastInstance instance = Hazelcast.getHazelcastInstanceByName(HAZELCAST_INSTANCE_NAME);
        assertNotNull(instance);
        return (IMap<UUID, Map<String, Object>>) (IMap<?, ?>) instance.getMap(HAZELCAST_MAP_NAME);
    }
}
