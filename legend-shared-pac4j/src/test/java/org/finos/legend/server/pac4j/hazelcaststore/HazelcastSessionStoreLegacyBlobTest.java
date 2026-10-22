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

package org.finos.legend.server.pac4j.hazelcaststore;

import com.google.common.collect.ImmutableMap;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.OperationTimeoutException;
import com.hazelcast.map.IMap;
import org.finos.legend.server.pac4j.SessionStoreTestUtil;
import org.finos.legend.server.pac4j.sessionutil.UuidUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Two pac4j versions cannot share a classpath, so {@link UndeserializableValue} stands in for a
 * pac4j 5 profile: its {@code readObject} throws the {@code ClassCastException} that pac4j 6 throws
 * on real pac4j 5 bytes (see {@code CrossVersionProfileSerializationTest}).
 */
public class HazelcastSessionStoreLegacyBlobTest
{
    private static final String HAZELCAST_CONFIG_FILE_PATH = "src/test/resources/hazelcast.yaml";
    private static final String HAZELCAST_INSTANCE_NAME = "legend-hazelcast-session-store";
    private static final String HAZELCAST_MAP_NAME = "session-store";
    private static final String SSO_COOKIE_NAME = "LegendSSOTest";

    static class UndeserializableValue implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
        {
            in.defaultReadObject();
            throw new ClassCastException(
                    "class java.util.HashSet cannot be cast to class java.lang.String");
        }
    }

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
    public void legacyValueDoesNotPropagateAndEntryIsEvicted()
    {
        UUID sessionId = seedSessionWithLegacyValue();
        IMap<UUID, Map<String, Object>> imap = hazelcastImap();

        Optional<Object> result = store.get(contextFor(sessionId), "userProfiles");
        assertFalse("an unreadable session value must read as absent", result.isPresent());

        assertFalse("the unreadable entry must be evicted so the failure does not repeat",
                mapStillHoldsUnreadableEntry(imap, sessionId));

        assertFalse(store.get(contextFor(sessionId), "userProfiles").isPresent());
    }

    @Test
    public void reAuthenticationAfterEvictionWorks()
    {
        UUID sessionId = seedSessionWithLegacyValue();

        assertFalse(store.get(contextFor(sessionId), "userProfiles").isPresent());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie[]{ssoCookie(sessionId)});
        MockHttpServletResponse response = new MockHttpServletResponse();
        store.set(new JEEContext(request, response), "userProfiles", "alice-after-reauth");

        assertEquals("alice-after-reauth",
                store.get(contextFor(sessionId), "userProfiles").orElse(null));
    }

    @Test
    public void destroySessionOnPoisonedEntryDoesNotThrow()
    {
        UUID sessionId = seedSessionWithLegacyValue();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie[]{ssoCookie(sessionId)});
        store.destroySession(new JEEContext(request, new MockHttpServletResponse()));

        assertFalse(store.get(contextFor(sessionId), "userProfiles").isPresent());
    }

    /**
     * Injected at the {@code IMap} boundary, not from a {@code readObject}: a failure raised during
     * deserialization is wrapped in {@code HazelcastSerializationException} and is a format problem.
     */
    @Test
    public void transientClusterFailureDoesNotWipeTheSession() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        store.set(new JEEContext(request, response), "userProfiles", "alice-profile");

        Cookie cookie = findSsoCookie(SessionStoreTestUtil.responseCookies(response));
        assertNotNull(cookie);
        UUID sessionId = sessionIdFromCookie(cookie);

        failNextMapGetWith(new OperationTimeoutException("cluster busy"));
        try
        {
            store.get(contextFor(sessionId), "userProfiles");
            fail("a transient cluster failure must not be swallowed");
        }
        catch (OperationTimeoutException expected)
        {
            assertEquals("cluster busy", expected.getMessage());
        }

        assertEquals("a transient Hazelcast read failure must not wipe the session entry",
                "alice-profile", store.get(contextFor(sessionId), "userProfiles").orElse(null));
    }

    @SuppressWarnings("unchecked")
    private void failNextMapGetWith(RuntimeException failure) throws Exception
    {
        Field field = HazelcastSessionStore.class.getDeclaredField("hazelcastMap");
        field.setAccessible(true);
        IMap<UUID, Map<String, Object>> real = (IMap<UUID, Map<String, Object>>) field.get(store);
        AtomicBoolean armed = new AtomicBoolean(true);

        Object proxy = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{IMap.class},
                (p, method, args) ->
                {
                    if ("get".equals(method.getName()) && armed.getAndSet(false))
                    {
                        throw failure;
                    }
                    try
                    {
                        return method.invoke(real, args);
                    }
                    catch (InvocationTargetException e)
                    {
                        throw e.getCause();
                    }
                });
        field.set(store, proxy);
    }

    @Test
    public void readableValueIsUntouched()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        store.set(new JEEContext(request, response), "userProfiles", "alice-profile");

        Cookie cookie = findSsoCookie(SessionStoreTestUtil.responseCookies(response));
        assertNotNull(cookie);
        UUID sessionId = sessionIdFromCookie(cookie);

        assertEquals("alice-profile", store.get(contextFor(sessionId), "userProfiles").orElse(null));
        assertEquals("alice-profile", hazelcastImap().get(sessionId).get("userProfiles"));
    }

    /** Hazelcast deserializes on read, so a surviving poisoned entry shows up as a throw. */
    private boolean mapStillHoldsUnreadableEntry(IMap<UUID, Map<String, Object>> imap, UUID sessionId)
    {
        try
        {
            Map<String, Object> data = imap.get(sessionId);
            return data != null && data.get("userProfiles") instanceof UndeserializableValue;
        }
        catch (RuntimeException e)
        {
            return true;
        }
    }

    private UUID seedSessionWithLegacyValue()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        store.set(new JEEContext(request, response), "seed", "seed");

        Cookie cookie = findSsoCookie(SessionStoreTestUtil.responseCookies(response));
        assertNotNull(cookie);
        UUID sessionId = sessionIdFromCookie(cookie);

        Map<String, Object> data = new HashMap<>();
        data.put("userProfiles", new UndeserializableValue());
        hazelcastImap().put(sessionId, data);
        return sessionId;
    }

    private JEEContext contextFor(UUID sessionId)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie[]{ssoCookie(sessionId)});
        return new JEEContext(request, new MockHttpServletResponse());
    }

    private Cookie ssoCookie(UUID sessionId)
    {
        MockHttpServletResponse probe = new MockHttpServletResponse();
        MockHttpServletRequest probeRequest = new MockHttpServletRequest();
        store.set(new JEEContext(probeRequest, probe), "probe", "probe");
        Cookie generated = findSsoCookie(SessionStoreTestUtil.responseCookies(probe));
        assertNotNull(generated);
        // The store looks sessions up by id only, so any well-formed key half will do.
        String keyHalf = generated.getValue().split("/", 2)[1];
        return new Cookie(SSO_COOKIE_NAME, UuidUtils.toHexString(sessionId) + "/" + keyHalf);
    }

    private static Cookie findSsoCookie(Cookie[] cookies)
    {
        if (cookies != null)
        {
            for (Cookie c : cookies)
            {
                if (SSO_COOKIE_NAME.equals(c.getName()))
                {
                    return c;
                }
            }
        }
        return null;
    }

    private static UUID sessionIdFromCookie(Cookie cookie)
    {
        return UuidUtils.fromHexString(cookie.getValue().split("/", 2)[0]);
    }

    @SuppressWarnings("unchecked")
    private static IMap<UUID, Map<String, Object>> hazelcastImap()
    {
        HazelcastInstance instance = Hazelcast.getHazelcastInstanceByName(HAZELCAST_INSTANCE_NAME);
        assertNotNull(instance);
        return (IMap<UUID, Map<String, Object>>) (IMap<?, ?>) instance.getMap(HAZELCAST_MAP_NAME);
    }
}
