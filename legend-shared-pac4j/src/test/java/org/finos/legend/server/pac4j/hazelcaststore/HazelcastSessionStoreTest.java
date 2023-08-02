package org.finos.legend.server.pac4j.hazelcaststore;

import com.google.common.collect.ImmutableMap;
import com.hazelcast.core.Hazelcast;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.session.J2ESessionStore;
import org.pac4j.jax.rs.pac4j.JaxRsContext;
import org.pac4j.jax.rs.servlet.pac4j.ServletSessionStore;

import static org.junit.Assert.assertTrue;

public class HazelcastSessionStoreTest {

    // TODO - figure out file path here
    private static final String HAZELCAST_CONFIG_FILE_PATH = "hazelcast.yaml";

    private HazelcastSessionStore store;

    @Before
    public void before()
    {
        store = new HazelcastSessionStore(HAZELCAST_CONFIG_FILE_PATH, ImmutableMap.of(
                J2EContext.class, new J2ESessionStore(), JaxRsContext.class, new ServletSessionStore()));
    }

    @After
    public void after()
    {
        Hazelcast.shutdownAll();
    }

    // TODO -- fill in method
    @Test
    public void testSetCreatesCookie()
    {
        assertTrue(true);
    }

}
