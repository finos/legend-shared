// Copyright 2020 Goldman Sachs
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

package org.finos.legend.server.pac4j.mongostore;

import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.finos.legend.server.pac4j.SessionStoreTestUtil;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.session.J2ESessionStore;

public class MongoDbSessionStoreTest
{

    private static MongoServer server;
    private static MongoClient client;
    private static MongoDatabase db;
    private MongoDbSessionStore store;

    @BeforeClass
    public static void setup()
    {
        server = new MongoServer(new MemoryBackend());
        InetSocketAddress serverAddress = server.bind();

        client = new MongoClient(new ServerAddress(serverAddress));
        db = client.getDatabase("test");
    }

    @AfterClass
    public static void teardown()
    {
        server.shutdown();
        client.close();
    }

    @Before
    public void before()
    {
        List<String> testTrustedPackages = new ArrayList<>();
        testTrustedPackages.add("test.trusted.package");
        store = new MongoDbSessionStore("AES", 100, db.getCollection("sessionData"), ImmutableMap.of(J2EContext.class, new J2ESessionStore()), testTrustedPackages);
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
    public void testTrustedPackageAdded()
    {
        assertTrue(this.store.getSerializationHelper().getTrustedPackages().contains("test.trusted.package"));
    }

    @Test
    public void testSetThenGetFromSession()
    {
        SessionStoreTestUtil.testSetThenGetFromSession(store);
    }

    @Test
    public void testSetThenGetFromMongo()
    {
        SessionStoreTestUtil.testSetThenGetFromStore(store);
    }

    @Test
    public void testSimulateCookieExpiryThenGetFromSession()
    {
        SessionStoreTestUtil.testSimulateCookieExpiryThenGetFromSession(store);
    }
}
