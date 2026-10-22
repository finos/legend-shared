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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.pac4j.core.authorization.generator.AuthorizationGenerator;
import org.pac4j.core.client.BaseClient;
import org.pac4j.core.client.Client;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.dropwizard.Pac4jMixins;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@code LegendPac4jBundle.setup} runs after dropwizard-pac4j's {@code DefaultFeatureSupport.setup},
 * so the dropwizard mixins are registered first here and the Legend mixin replaces them.
 */
public class ClientMixinTest
{
    public static class TestGenerator implements AuthorizationGenerator
    {
        @Override
        public Optional<UserProfile> generate(CallContext ctx, UserProfile profile)
        {
            return Optional.of(profile);
        }
    }

    private static final String JSON =
            "{\"" + TestClient.class.getName() + "\":{"
                    + "\"name\":\"tc\","
                    + "\"authorizationGenerators\":[{\"class\":\""
                    + TestGenerator.class.getName() + "\"}]"
                    + "}}";

    private static ObjectMapper mapperWithDropwizardPac4jMixins()
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Client.class, Pac4jMixins.ClientMixin.class);
        mapper.addMixIn(BaseClient.class, Pac4jMixins.BaseClientMixin.class);
        mapper.addMixIn(AuthorizationGenerator.class, Pac4jMixins.InstantiateByClassNameMixin.class);
        return mapper;
    }

    @Test
    public void authorizationGeneratorsConfiguredOnAClientStillApply() throws Exception
    {
        ObjectMapper mapper = mapperWithDropwizardPac4jMixins();
        mapper.addMixIn(BaseClient.class, LegendPac4jBundle.IgnoreClientConfigMixin.class);

        Client client = mapper.readValue(JSON, Client.class);

        assertEquals("authorizationGenerators configured on a client must still be applied",
                1, ((BaseClient) client).getAuthorizationGenerators().size());
        assertTrue(((BaseClient) client).getAuthorizationGenerators().get(0) instanceof TestGenerator);
    }

    @Test
    public void withoutAnyMixinTheOverloadedSettersConflict()
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Client.class, Pac4jMixins.ClientMixin.class);
        mapper.addMixIn(AuthorizationGenerator.class, Pac4jMixins.InstantiateByClassNameMixin.class);

        try
        {
            mapper.readValue(JSON, Client.class);
            fail("expected Jackson to reject the overloaded setters");
        }
        catch (Exception expected)
        {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("Conflicting setter definitions"));
        }
    }
}
