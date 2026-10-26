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

package org.finos.legend.server.pac4j;

import com.google.common.collect.ImmutableList;
import io.dropwizard.core.Configuration;
import org.junit.Test;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.logout.handler.DefaultSessionLogoutHandler;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.profile.creator.AuthenticatorProfileCreator;
import org.pac4j.core.profile.factory.ProfileManagerFactory;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class LegendSecurityLogicDefaultPathTest
{
    private final AtomicInteger sessionReads = new AtomicInteger();
    private final AtomicInteger sessionWrites = new AtomicInteger();
    private final AtomicInteger authentications = new AtomicInteger();
    private final List<String> sessionEvents = new ArrayList<>();
    private HttpSession browserSession = new MockHttpSession();

    private final JEESessionStore store = new JEESessionStore()
    {
        @Override
        public Optional<Object> get(WebContext context, String key)
        {
            if (Pac4jConstants.USER_PROFILES.equals(key))
            {
                sessionReads.incrementAndGet();
            }
            return super.get(context, key);
        }

        @Override
        public void set(WebContext context, String key, Object value)
        {
            if (Pac4jConstants.USER_PROFILES.equals(key))
            {
                sessionWrites.incrementAndGet();
                sessionEvents.add("save");
            }
            super.set(context, key, value);
        }

        @Override
        public boolean renewSession(WebContext context)
        {
            sessionEvents.add("renew");
            return super.renewSession(context);
        }
    };

    @Test
    public void multiProfileOff_aRefusedSessionReadDoesNotGrantTheStoredProfile()
    {
        assertUnauthorized(performWithStoredProfile(false, ImmutableList.of(TestClient.withoutCredentials("A"), unannotated("B"))));
    }

    @Test
    public void nonBrowserCall_aRefusedSessionReadDoesNotGrantTheStoredProfile()
    {
        assertUnauthorized(performWithStoredProfile(true, ImmutableList.of(TestClient.withoutCredentials("A"), unannotated("B"))));
    }

    @Test
    public void anAllowedSessionReadGrantsTheStoredProfile()
    {
        Object result = performWithStoredProfile(true, ImmutableList.of(TestClient.withoutCredentials("A")));

        assertTrue("expected access granted from the stored profile, got " + result, result instanceof Collection);
        assertEquals("alice", ((UserProfile) ((Collection<?>) result).iterator().next()).getId());
    }

    @Test
    public void sessionProfilesAreReadOncePerRequest()
    {
        performWithStoredProfile(true, ImmutableList.of(TestClient.withoutCredentials("A")));

        assertEquals("session profile reads per request", 1, sessionReads.get());
    }

    /**
     * With an unannotated client in play the profile is neither saved nor read, so each request authenticates.
     */
    @Test
    public void aProfileIsNotSavedWhereItWillNotBeReadBack()
    {
        Config config = bundleConfig(authenticating("A"), unannotated("B"));

        assertGranted(performThroughTheBundle(config, "A,B"));
        assertGranted(performThroughTheBundle(config, "A,B"));

        assertEquals("profile writes to the session", 0, sessionWrites.get());
        assertEquals("authentications", 2, authentications.get());
    }

    @Test
    public void aProfileIsSavedAndReadBackWhenEveryClientInPlayIsAnnotated()
    {
        Config config = bundleConfig(authenticating("A"), unannotated("B"));

        assertGranted(performThroughTheBundle(config, "A"));
        assertGranted(performThroughTheBundle(config, "A"));

        assertEquals("profile writes to the session", 1, sessionWrites.get());
        assertEquals("authentications", 1, authentications.get());
    }

    @Test
    public void aClientThatSaysNotToSaveIsNotSaved()
    {
        Config config = bundleConfig(authenticating("A"));
        ((DirectClient) config.getClients().findClient("A").get()).setSaveProfileInSession(false);

        assertGranted(performThroughTheBundle(config, "A"));

        assertEquals("profile writes to the session", 0, sessionWrites.get());
        assertEquals("nothing is saved, so the session is not renewed", 0, sessionEvents.size());
    }

    @Test
    public void aDirectSignInRenewsTheSessionBeforeSavingTheProfile()
    {
        Config config = bundleConfig(authenticating("A"));
        HttpSession planted = browserSession;
        planted.setAttribute(Pac4jConstants.REQUESTED_URL, "/requested");

        assertGranted(performThroughTheBundle(config, "A"));

        assertEquals(ImmutableList.of("renew", "save"), sessionEvents);
        assertNotSame("the planted session is replaced", planted, browserSession);
        assertEquals("its data moves along", "/requested", browserSession.getAttribute(Pac4jConstants.REQUESTED_URL));
    }

    @Test
    public void aDirectSignInKeepsSingleLogoutTrackingTheSession()
    {
        Config config = bundleConfig(authenticating("A"), trackingTheSession("Oidc"));
        DefaultSessionLogoutHandler handler = new DefaultSessionLogoutHandler();
        config.setSessionLogoutHandler(handler);
        String signedInSessionId = browserSession.getId();
        // As OidcProfileCreator records the identity provider's sid at the callback.
        handler.recordSession(new CallContext(browserRequest(), store), "sid-1");

        assertGranted(performThroughTheBundle(config, "A"));
        assertNotEquals("the direct sign-in renewed the session", signedInSessionId, browserSession.getId());
        assertFalse(new ProfileManager(browserRequest(), store).getProfiles().isEmpty());

        // The identity provider's front-channel logout, through the browser.
        handler.destroySession(new CallContext(browserRequest(), store), "sid-1");

        assertTrue("the user is logged out", new ProfileManager(browserRequest(), store).getProfiles().isEmpty());
    }

    private static Config bundleConfig(Client... clients)
    {
        LegendPac4jConfiguration legendConfig = new LegendPac4jConfiguration();
        legendConfig.setCallbackPrefix("/test");
        legendConfig.setClients(ImmutableList.copyOf(clients));
        return new LegendPac4jBundle<>(c -> legendConfig).getPac4jFactory(new Configuration()).build();
    }

    /** As a client that keeps cookies: the next call carries the session this one left, which sign-in renews. */
    private Object performThroughTheBundle(Config config, String clientNames)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/thing");
        request.addHeader("User-Agent", "curl/8.0");
        request.setSession(browserSession);
        JEEContext ctx = new JEEContext(request, new MockHttpServletResponse());
        config.setWebContextFactory(p -> ctx);
        config.setSessionStoreFactory(p -> store);
        config.setHttpActionAdapter((action, wc) -> action);
        config.setProfileManagerFactory(ProfileManagerFactory.DEFAULT);
        Object result = config.getSecurityLogic().perform(config, (wc, ss, granted) -> granted, clientNames, "none", "none", null);
        if (request.getSession(false) != null)
        {
            browserSession = request.getSession(false);
        }
        return result;
    }

    private JEEContext browserRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/thing");
        request.setSession(browserSession);
        return new JEEContext(request, new MockHttpServletResponse());
    }

    /** Follows a session renewal in the session logout handler, as OidcClient does. */
    private static TestClient trackingTheSession(String name)
    {
        TestClient client = new TestClient(name)
        {
            @Override
            public void notifySessionRenewal(CallContext ctx, String oldSessionId)
            {
                findSessionLogoutHandler().renewSession(ctx, oldSessionId);
            }
        };
        client.setCredentialsExtractor(ctx -> Optional.empty());
        client.setAuthenticator(Authenticator.ALWAYS_VALIDATE);
        client.setProfileCreator(AuthenticatorProfileCreator.INSTANCE);
        return client;
    }

    private TestClient authenticating(String name)
    {
        TestClient client = new TestClient(name);
        client.setCredentialsExtractor(ctx -> Optional.of(new TokenCredentials("token")));
        client.setAuthenticator((ctx, credentials) ->
        {
            authentications.incrementAndGet();
            return Optional.of(credentials);
        });
        client.setProfileCreator((ctx, credentials) ->
        {
            CommonProfile profile = new CommonProfile();
            profile.setId("alice");
            return Optional.of(profile);
        });
        return client;
    }

    private static void assertGranted(Object result)
    {
        assertTrue("expected access granted, got " + result, result instanceof Collection);
    }

    private Object performWithStoredProfile(boolean multiProfile, List<Client> clients)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/thing");
        request.addHeader("User-Agent", "curl/8.0");
        JEEContext ctx = new JEEContext(request, new MockHttpServletResponse());
        CommonProfile stored = new CommonProfile();
        stored.setId("alice");
        stored.setClientName("A");
        LinkedHashMap<String, UserProfile> profiles = new LinkedHashMap<>();
        profiles.put("A", stored);
        store.set(ctx, Pac4jConstants.USER_PROFILES, profiles);

        Config config = new Config(new Clients("/callback", clients));
        config.setWebContextFactory(p -> ctx);
        config.setSessionStoreFactory(p -> store);
        config.setHttpActionAdapter((action, wc) -> action);
        config.setProfileManagerFactory(ProfileManagerFactory.DEFAULT);

        String clientNames = String.join(",", clients.stream().map(Client::getName).toArray(String[]::new));
        LegendSecurityLogic logic = new LegendSecurityLogic();
        logic.setMultiProfile(multiProfile);
        return logic.perform(config, (wc, ss, granted) -> granted, clientNames, "none", "none", null);
    }

    private static Client unannotated(String name)
    {
        SecondTestClient client = new SecondTestClient();
        client.setName(name);
        client.setCredentialsExtractor(ctx -> Optional.empty());
        client.setAuthenticator(Authenticator.ALWAYS_VALIDATE);
        client.setProfileCreator(AuthenticatorProfileCreator.INSTANCE);
        assertFalse(client.getClass().isAnnotationPresent(SerializableProfile.class));
        return client;
    }

    private static void assertUnauthorized(Object result)
    {
        assertTrue("the storage decision refuses the session, so the stored profile must not be granted: " + result,
                result instanceof HttpAction && ((HttpAction) result).getCode() == 401);
    }
}
