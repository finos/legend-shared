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

package org.finos.legend.server.pac4j.ping;

import org.junit.Test;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.profile.creator.ProfileCreator;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.profile.creator.OidcProfileCreator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PingDirectClientTest
{
    private static void set(Object target, String name, Object value) throws Exception
    {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static PingDirectClient initialisedClient() throws Exception
    {
        PingDirectClient client = new PingDirectClient();
        set(client, "clientId", "id");
        set(client, "secret", "secret");
        set(client, "discoveryUri", "https://example.invalid/.well-known/openid-configuration");
        set(client, "customParams", new HashMap<String, String>());
        set(client, "scope", "openid profile");
        client.setName("ping");
        client.init();
        return client;
    }

    private static CallContext bearerRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc123");
        WebContext ctx = new JEEContext(request, new MockHttpServletResponse());
        return new CallContext(ctx, new JEESessionStore());
    }

    @Test
    public void bearerTokenSurvivesCredentialValidation() throws Exception
    {
        PingDirectClient client = initialisedClient();
        CallContext callContext = bearerRequest();

        Optional<Credentials> extracted = client.getCredentials(callContext);
        assertTrue("bearer credentials must be extracted", extracted.isPresent());
        assertTrue("a presented bearer token must survive credential validation",
                client.validateCredentials(callContext, extracted.get()).isPresent());
    }

    @Test
    public void validationIsDelegatedToTheOidcProfileCreator() throws Exception
    {
        PingDirectClient client = initialisedClient();

        assertSame("the pass-through authenticator is the only correct one here, "
                        + "because the profile creator performs the actual validation",
                Authenticator.ALWAYS_VALIDATE, client.getAuthenticator());
        ProfileCreator profileCreator = client.getProfileCreator();
        assertTrue("profile creation must go through OidcProfileCreator: " + profileCreator,
                profileCreator instanceof OidcProfileCreator);

        Field configuration = OidcProfileCreator.class.getDeclaredField("configuration");
        configuration.setAccessible(true);
        assertTrue("OidcProfileCreator must still call the userinfo endpoint -- that call is what "
                        + "validates the bearer token now that UserInfoOidcAuthenticator is gone",
                ((OidcConfiguration) configuration.get(profileCreator)).isCallUserInfoEndpoint());
    }

    @Test
    public void reinitialisationReusesTheSameProfileCreator() throws Exception
    {
        PingDirectClient client = initialisedClient();
        ProfileCreator first = client.getProfileCreator();
        assertNotNull(first);

        client.reinit();

        assertSame("reinit() must not rebuild the OIDC configuration and profile creator",
                first, client.getProfileCreator());
    }
}
