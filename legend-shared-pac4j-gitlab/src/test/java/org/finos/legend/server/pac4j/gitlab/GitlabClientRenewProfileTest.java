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

package org.finos.legend.server.pac4j.gitlab;

import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.oidc.profile.OidcProfile;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GitlabClientRenewProfileTest
{
    private MockWebServer mockServer;
    private String discoveryUrl;

    @Before
    public void setUp() throws IOException
    {
        mockServer = new MockWebServer();
        mockServer.start();
        discoveryUrl = mockServer.url("/.well-known/openid-configuration").toString();
    }

    @After
    public void tearDown() throws IOException
    {
        mockServer.shutdown();
    }

    @Test
    public void testRenewUserProfileOnUninitializedClient_shouldNotThrowConfigurationNull()
    {
        GitlabClient client = new GitlabClient();
        client.setClientId("test-client-id");
        client.setSecret("test-secret");
        client.setDiscoveryUri(discoveryUrl);
        client.setCallbackUrl("http://localhost:8080/callback");

        assertNull("Configuration should be null before init() is called",
                client.getConfiguration());

        mockServer.enqueue(createDiscoveryResponse());
        mockServer.enqueue(createTokenErrorResponse());

        OidcProfile expiredProfile = createExpiredProfile();
        WebContext mockContext = createMockWebContext();

        TechnicalException e = assertThrows(TechnicalException.class,
                () -> client.renewUserProfile(expiredProfile, mockContext));
        assertFalse("init() was not called before accessing getConfiguration()",
                e.getMessage().contains("configuration cannot be null"));
        assertNotNull("Configuration should be non-null after renewUserProfile() triggers init()",
                client.getConfiguration());
    }

    @Test
    public void testRenewUserProfileOnAlreadyInitializedClient_isIdempotent()
    {
        GitlabClient client = new GitlabClient();
        client.setClientId("test-client-id");
        client.setSecret("test-secret");
        client.setDiscoveryUri(discoveryUrl);
        client.setCallbackUrl("http://localhost:8080/callback");

        mockServer.enqueue(createDiscoveryResponse());
        client.init();

        assertNotNull("Configuration should be set after init()", client.getConfiguration());

        mockServer.enqueue(createTokenErrorResponse());

        OidcProfile expiredProfile = createExpiredProfile();
        WebContext mockContext = createMockWebContext();

        TechnicalException e = assertThrows(TechnicalException.class,
                () -> client.renewUserProfile(expiredProfile, mockContext));
        assertFalse("Should not get 'configuration cannot be null' on an initialized client",
                e.getMessage().contains("configuration cannot be null"));
        assertNotNull("Configuration should still be non-null", client.getConfiguration());
    }

    @Test
    public void testRenewUserProfile_invalidGrant_fallsBackToRenewedProfileFromSession()
    {
        GitlabClient client = new GitlabClient();
        client.setClientId("test-client-id");
        client.setSecret("test-secret");
        client.setDiscoveryUri(discoveryUrl);
        client.setCallbackUrl("http://localhost:8080/callback");

        mockServer.enqueue(createDiscoveryResponse());
        client.init();

        mockServer.enqueue(createTokenErrorResponse());

        OidcProfile expiredProfile = createExpiredProfile();

        // Simulate another thread/node having already renewed the profile in the session
        OidcProfile renewedProfile = new OidcProfile();
        renewedProfile.setId("testuser");
        renewedProfile.setClientName(GitlabClient.GITLAB_CLIENT_NAME);
        renewedProfile.setAccessToken(new BearerAccessToken("new-access-token", 7200, null));
        renewedProfile.addAttribute("expiration", Date.from(Instant.now().plusSeconds(7200)));
        renewedProfile.setRefreshToken(new RefreshToken("new-refresh-token"));

        LinkedHashMap<String, UserProfile> sessionProfiles = new LinkedHashMap<>();
        sessionProfiles.put(GitlabClient.GITLAB_CLIENT_NAME, renewedProfile);

        WebContext mockContext = mock(WebContext.class);
        SessionStore sessionStore = mock(SessionStore.class);
        when(mockContext.getSessionStore()).thenReturn(sessionStore);
        when(sessionStore.get(mockContext, "pac4jUserProfiles")).thenReturn(Optional.of(sessionProfiles));

        Optional<UserProfile> result = client.renewUserProfile(expiredProfile, mockContext);

        assertTrue("Should return the renewed profile from session", result.isPresent());
        assertEquals("new-access-token", ((OidcProfile) result.get()).getAccessToken().getValue());
    }

    @Test
    public void testRenewUserProfile_invalidGrant_throwsWhenNoRenewedProfileInSession()
    {
        GitlabClient client = new GitlabClient();
        client.setClientId("test-client-id");
        client.setSecret("test-secret");
        client.setDiscoveryUri(discoveryUrl);
        client.setCallbackUrl("http://localhost:8080/callback");

        mockServer.enqueue(createDiscoveryResponse());
        client.init();

        mockServer.enqueue(createTokenErrorResponse());

        OidcProfile expiredProfile = createExpiredProfile();

        // Session has no renewed profile (empty map)
        LinkedHashMap<String, UserProfile> sessionProfiles = new LinkedHashMap<>();

        WebContext mockContext = mock(WebContext.class);
        SessionStore sessionStore = mock(SessionStore.class);
        when(mockContext.getSessionStore()).thenReturn(sessionStore);
        when(sessionStore.get(mockContext, "pac4jUserProfiles")).thenReturn(Optional.of(sessionProfiles));

        TechnicalException e = assertThrows(TechnicalException.class,
                () -> client.renewUserProfile(expiredProfile, mockContext));
        assertTrue("Should be invalid_grant error",
                e.getMessage().contains("invalid_grant"));
    }

    private OidcProfile createExpiredProfile()
    {
        OidcProfile profile = new OidcProfile();
        profile.setId("testuser");
        profile.setClientName(GitlabClient.GITLAB_CLIENT_NAME);
        BearerAccessToken expiredToken = new BearerAccessToken("expired-access-token", 1, null);
        profile.setAccessToken(expiredToken);
        profile.addAttribute("expiration", Date.from(Instant.now().minusSeconds(3600)));
        profile.setRefreshToken(new RefreshToken("test-refresh-token"));
        return profile;
    }

    private WebContext createMockWebContext()
    {
        WebContext context = mock(WebContext.class);
        SessionStore sessionStore = mock(SessionStore.class);
        when(context.getSessionStore()).thenReturn(sessionStore);
        when(sessionStore.get(any(), anyString())).thenReturn(Optional.empty());
        return context;
    }

    private MockResponse createDiscoveryResponse()
    {
        String tokenEndpoint = mockServer.url("/token").toString();
        String authEndpoint = mockServer.url("/authorize").toString();
        String jwksUri = mockServer.url("/jwks").toString();
        String userInfoEndpoint = mockServer.url("/userinfo").toString();

        String discoveryJson = "{"
                + "\"issuer\": \"" + mockServer.url("/").toString() + "\","
                + "\"authorization_endpoint\": \"" + authEndpoint + "\","
                + "\"token_endpoint\": \"" + tokenEndpoint + "\","
                + "\"userinfo_endpoint\": \"" + userInfoEndpoint + "\","
                + "\"jwks_uri\": \"" + jwksUri + "\","
                + "\"response_types_supported\": [\"code\"],"
                + "\"subject_types_supported\": [\"public\"],"
                + "\"id_token_signing_alg_values_supported\": [\"RS256\"],"
                + "\"token_endpoint_auth_methods_supported\": [\"client_secret_basic\", \"client_secret_post\"]"
                + "}";

        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(discoveryJson);
    }

    private MockResponse createTokenErrorResponse()
    {
        return new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"invalid_grant\", \"error_description\": \"refresh token expired\"}");
    }
}
