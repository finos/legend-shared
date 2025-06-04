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
import org.finos.legend.server.pac4j.SessionStoreTestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.session.JEESessionStore;
import org.pac4j.jax.rs.pac4j.JaxRsContext;
import org.pac4j.jax.rs.servlet.pac4j.ServletSessionStore;

public class HazelcastSessionStoreTest {

    private static final String HAZELCAST_CONFIG_FILE_PATH = "src/test/resources/hazelcast.yaml";

    private HazelcastSessionStore store;

    @Before
    public void before()
    {
        store = new HazelcastSessionStore(HAZELCAST_CONFIG_FILE_PATH, ImmutableMap.of(
                JEEContext.class, new JEESessionStore(), JaxRsContext.class, new ServletSessionStore()), "LegendSSOTest");
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
    public void testSetThenGetFromSession()
    {
        SessionStoreTestUtil.testSetThenGetFromSession(store);
    }

    @Test
    public void testSetThenGetFromHazelcast()
    {
        SessionStoreTestUtil.testSetThenGetFromStore(store);
    }

    @Test
    public void testSimulateCookieExpiryThenGetFromSession()
    {
        SessionStoreTestUtil.testSimulateCookieExpiryThenGetFromSession(store);
    }
}
