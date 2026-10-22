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

package org.finos.legend.server.pac4j.mongostore;

import com.google.common.collect.ImmutableMap;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.bson.Document;
import org.finos.legend.server.pac4j.CrossVersionProfileSerializationTest;
import org.finos.legend.server.pac4j.SessionStoreTestUtil;
import org.finos.legend.server.pac4j.sessionutil.SessionToken;
import org.finos.legend.server.pac4j.sessionutil.UuidUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MongoDbSessionStoreLegacyBlobTest
{
    private static final String SESSION_COLLECTION = "legacyBlobSessionData";
    private static final String TOKEN_NAME = "LegendSSOTest";

    private static MongoServer server;
    private static MongoClient client;
    private static MongoDatabase db;

    private MongoCollection<Document> sessions;
    private MongoDbSessionStore store;

    @BeforeClass
    public static void setup()
    {
        server = new MongoServer(new MemoryBackend());
        InetSocketAddress address = server.bind();
        client = MongoClients.create(
                new ConnectionString("mongodb://" + address.getHostName() + ":" + address.getPort()));
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
        db.getCollection(SESSION_COLLECTION).drop();
        sessions = db.getCollection(SESSION_COLLECTION);
        List<String> trustedPackages = new ArrayList<>();
        trustedPackages.add("org.pac4j.core.profile");
        store = new MongoDbSessionStore("AES", 100, sessions,
                ImmutableMap.of(JEEContext.class, new JEESessionStore()), trustedPackages, TOKEN_NAME);
    }

    @Test
    public void legacyPac4j5BlobDoesNotPropagateAndIsEvicted() throws GeneralSecurityException
    {
        assertLegacyBlobHandled("/profiles/commonprofile-pac4j-5.7.1.bin");
    }

    @Test
    public void legacyPac4j4BlobDoesNotPropagateAndIsEvicted() throws GeneralSecurityException
    {
        assertLegacyBlobHandled("/profiles/commonprofile-pac4j-4.5.8.bin");
    }

    @Test
    public void onlyTheUndecodableKeyIsEvicted() throws GeneralSecurityException
    {
        SessionToken token = seed("/profiles/commonprofile-pac4j-5.7.1.bin", Pac4jConstants.USER_PROFILES);
        sessions.updateOne(searchSpec(token), new Document("$set",
                new Document("readableKey", new SessionCrypt("AES").toCryptedString(
                        new org.pac4j.core.util.serializer.JavaSerializer().serializeToBytes("still good"),
                        token))));

        JEEContext context = contextWith(token);
        assertFalse(store.get(context, Pac4jConstants.USER_PROFILES).isPresent());

        Document after = sessions.find(searchSpec(token)).first();
        assertNotNull("the document itself must survive", after);
        assertNull("the undecodable key must be gone", after.getString(Pac4jConstants.USER_PROFILES));
        assertNotNull("the readable key must be untouched", after.getString("readableKey"));
        assertEquals("still good", store.get(contextWith(token), "readableKey").orElse(null));
    }

    @Test
    public void currentFormatBlobIsStillReadAndKept()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext context = new JEEContext(request, response);
        store.set(context, "testKey", "testValue");

        Cookie[] cookies = SessionStoreTestUtil.responseCookies(response);
        MockHttpServletRequest next = new MockHttpServletRequest();
        next.setCookies(cookies);
        JEEContext nextContext = new JEEContext(next, new MockHttpServletResponse());

        assertEquals("testValue", store.get(nextContext, "testKey").orElse(null));
        Document doc = sessions.find(new Document()).first();
        assertNotNull(doc);
        assertNotNull("a readable value must not be evicted", doc.getString("testKey"));
    }

    private void assertLegacyBlobHandled(String fixture) throws GeneralSecurityException
    {
        SessionToken token = seed(fixture, Pac4jConstants.USER_PROFILES);

        Optional<Object> first = store.get(contextWith(token), Pac4jConstants.USER_PROFILES);
        assertFalse("an undecodable legacy blob must read as absent, not as a profile",
                first.isPresent());

        Document after = sessions.find(searchSpec(token)).first();
        assertNotNull(after);
        assertNull("the undecodable entry must be evicted so the failure does not repeat",
                after.getString(Pac4jConstants.USER_PROFILES));

        Optional<Object> second = store.get(contextWith(token), Pac4jConstants.USER_PROFILES);
        assertFalse(second.isPresent());
    }

    private SessionToken seed(String fixture, String key) throws GeneralSecurityException
    {
        SessionToken token = SessionToken.generate();
        byte[] legacy = CrossVersionProfileSerializationTest.readResource(fixture);
        sessions.insertOne(searchSpec(token)
                .append("created", new Date())
                .append(key, new SessionCrypt("AES").toCryptedString(legacy, token)));
        return token;
    }

    private Document searchSpec(SessionToken token)
    {
        return new Document("_id", UuidUtils.toHexString(token.getSessionId()));
    }

    private JEEContext contextWith(SessionToken token)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        token.saveInContext(TOKEN_NAME, new JEEContext(new MockHttpServletRequest(), response), 100);
        request.setCookies(SessionStoreTestUtil.responseCookies(response));
        return new JEEContext(request, new MockHttpServletResponse());
    }
}
