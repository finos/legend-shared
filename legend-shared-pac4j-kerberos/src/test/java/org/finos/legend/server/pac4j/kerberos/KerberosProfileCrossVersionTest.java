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

package org.finos.legend.server.pac4j.kerberos;

import com.google.common.collect.ImmutableMap;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.bson.Document;
import org.finos.legend.server.pac4j.mongostore.MongoDbSessionStore;
import org.finos.legend.server.pac4j.sessionutil.SessionToken;
import org.finos.legend.server.pac4j.sessionutil.UuidUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.core.util.serializer.JavaSerializer;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamClass;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The fixture is a real {@link KerberosProfile} serialized by this class compiled against pac4j
 * 5.7.1. pac4j 6 dropped the {@code permissions} slot from the external form, so it cannot be read.
 */
public class KerberosProfileCrossVersionTest
{
    private static final String LEGACY_BLOB = "/profiles/kerberosprofile-pac4j-5.7.1.bin";
    private static final String SESSION_COLLECTION = "kerberosLegacySessionData";
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
        trustedPackages.add("org.finos.legend.server.pac4j.kerberos");
        trustedPackages.add("javax.security.auth");
        store = new MongoDbSessionStore("AES", 3600, sessions,
                ImmutableMap.of(JEEContext.class, new JEESessionStore()), trustedPackages, TOKEN_NAME);
    }

    /**
     * A pac4j 4 node must fail on the class descriptor, which it treats as no session, not in readExternal,
     * which throws a ClassCastException it does not catch.
     */
    @Test
    public void serialVersionUIDIsNotTheOneOlderReleasesUse()
    {
        assertNotEquals(-8691865551996919736L, ObjectStreamClass.lookup(KerberosProfile.class).getSerialVersionUID());
    }

    @Test
    public void legacyKerberosBlobIsUnreadableByTheRawSerializer()
    {
        JavaSerializer serializer = new JavaSerializer();
        serializer.addTrustedPackage("org.finos.legend.server.pac4j.kerberos");
        serializer.addTrustedPackage("javax.security.auth");
        assertNull("JavaSerializer swallows the InvalidClassException",
                serializer.deserializeFromBytes(readResource(LEGACY_BLOB)));
    }

    /**
     * Not evicted: a null from the serializer looks the same as a wrong session key, whose data must survive.
     */
    @Test
    public void legacyKerberosBlobReadsAsAbsentAndIsReplacedAtSignIn() throws GeneralSecurityException
    {
        SessionToken token = seedLegacyBlob();

        assertFalse("a legacy Kerberos session must read as absent, not throw",
                store.get(contextWith(token), Pac4jConstants.USER_PROFILES).isPresent());

        KerberosProfile profile = new KerberosProfile(subjectWithPrincipal(), null);
        profile.setId("alice");
        store.set(contextWith(token), Pac4jConstants.USER_PROFILES, profile);

        Object retrieved = store.get(contextWith(token), Pac4jConstants.USER_PROFILES).orElse(null);
        assertTrue(retrieved instanceof KerberosProfile);
        assertEquals("alice", ((KerberosProfile) retrieved).getId());
    }

    @Test
    public void currentFormatKerberosProfileStillRoundTrips()
    {
        KerberosProfile profile = new KerberosProfile(subjectWithPrincipal(), null);
        profile.setId("alice");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        store.set(new JEEContext(request, response), Pac4jConstants.USER_PROFILES, profile);

        MockHttpServletRequest next = new MockHttpServletRequest();
        next.setCookies(responseCookies(response));
        JEEContext nextContext = new JEEContext(next, new MockHttpServletResponse());

        Object retrieved = store.get(nextContext, Pac4jConstants.USER_PROFILES).orElse(null);
        assertTrue(retrieved instanceof KerberosProfile);
        assertTrue("alice".equals(((KerberosProfile) retrieved).getId()));
    }

    private javax.security.auth.Subject subjectWithPrincipal()
    {
        javax.security.auth.Subject subject = new javax.security.auth.Subject();
        subject.getPrincipals().add(
                new javax.security.auth.kerberos.KerberosPrincipal("alice@REALM"));
        return subject;
    }

    /**
     * Encrypts by hand because {@code SessionCrypt} is package-private to the mongostore package.
     */
    private SessionToken seedLegacyBlob() throws GeneralSecurityException
    {
        SessionToken token = SessionToken.generate();
        byte[] keyBytes = UuidUtils.toByteArray(token.getSessionKey());
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(keyBytes, "AES"));
        String crypted = Base64.getEncoder()
                .encodeToString(cipher.doFinal(readResource(LEGACY_BLOB)));

        sessions.insertOne(searchSpec(token)
                .append("created", new Date())
                .append(Pac4jConstants.USER_PROFILES, crypted));
        return token;
    }

    private Document searchSpec(SessionToken token)
    {
        return new Document("_id", UuidUtils.toHexString(token.getSessionId()));
    }

    private JEEContext contextWith(SessionToken token)
    {
        MockHttpServletResponse probe = new MockHttpServletResponse();
        token.saveInContext(TOKEN_NAME, new JEEContext(new MockHttpServletRequest(), probe), 3600);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(responseCookies(probe));
        return new JEEContext(request, new MockHttpServletResponse());
    }

    /** pac4j writes a raw Set-Cookie header, so MockHttpServletResponse.getCookies() is empty. */
    private static Cookie[] responseCookies(MockHttpServletResponse response)
    {
        List<Cookie> cookies = new ArrayList<>();
        for (String header : response.getHeaders("Set-Cookie"))
        {
            String[] attributes = header.split(";");
            String[] nameAndValue = attributes[0].split("=", 2);
            cookies.add(new Cookie(nameAndValue[0].trim(),
                    nameAndValue.length > 1 ? nameAndValue[1].trim() : ""));
        }
        return cookies.toArray(new Cookie[0]);
    }

    private static byte[] readResource(String path)
    {
        try (InputStream in = KerberosProfileCrossVersionTest.class.getResourceAsStream(path))
        {
            assertNotNull("missing test fixture " + path, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int read = in.read(buffer); read != -1; read = in.read(buffer))
            {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
