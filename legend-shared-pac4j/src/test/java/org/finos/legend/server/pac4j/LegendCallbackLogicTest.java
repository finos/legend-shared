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
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.factory.ProfileManagerFactory;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LegendCallbackLogicTest
{
    private final List<String> sessionEvents = new ArrayList<>();

    private final JEESessionStore store = new JEESessionStore()
    {
        @Override
        public void set(WebContext context, String key, Object value)
        {
            if (Pac4jConstants.USER_PROFILES.equals(key))
            {
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
    public void theSessionIsRenewedOnceBeforeTheProfileIsSaved()
    {
        saveUserProfile(true);

        assertEquals(ImmutableList.of("renew", "save"), sessionEvents);
    }

    @Test
    public void renewSessionOffIsHonoured()
    {
        saveUserProfile(false);

        assertEquals(ImmutableList.of("save"), sessionEvents);
    }

    private void saveUserProfile(boolean renewSession)
    {
        JEEContext context = new JEEContext(new MockHttpServletRequest(), new MockHttpServletResponse());
        Config config = new Config(new Clients("/callback", ImmutableList.of(TestClient.withoutCredentials("A"))));
        CommonProfile profile = new CommonProfile();
        profile.setId("alice");
        profile.setClientName("A");

        new LegendCallbackLogic(false).saveUserProfile(new CallContext(context, store, ProfileManagerFactory.DEFAULT),
                config, profile, true, false, renewSession);
    }
}
