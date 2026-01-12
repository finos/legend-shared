package org.finos.legend.server.pac4j;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.client.IndirectClient;
import org.pac4j.core.client.direct.AnonymousClient;
import org.pac4j.core.client.finder.ClientFinder;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.AnonymousCredentials;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.engine.SecurityGrantedAccessAdapter;
import org.pac4j.core.engine.decision.ProfileStorageDecision;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.http.adapter.HttpActionAdapter;
import org.pac4j.core.http.url.DefaultUrlResolver;
import org.pac4j.core.matching.checker.MatchingChecker;
import org.pac4j.core.profile.AnonymousProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.profile.creator.ProfileCreator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.finos.legend.server.pac4j.LegendRequestHandler.REDIRECT_PROTO_ATTRIBUTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LegendSecurityLogicTest
{
    @Mock
    private WebContext webContext;

    @Mock
    private SessionStore sessionStore;

    @Mock
    private Config config;

    @Mock
    private HttpActionAdapter httpActionAdapter;

    private TestableLegendSecurityLogic legendSecurityLogic;

    @Mock
    private MatchingChecker matchingChecker;

    @Mock
    private ProfileStorageDecision profileStorageDecision;

    AutoCloseable closeableMocks;

    @Before
    public void setUp()
    {
        closeableMocks = MockitoAnnotations.openMocks(this);
        legendSecurityLogic = spy(new TestableLegendSecurityLogic<>());
    }

    @After
    public void tearDown() throws Exception {
        closeableMocks.close();
    }

    @Test
    public void testPerform_WithDirectClient_SingleProfile_BrowserCall() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(profileCreator.create(any(Credentials.class), any(WebContext.class)))
                .thenReturn(Optional.of(userProfile));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getAll(anyBoolean())).thenReturn(Collections.emptyList());

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), (Collection<UserProfile>) any(), anyList())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setProfileCreator(profileCreator);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((webContext) -> profileManager);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(),any(),any(),any(),any(),anyString(),anyString(),any(),any());

        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "AnonymousClient",
                "authorizers",
                "matchers",
                true
        );

        // Verify
        verify(profileManager).save(true, userProfile,true);
    }


    @Test
    public void testPerform_WithDirectAndIndirectClient_MultiProfile_BrowserCall() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(profileCreator.create(any(Credentials.class), any(WebContext.class)))
                .thenReturn(Optional.of(userProfile));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getAll(anyBoolean())).thenReturn(Collections.emptyList());

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), (Collection<UserProfile>) any(), anyList())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setProfileCreator(profileCreator);
        IndirectClient indirectClient = mock(IndirectClient.class);
        when(indirectClient.getCallbackUrl()).thenReturn("/callback");
        when(indirectClient.getUrlResolver()).thenReturn(new DefaultUrlResolver());
        when(webContext.getFullRequestURL()).thenReturn("http://localhost/user");
        when(webContext.getRequestAttribute(REDIRECT_PROTO_ATTRIBUTE)).thenReturn(Optional.of("https"));
        when(webContext.getSessionStore()).thenReturn(sessionStore);

        List inputClients = new ArrayList<>();
        inputClients.add(anonymousClient);
        inputClients.add(indirectClient);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(inputClients);

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((webContext) -> profileManager);
        HttpAction mockHttpAction = mock(HttpAction.class);
        legendSecurityLogic.setMockedRedirectResponse(mockHttpAction);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        Clients clients = mock(Clients.class);
        when(config.getClients()).thenReturn(clients);
        when(clients.getAjaxRequestResolver()).thenReturn(null);

        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "client1,client2",
                "authorizers",
                "matchers",
                true
        );

        // Verify
        verify(profileManager,times(1)).save(true, userProfile,true);
        verify(adapter,times(0)).adapt(eq(webContext),any(),any());
        verify(httpActionAdapter,times(1)).adapt(eq(mockHttpAction),eq(webContext));

    }

    @Test
    public void testPerform_WithMultiPleDirectAndIndirectClient_BrowserCall() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(profileCreator.create(any(Credentials.class), any(WebContext.class)))
                .thenReturn(Optional.of(userProfile));


        AnonymousProfile directClient1Profile = new AnonymousProfile();
        directClient1Profile.setClientName("directClient1");
        UserProfile indirectClientProfile = mock(UserProfile.class);
        when(indirectClientProfile.getClientName()).thenReturn("indirectClient");
        when(indirectClientProfile.isExpired()).thenReturn(false);
        List profiles = new ArrayList<>();
        profiles.add(directClient1Profile);
        profiles.add(indirectClientProfile);
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getAll(anyBoolean())).thenReturn(Collections.emptyList()).thenReturn(profiles);

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), (Collection<UserProfile>) any(), anyList())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient directClient1 = new AnonymousClient();
        directClient1.setName("directClient1");
        directClient1.setProfileCreator(profileCreator);
        AnonymousClient directClient2 = new AnonymousClient();
        directClient2.setProfileCreator(profileCreator);
        IndirectClient indirectClient = mock(IndirectClient.class);
        when(indirectClient.getName()).thenReturn("indirectClient");
        when(indirectClient.getCallbackUrl()).thenReturn("/callback");
        when(indirectClient.getUrlResolver()).thenReturn(new DefaultUrlResolver());
        when(webContext.getFullRequestURL()).thenReturn("http://localhost/user");
        when(webContext.getRequestAttribute(REDIRECT_PROTO_ATTRIBUTE)).thenReturn(Optional.of("https"));
        when(webContext.getSessionStore()).thenReturn(sessionStore);

        List inputClients = new ArrayList<>();
        inputClients.add(directClient1);
        inputClients.add(indirectClient);
        inputClients.add(directClient2);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(inputClients);

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((webContext) -> profileManager);
        HttpAction mockHttpAction = mock(HttpAction.class);
        legendSecurityLogic.setMockedRedirectResponse(mockHttpAction);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        Clients clients = mock(Clients.class);
        when(config.getClients()).thenReturn(clients);
        when(clients.getAjaxRequestResolver()).thenReturn(null);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(),any(),any(),any(),any(),any(),anyString(),any(),any());

        //call for first two clients, second client is indirect client so redirect expected
        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "client1,client2,client3",
                "authorizers",
                "matchers",
                true
        );
        //after redirect we expect first two clients to be skipped and third client to be processed
        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "client1,client2,client3",
                "authorizers",
                "matchers",
                true
        );

        // Verify
        verify(profileManager,times(2)).save(true, userProfile,true);
        verify(adapter,times(0)).adapt(eq(webContext),any(),any());
        verify(httpActionAdapter,times(1)).adapt(eq(mockHttpAction),eq(webContext));

    }

    @Test
    public void testPerform_WithDirectClient_MissingCredentials_BrowserCall() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(profileCreator.create(any(Credentials.class), any(WebContext.class)))
                .thenReturn(Optional.of(userProfile));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getAll(anyBoolean())).thenReturn(Collections.emptyList())
                .thenReturn(Collections.singletonList(userProfile));

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), (Collection<UserProfile>) any(), anyList())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        TestClient testClient = spy(new TestClient());
        testClient.setProfileCreator(profileCreator);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(testClient));
        doNothing().when(testClient).getCredentials(webContext);

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((webContext) -> profileManager);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        ArgumentCaptor<HttpAction> httpActionCaptor = ArgumentCaptor.forClass(HttpAction.class);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(),any(),any(),any(),any(),any(),anyString(),any(),any());
        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "AnonymousClient",
                "authorizers",
                "matchers",
                true
        );

        // Verify
        verify(profileManager,never()).save(true, userProfile,true);
        verify(adapter,never()).adapt(eq(webContext),any(),any());
        verify(httpActionAdapter,times(1)).adapt(httpActionCaptor.capture(),eq(webContext));
        HttpAction capturedAction = httpActionCaptor.getValue();
        assertEquals(401, capturedAction.getCode());
    }

    @Test
    public void testPerform_WithDirectClient_SingleProfile_NonBrowserCall() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.empty());
        when(webContext.getRequestMethod()).thenReturn("GET");

        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(profileCreator.create(any(Credentials.class), any(WebContext.class)))
                .thenReturn(Optional.of(userProfile));

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), (Collection<UserProfile>) any(), anyList())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setProfileCreator(profileCreator);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));
        when(config.getClients()).thenReturn(new Clients());
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getAll(anyBoolean())).thenReturn(Collections.singletonList(userProfile));


        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((webContext) -> profileManager);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "AnonymousClient",
                "isAuthenticated",
                "GET",
                true
        );

        // Verify
        verify(legendSecurityLogic).callParentPerform(any(),any(),any(),any(),any(),anyString(),anyString(),eq(false),any());
        verify(profileManager,never()).save(false, userProfile,true);
        verify(adapter).adapt(eq(webContext),any(),any());
        verify(webContext, never()).setRequestAttribute(eq(REDIRECT_PROTO_ATTRIBUTE), any());
    }

    @Test
    public void testPerform_MultiProfile_False() throws Exception
    {
        UserProfile userProfile = mock(UserProfile.class);
        ProfileManager profileManager = mock(ProfileManager.class);
        legendSecurityLogic.setProfileManagerFactory((webContext) -> profileManager);
        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), (Collection<UserProfile>) any(), anyList())).thenReturn(null);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(),any(),any(),any(),any(),anyString(),anyString(),eq(false),any());

        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "AnonymousClient",
                "",
                "matchers",
                false
        );

        // Verify
        verify(profileManager,never()).save(false, userProfile,true);
        verify(adapter,never()).adapt(eq(webContext),any(),any());
        verify(webContext, never()).setRequestAttribute(eq(REDIRECT_PROTO_ATTRIBUTE), any());
    }

    @Test
    public void testPerform_MultiProfile_True_NonBrowserCall() throws Exception
    {
        UserProfile userProfile = mock(UserProfile.class);
        ProfileManager profileManager = mock(ProfileManager.class);
        legendSecurityLogic.setProfileManagerFactory((webContext) -> profileManager);
        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), (Collection<UserProfile>) any(), anyList())).thenReturn(null);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(),any(),any(),any(),any(),anyString(),anyString(),eq(false),any());

        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "AnonymousClient",
                "",
                "matchers",
                true
        );

        // Verify
        verify(profileManager,never()).save(false, userProfile,true);
        verify(adapter,never()).adapt(eq(webContext),any(),any());
        verify(webContext, never()).setRequestAttribute(eq(REDIRECT_PROTO_ATTRIBUTE), any());
    }


    @Test
    public void testPerform_MultiProfile_Null() throws Exception
    {
        UserProfile userProfile = mock(UserProfile.class);
        ProfileManager profileManager = mock(ProfileManager.class);
        legendSecurityLogic.setProfileManagerFactory((webContext) -> profileManager);
        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), (Collection<UserProfile>) any(), anyList())).thenReturn(null);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(),any(),any(),any(),any(),anyString(),anyString(),eq(null),any());

        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "AnonymousClient",
                "",
                "matchers",
                null
        );

        // Verify
        verify(profileManager,never()).save(false, userProfile,true);
        verify(adapter,never()).adapt(eq(webContext),any(),any());
        verify(webContext, never()).setRequestAttribute(eq(REDIRECT_PROTO_ATTRIBUTE), any());
    }

    @Test
    public void testPerform_WithDirectClient_ExpiredProfile() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(userProfile.isExpired()).thenReturn(true);
        when(userProfile.getClientName()).thenReturn("AnonymousClient");
        when(profileCreator.create(any(Credentials.class), any(WebContext.class)))
                .thenReturn(Optional.of(userProfile));
        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), (Collection<UserProfile>) any(), anyList())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setName("AnonymousClient");
        anonymousClient.setProfileCreator(profileCreator);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        ProfileManager profileManager = mock(ProfileManager.class);
        legendSecurityLogic.setProfileManagerFactory((webContext) -> profileManager);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(),any(),any(),any(),any(),any(),anyString(),any(),any());

        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "AnonymousClient",
                "authorizers",
                "matchers",
                true
        );

        // Verify
        verify(profileManager).save(true, userProfile,true);
    }

    @Test
    public void testPerform_WithDirectClientExpiredProfile_ValidIndirectClientProfile() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile oidProfile = mock(UserProfile.class);
        when(oidProfile.isExpired()).thenReturn(false);
        when(oidProfile.getClientName()).thenReturn("oidc");
        when(profileCreator.create(any(Credentials.class), any(WebContext.class)))
                .thenReturn(Optional.of(oidProfile));
        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), (Collection<UserProfile>) any(), anyList())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setName("AnonymousClient");
        anonymousClient.setProfileCreator(profileCreator);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        ProfileManager profileManager = mock(ProfileManager.class);
        legendSecurityLogic.setProfileManagerFactory((webContext) -> profileManager);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(),any(),any(),any(),any(),eq("none"),anyString(),any(),any());

        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "AnonymousClient",
                "",
                "matchers",
                true
        );

        // Verify
        verify(profileManager).save(true, oidProfile,true);
        verify(adapter,never()).adapt(eq(webContext),any(),any());
    }

    @Test
    public void testPerform_WithIndirectClient_ExpiredProfile() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(userProfile.isExpired()).thenReturn(true);
        when(userProfile.getClientName()).thenReturn("oidc");
        when(profileCreator.create(any(Credentials.class), any(WebContext.class)))
                .thenReturn(Optional.of(userProfile));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getAll(anyBoolean())).thenReturn(Collections.singletonList(userProfile));

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), (Collection<UserProfile>) any(), anyList())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient directClient = new AnonymousClient();
        directClient.setProfileCreator(profileCreator);
        directClient.setName("AnnonymousClient");
        IndirectClient indirectClient = mock(IndirectClient.class);
        when(indirectClient.getName()).thenReturn("oidc");
        when(indirectClient.getCallbackUrl()).thenReturn("/callback");
        when(indirectClient.getUrlResolver()).thenReturn(new DefaultUrlResolver());
        when(webContext.getFullRequestURL()).thenReturn("http://localhost/user");
        when(webContext.getRequestAttribute(REDIRECT_PROTO_ATTRIBUTE)).thenReturn(Optional.of("https"));
        when(webContext.getSessionStore()).thenReturn(sessionStore);

        List inputClients = new ArrayList<>();
        inputClients.add(directClient);
        inputClients.add(indirectClient);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(inputClients);

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((webContext) -> profileManager);
        HttpAction mockHttpAction = mock(HttpAction.class);
        legendSecurityLogic.setMockedRedirectResponse(mockHttpAction);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        Clients clients = mock(Clients.class);
        when(config.getClients()).thenReturn(clients);
        when(clients.getAjaxRequestResolver()).thenReturn(null);

        legendSecurityLogic.perform(
                webContext,
                config,
                adapter,
                httpActionAdapter,
                "client1,client2",
                "authorizers",
                "matchers",
                true
        );

        // Verify
        verify(profileManager,times(1)).save(true, userProfile,true);
        verify(adapter,times(0)).adapt(eq(webContext),any(),any());
        verify(httpActionAdapter,times(1)).adapt(eq(mockHttpAction),eq(webContext));

    }

    private static class TestableLegendSecurityLogic<R, C extends WebContext>  extends LegendSecurityLogic<R, C> {
        private HttpAction mockedRedirectResponse;

        public void setMockedRedirectResponse(HttpAction action) {
            this.mockedRedirectResponse = action;
        }

        @Override
        protected HttpAction redirectToIdentityProvider(C context, List<Client<? extends Credentials>> currentClients) {
            return mockedRedirectResponse;
        }
    }

    private static class TestClient extends DirectClient<AnonymousCredentials>
    {
        public static final org.pac4j.core.client.direct.AnonymousClient INSTANCE = new org.pac4j.core.client.direct.AnonymousClient();
        private static boolean warned;

        public TestClient() {
            if (!warned) {
                this.logger.warn("Be careful when using the 'AnonymousClient': an 'AnonymousProfile' is returned and the access is granted for the request.");
                warned = true;
            }

        }

        protected void clientInit() {
            this.defaultCredentialsExtractor((ctx) -> Optional.of(AnonymousCredentials.INSTANCE));
            this.defaultAuthenticator((cred, ctx) -> cred.setUserProfile(AnonymousProfile.INSTANCE));
        }

        protected Optional<AnonymousCredentials> retrieveCredentials(WebContext context) {
            return Optional.empty();
        }
    }
}