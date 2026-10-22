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
import org.mockito.Mockito;
import org.pac4j.core.client.BaseClient;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.client.direct.AnonymousClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.http.FoundAction;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.test.context.MockWebContext;
import org.pac4j.test.context.session.MockSessionStore;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * pac4j 6 reads multiProfile from each client. Set there, it lets a non-browser call carry on past
 * the client that authenticated it, so a later client that redirects when it finds no credentials
 * turns an authenticated {@code curl} into a 302.
 */
public class NonBrowserCallStopsAtTheFirstProfileTest
{
    private static final String GRANTED = "granted";

    @Test
    public void anAuthenticatedNonBrowserCallIsNotSentToALaterRedirectingClient()
    {
        RedirectingClient redirecting = new RedirectingClient();
        Config config = buildWithMultiProfile(redirecting);

        Object result = performAsCurl(config);

        assertEquals("the call authenticated by the first client must be granted", GRANTED, result);
        assertEquals("the redirecting client must not be consulted", 0, redirecting.calls.get());
    }

    @Test
    public void withMultiProfileOnTheClientsTheSameCallIsRedirected()
    {
        RedirectingClient redirecting = new RedirectingClient();
        Config config = buildWithMultiProfile(redirecting);
        config.getClients().getClients().forEach(c -> ((BaseClient) c).setMultiProfile(true));

        Object result = performAsCurl(config);

        assertTrue("expected the redirecting client's 302, got " + result, result instanceof FoundAction);
        assertEquals(1, redirecting.calls.get());
    }

    @Test
    public void theCallbackSavesWithTheConfiguredMultiProfile()
    {
        assertCallbackSavesWith(true);
        assertCallbackSavesWith(false);
    }

    private static void assertCallbackSavesWith(boolean configured)
    {
        ProfileManager manager = Mockito.mock(ProfileManager.class);
        UserProfile profile = Mockito.mock(UserProfile.class);
        CallContext ctx = new CallContext(MockWebContext.create(), new MockSessionStore(), (c, s) -> manager);

        // The client's own flag, which pac4j 6 would otherwise use, is the opposite of the
        // configured value.
        new LegendCallbackLogic(configured).saveUserProfile(ctx, new Config(), profile, true, !configured, false);

        verify(manager).save(true, profile, configured);
    }

    private static Config buildWithMultiProfile(RedirectingClient redirecting)
    {
        LegendPac4jConfiguration legendConfig = new LegendPac4jConfiguration();
        legendConfig.setCallbackPrefix("/test");
        legendConfig.setMultiProfile(true);
        legendConfig.setClients(ImmutableList.<Client>of(new AnonymousClient(), redirecting));

        Config config = new LegendPac4jBundle<>(c -> legendConfig).getPac4jFactory(new Configuration()).build();
        MockWebContext context = MockWebContext.create().addRequestHeader("User-Agent", "curl/8.5.0");
        MockSessionStore sessionStore = new MockSessionStore();
        config.setWebContextFactory(p -> context);
        config.setSessionStoreFactory(p -> sessionStore);
        config.setHttpActionAdapter((action, ctx) -> action);
        return config;
    }

    private static Object performAsCurl(Config config)
    {
        return config.getSecurityLogic().perform(config, (ctx, sessionStore, profiles) -> GRANTED,
                "AnonymousClient,redirecting", "none", "", null);
    }

    private static class RedirectingClient extends DirectClient
    {
        final AtomicInteger calls = new AtomicInteger();

        RedirectingClient()
        {
            setName("redirecting");
        }

        @Override
        protected void internalInit(boolean forceReinit)
        {
            setCredentialsExtractorIfUndefined(ctx ->
            {
                calls.incrementAndGet();
                throw new FoundAction("https://login.example.com/");
            });
            setAuthenticatorIfUndefined(Authenticator.ALWAYS_VALIDATE);
        }
    }
}
