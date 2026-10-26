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
import org.junit.Test;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.creator.AuthenticatorProfileCreator;
import org.pac4j.core.profile.factory.ProfileManagerFactory;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LegendSecurityLogicBrowserPathTest
{
    private final JEESessionStore store = new JEESessionStore();

    @Test
    public void aClientThatSaysNotToSaveIsNotSaved()
    {
        JEEContext ctx = browserRequest();

        Object result = perform(ctx, tokenClient(false));

        assertTrue("the request itself must still be granted: " + result, result instanceof Collection);
        assertEquals(Optional.empty(), store.get(ctx, Pac4jConstants.USER_PROFILES));
    }

    @Test
    public void aClientThatSaysToSaveIsSaved()
    {
        JEEContext ctx = browserRequest();

        perform(ctx, tokenClient(true));

        assertTrue(store.get(ctx, Pac4jConstants.USER_PROFILES).isPresent());
    }

    private Object perform(JEEContext ctx, TestClient client)
    {
        List<Client> clients = ImmutableList.of(client);
        Config config = new Config(new Clients("/callback", clients));
        config.setWebContextFactory(p -> ctx);
        config.setSessionStoreFactory(p -> store);
        config.setHttpActionAdapter((action, wc) -> action);
        config.setProfileManagerFactory(ProfileManagerFactory.DEFAULT);
        LegendSecurityLogic logic = new LegendSecurityLogic();
        logic.setMultiProfile(true);
        return logic.perform(config, (wc, ss, profiles) -> profiles, client.getName(), "none", "none", null);
    }

    private static JEEContext browserRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.addHeader("User-Agent", "Mozilla/5.0");
        return new JEEContext(request, new MockHttpServletResponse());
    }

    /** Annotated, so the storage decision alone would save: only the client's flag differs. */
    private static TestClient tokenClient(boolean saveProfileInSession)
    {
        TestClient client = new TestClient("gitlabPAToken");
        client.setCredentialsExtractor(ctx -> Optional.of(new TokenCredentials("glpat-secret")));
        client.setAuthenticator((ctx, credentials) ->
        {
            CommonProfile profile = new CommonProfile();
            profile.setId("alice");
            credentials.setUserProfile(profile);
            return Optional.of(credentials);
        });
        client.setProfileCreator(AuthenticatorProfileCreator.INSTANCE);
        client.setSaveProfileInSession(saveProfileInSession);
        return client;
    }
}
