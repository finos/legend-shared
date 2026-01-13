package org.finos.legend.server.pac4j.ping;

import com.google.common.collect.ImmutableMap;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.finos.legend.server.pac4j.mongostore.MongoDbSessionStore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.session.JEESessionStore;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.oidc.profile.OidcProfile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OidcProfileTest
{
    private static MongoServer server;
    private static MongoClient client;
    private static MongoDatabase db;
    private static final String SESSION_COLLECTION = "sessionData";

    @BeforeClass
    public static void setup()
    {
        server = new MongoServer(new MemoryBackend());
        InetSocketAddress serverAddress = server.bind();

        client = MongoClients.create(new ConnectionString("mongodb://"+serverAddress.getHostName() +":"+ serverAddress.getPort()));
        db = client.getDatabase("test");
    }

    @AfterClass
    public static void teardown()
    {
        server.shutdown();
        client.close();
    }

    @Test
    public void testOIDCProfileStorageAndRetrievalInMongoStore()
    {
        // Setup trusted packages for KerberosProfile serialization
        List<String> trustedPackages = new ArrayList<>();
        trustedPackages.add("org.pac4j.core.profile");
        trustedPackages.add("org.finos.legend.server.pac4j.kerberos");

        MongoDbSessionStore kerberosStore = new MongoDbSessionStore(
                "AES",
                3600,
                db.getCollection("OIDCSessionData"),
                ImmutableMap.of(JEEContext.class, new JEESessionStore()),
                trustedPackages,
                "LegendSSOTest"
        );

        OidcProfile oidcProfile = new OidcProfile();
        oidcProfile.setId("testOIDCUser");
        oidcProfile.setAccessToken(new BearerAccessToken("dummy_token"));

        // First call: Store the profile
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(request, response);

        kerberosStore.set(requestContext, Pac4jConstants.USER_PROFILES, oidcProfile);

        // Verify profile was stored in session
        Optional<Object> retrievedFromSession = kerberosStore.get(requestContext, Pac4jConstants.USER_PROFILES);
        assertTrue(retrievedFromSession.isPresent());
        assertEquals(oidcProfile.getId(), ((OidcProfile) retrievedFromSession.get()).getId());

        // Extract SSO cookie
        Cookie[] cookies = response.getCookies();
        assertNotNull(cookies);
        assertTrue(cookies.length > 0);

        // Second call: Simulate new request with SSO cookie
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setCookies(cookies);
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        JEEContext newRequestContext = new JEEContext(newRequest, newResponse);

        // Retrieve profile from MongoDB
        Optional<Object> retrievedFromMongo = kerberosStore.get(newRequestContext, Pac4jConstants.USER_PROFILES);
        assertTrue(retrievedFromMongo.isPresent());
        assertTrue(retrievedFromMongo.get() instanceof OidcProfile);

        // Verify the KerberosProfile data
        OidcProfile retrievedProfile = (OidcProfile) retrievedFromMongo.get();
        assertEquals("testOIDCUser", retrievedProfile.getId());
        assertEquals("dummy_token", retrievedProfile.getAccessToken().getValue());
        assertNotNull(retrievedProfile.getSubject());
        assertFalse(retrievedProfile.isExpired());

    }
  
}