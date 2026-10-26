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
import org.pac4j.core.context.CallContext;
import org.pac4j.core.context.FrameworkParameters;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.factory.ProfileManagerFactory;
import org.pac4j.core.credentials.AnonymousCredentials;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.engine.SecurityGrantedAccessAdapter;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.http.adapter.HttpActionAdapter;
import org.pac4j.core.http.url.DefaultUrlResolver;
import org.pac4j.core.matching.checker.MatchingChecker;
import org.pac4j.core.profile.AnonymousProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.profile.creator.ProfileCreator;
import org.pac4j.core.util.Pac4jConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.finos.legend.server.pac4j.LegendRequestHandler.REDIRECT_PROTO_ATTRIBUTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Clients expected to save are given {@code setSaveProfileInSession(true)}, as {@link LegendPac4jBundle} does:
 * the browser branch needs both the storage decision and the client's flag.
 */
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
    private LegendUserProfileStorageDecision profileStorageDecision;

    AutoCloseable closeableMocks;

    @Before
    public void setUp()
    {
        closeableMocks = MockitoAnnotations.openMocks(this);
        lenient().when(config.getHttpActionAdapter()).thenReturn(httpActionAdapter);
        legendSecurityLogic = spy(new TestableLegendSecurityLogic());
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
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(userProfile));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.emptyList());

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setProfileCreator(profileCreator);
        anonymousClient.setSaveProfileInSession(true);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(), any(), any(), eq("none"), anyString(), any());

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(
                config,
                adapter,
                "AnonymousClient", "", "matchers", null
        );

        // Verify
        verify(profileManager).save(true, userProfile,true);
    }


    @Test
    public void testPerform_DoesNotSetDeadRequestAttribute_NorMutateSharedState() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(userProfile));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.emptyList());

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setProfileCreator(profileCreator);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);
        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);
        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustLoadProfilesFromSession(any(), anyList())).thenReturn(false);
        when(profileStorageDecision.mustSaveProfileInSession(any(), anyList(), any(), any())).thenReturn(true);
        doReturn(mock(Object.class)).when(legendSecurityLogic)
                .callParentPerform(any(), any(), any(), eq("none"), anyString(), any());

        boolean loadProfilesBefore = legendSecurityLogic.isLoadProfilesFromSession();
        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(config, adapter, "AnonymousClient", "", "matchers", null);

        verify(webContext, never()).setRequestAttribute(eq("pac4jLoadProfilesFromSession"), any());
        assertEquals("perform() must not mutate state shared across requests",
                loadProfilesBefore, legendSecurityLogic.isLoadProfilesFromSession());
    }

    @Test
    public void testPerform_StorageDecisionGatesTheSessionRead() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(userProfile));

        // Lenient: only reached if the gate fails, and then this valid profile skips the client so
        // nothing is saved.
        UserProfile sessionProfile = mock(UserProfile.class);
        lenient().when(sessionProfile.getClientName()).thenReturn("AnonymousClient");
        lenient().when(sessionProfile.isExpired()).thenReturn(false);
        ProfileManager profileManager = mock(ProfileManager.class);
        lenient().when(profileManager.getProfiles())
                .thenReturn(Collections.singletonList(sessionProfile));

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setName("AnonymousClient");
        anonymousClient.setProfileCreator(profileCreator);
        anonymousClient.setSaveProfileInSession(true);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);
        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);
        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustLoadProfilesFromSession(any(), anyList())).thenReturn(false);
        when(profileStorageDecision.mustSaveProfileInSession(any(), anyList(), any(), any())).thenReturn(true);
        doReturn(mock(Object.class)).when(legendSecurityLogic)
                .callParentPerform(any(), any(), any(), eq("none"), anyString(), any());

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(config, adapter, "AnonymousClient", "", "matchers", null);

        verify(profileManager, never()).getProfiles();
        verify(profileManager).save(true, userProfile, true);
    }

    @Test
    public void testPerform_StorageDecisionAllowsTheSessionRead() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        lenient().when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(userProfile));

        UserProfile sessionProfile = mock(UserProfile.class);
        when(sessionProfile.getClientName()).thenReturn("AnonymousClient");
        when(sessionProfile.isExpired()).thenReturn(false);
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.singletonList(sessionProfile));

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setName("AnonymousClient");
        anonymousClient.setProfileCreator(profileCreator);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);
        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);
        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustLoadProfilesFromSession(any(), anyList())).thenReturn(true);
        doReturn(mock(Object.class)).when(legendSecurityLogic)
                .callParentPerform(any(), any(), any(), eq("none"), anyString(), any());

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(config, adapter, "AnonymousClient", "", "matchers", null);

        verify(profileManager).getProfiles();
        verify(profileManager, never()).save(anyBoolean(), any(), anyBoolean());
    }

    @Test
    public void testPerform_ResolvesClientsAndReadsProfilesOncePerRequest() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(userProfile));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.emptyList());

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setName("AnonymousClient");
        anonymousClient.setProfileCreator(profileCreator);
        when(clientFinder.find(any(), any(), anyString()))
                .thenReturn(Collections.singletonList(anonymousClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);
        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);
        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustLoadProfilesFromSession(any(), anyList())).thenReturn(true);
        when(profileStorageDecision.mustSaveProfileInSession(any(), anyList(), any(), any())).thenReturn(true);

        doReturn(mock(Object.class)).when(legendSecurityLogic)
                .callParentPerform(any(), any(), any(), eq("none"), anyString(), any());

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(config, adapter, "AnonymousClient", "", "matchers", null);

        verify(clientFinder, times(1)).find(any(), any(), anyString());
        verify(profileManager, times(1)).getProfiles();
        verify(legendSecurityLogic).callParentPerform(any(), any(), any(), eq("none"), anyString(), any());
    }

    @Test
    public void testPerform_IndirectClientRedirectDoesNotReplaceTheSavedRequestHandler() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.emptyList());

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);

        IndirectClient indirectClient = mock(IndirectClient.class);
        when(indirectClient.getCallbackUrl()).thenReturn("/callback");
        when(indirectClient.getUrlResolver()).thenReturn(new DefaultUrlResolver());
        when(webContext.getFullRequestURL()).thenReturn("http://localhost/user");

        ClientFinder clientFinder = mock(ClientFinder.class);
        when(clientFinder.find(any(), any(), anyString()))
                .thenReturn(Collections.singletonList((Client) indirectClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);
        legendSecurityLogic.setMockedRedirectResponse(mock(HttpAction.class));
        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);
        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustLoadProfilesFromSession(any(), anyList())).thenReturn(true);

        Clients clients = mock(Clients.class);
        when(config.getClients()).thenReturn(clients);
        when(clients.getAjaxRequestResolver()).thenReturn(null);

        LegendRequestHandler installed = new LegendRequestHandler();
        legendSecurityLogic.setSavedRequestHandler(installed);

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(config, adapter, "oidc", "authorizers", "matchers", null);

        assertSame("perform() must not replace the application-wide saved request handler",
                installed, legendSecurityLogic.getSavedRequestHandler());
    }

    @Test
    public void testPerform_WithDirectAndIndirectClient_MultiProfile_BrowserCall() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(userProfile));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.emptyList());

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setProfileCreator(profileCreator);
        anonymousClient.setSaveProfileInSession(true);
        IndirectClient indirectClient = mock(IndirectClient.class);
        when(indirectClient.getCallbackUrl()).thenReturn("/callback");
        when(indirectClient.getUrlResolver()).thenReturn(new DefaultUrlResolver());
        when(webContext.getFullRequestURL()).thenReturn("http://localhost/user");
        when(webContext.getRequestAttribute(REDIRECT_PROTO_ATTRIBUTE)).thenReturn(Optional.of("https"));

        List inputClients = new ArrayList<>();
        inputClients.add(anonymousClient);
        inputClients.add(indirectClient);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(inputClients);

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);
        HttpAction mockHttpAction = mock(HttpAction.class);
        legendSecurityLogic.setMockedRedirectResponse(mockHttpAction);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        Clients clients = mock(Clients.class);
        when(config.getClients()).thenReturn(clients);
        when(clients.getAjaxRequestResolver()).thenReturn(null);

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(
                config,
                adapter,
                "client1, client2", "authorizers", "matchers", null
        );

        // Verify
        verify(profileManager,times(1)).save(true, userProfile,true);
        verify(adapter,times(0)).adapt(eq(webContext),any(),any());
        verify(httpActionAdapter,times(1)).adapt(eq(mockHttpAction),eq(webContext));

    }


    @Test
    public void testPerform_MultiProfile_BrowserCall_WithoutConstrainedKerberosFlowAttribute() throws Exception
    {
        // Verifies that multi-profile support works for browser calls even when
        // IS_CONSTRAINED_KERBEROS_FLOW attribute is not set (removed gate)
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(userProfile));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.emptyList());

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setProfileCreator(profileCreator);
        anonymousClient.setSaveProfileInSession(true);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(), any(), any(), eq("none"), anyString(), any());

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(
                config,
                adapter,
                "AnonymousClient", "", "matchers", null
        );

        // Verify that profile is saved via multi-profile logic (not falling back to default)
        verify(profileManager).save(true, userProfile, true);
    }

    @Test
    public void testPerform_WithMultiPleDirectAndIndirectClient_BrowserCall() throws Exception
    {
        when(webContext.getRequestHeader(eq("User-Agent"))).thenReturn(Optional.of("Mozilla/5.0"));
        ProfileCreator profileCreator = mock(ProfileCreator.class);
        UserProfile userProfile = mock(UserProfile.class);
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
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
        when(profileManager.getProfiles()).thenReturn(Collections.emptyList()).thenReturn(profiles);

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient directClient1 = new AnonymousClient();
        directClient1.setName("directClient1");
        directClient1.setProfileCreator(profileCreator);
        directClient1.setSaveProfileInSession(true);
        AnonymousClient directClient2 = new AnonymousClient();
        directClient2.setProfileCreator(profileCreator);
        directClient2.setSaveProfileInSession(true);
        IndirectClient indirectClient = mock(IndirectClient.class);
        when(indirectClient.getName()).thenReturn("indirectClient");
        when(indirectClient.getCallbackUrl()).thenReturn("/callback");
        when(indirectClient.getUrlResolver()).thenReturn(new DefaultUrlResolver());
        when(webContext.getFullRequestURL()).thenReturn("http://localhost/user");
        when(webContext.getRequestAttribute(REDIRECT_PROTO_ATTRIBUTE)).thenReturn(Optional.of("https"));

        List inputClients = new ArrayList<>();
        inputClients.add(directClient1);
        inputClients.add(indirectClient);
        inputClients.add(directClient2);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(inputClients);

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);
        HttpAction mockHttpAction = mock(HttpAction.class);
        legendSecurityLogic.setMockedRedirectResponse(mockHttpAction);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);
        // The second perform() must find the first one's profiles in the session; left unstubbed,
        // Mockito's default of false would skip the session read.
        when(profileStorageDecision.mustLoadProfilesFromSession(any(), anyList())).thenReturn(true);

        Clients clients = mock(Clients.class);
        when(config.getClients()).thenReturn(clients);
        when(clients.getAjaxRequestResolver()).thenReturn(null);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(), any(), any(), any(), anyString(), any());

        //call for first two clients, second client is indirect client so redirect expected
        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(
                config,
                adapter,
                "client1, client2, client3", "authorizers", "matchers", null
        );
        //after redirect we expect first two clients to be skipped and third client to be processed
        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(
                config,
                adapter,
                "client1, client2, client3", "authorizers", "matchers", null
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
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(userProfile));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.emptyList())
                .thenReturn(Collections.singletonList(userProfile));

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        TestClient testClient = spy(new TestClient());
        testClient.setProfileCreator(profileCreator);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(testClient));
        doReturn(Optional.empty()).when(testClient).getCredentials(any(CallContext.class));

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        ArgumentCaptor<HttpAction> httpActionCaptor = ArgumentCaptor.forClass(HttpAction.class);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(), any(), any(), any(), anyString(), any());
        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(
                config,
                adapter,
                "AnonymousClient", "authorizers", "matchers", null
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
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(userProfile));

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setProfileCreator(profileCreator);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));
        when(config.getClients()).thenReturn(new Clients());
        when(userProfile.getClientName()).thenReturn(anonymousClient.getName());
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.singletonList(userProfile));


        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustLoadProfilesFromSession(any(), anyList())).thenReturn(true);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(
                config,
                adapter,
                "AnonymousClient", "isAuthenticated", "GET", null
        );

        // Verify
        verify(legendSecurityLogic).callParentPerform(any(), any(), any(), anyString(), anyString(), any());
        verify(profileManager,never()).save(false, userProfile,true);
        verify(adapter).adapt(eq(webContext), eq(sessionStore), any());
        verify(webContext, never()).setRequestAttribute(eq(REDIRECT_PROTO_ATTRIBUTE), any());
    }

    @Test
    public void testPerform_MultiProfile_False() throws Exception
    {
        UserProfile userProfile = mock(UserProfile.class);
        ProfileManager profileManager = mock(ProfileManager.class);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);
        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(), any(), any(), anyString(), anyString(), any());

        legendSecurityLogic.setMultiProfile(false);
        legendSecurityLogic.perform(
                config,
                adapter,
                "AnonymousClient", "", "matchers", null
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
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);
        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(), any(), any(), anyString(), anyString(), any());

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(
                config,
                adapter,
                "AnonymousClient", "", "matchers", null
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
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);
        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);
        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(), any(), any(), anyString(), anyString(), any());

        legendSecurityLogic.setMultiProfile(false);
        legendSecurityLogic.perform(
                config,
                adapter,
                "AnonymousClient", "", "matchers", null
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
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(userProfile));
        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setName("AnonymousClient");
        anonymousClient.setProfileCreator(profileCreator);
        anonymousClient.setSaveProfileInSession(true);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        ProfileManager profileManager = mock(ProfileManager.class);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(), any(), any(), any(), anyString(), any());

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(
                config,
                adapter,
                "AnonymousClient", "authorizers", "matchers", null
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
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(oidProfile));
        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient anonymousClient = new AnonymousClient();
        anonymousClient.setName("AnonymousClient");
        anonymousClient.setProfileCreator(profileCreator);
        anonymousClient.setSaveProfileInSession(true);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(Collections.singletonList(anonymousClient));

        legendSecurityLogic.setClientFinder(clientFinder);
        ProfileManager profileManager = mock(ProfileManager.class);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        doReturn(mock(Object.class)).when(legendSecurityLogic).callParentPerform(any(), any(), any(), eq("none"), anyString(), any());

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(
                config,
                adapter,
                "AnonymousClient", "", "matchers", null
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
        when(profileCreator.create(any(CallContext.class), any(Credentials.class)))
                .thenReturn(Optional.of(userProfile));

        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.singletonList(userProfile));

        SecurityGrantedAccessAdapter adapter = mock(SecurityGrantedAccessAdapter.class);
        when(adapter.adapt(any(), any(), (Collection<UserProfile>) any())).thenReturn(null);

        ClientFinder clientFinder = mock(ClientFinder.class);
        AnonymousClient directClient = new AnonymousClient();
        directClient.setProfileCreator(profileCreator);
        directClient.setSaveProfileInSession(true);
        directClient.setName("AnnonymousClient");
        IndirectClient indirectClient = mock(IndirectClient.class);
        when(indirectClient.getName()).thenReturn("oidc");
        when(indirectClient.getCallbackUrl()).thenReturn("/callback");
        when(indirectClient.getUrlResolver()).thenReturn(new DefaultUrlResolver());
        when(webContext.getFullRequestURL()).thenReturn("http://localhost/user");
        when(webContext.getRequestAttribute(REDIRECT_PROTO_ATTRIBUTE)).thenReturn(Optional.of("https"));

        List inputClients = new ArrayList<>();
        inputClients.add(directClient);
        inputClients.add(indirectClient);
        when(clientFinder.find(any(), any(), anyString())).thenReturn(inputClients);

        legendSecurityLogic.setClientFinder(clientFinder);
        legendSecurityLogic.setProfileManagerFactory((c, ss) -> profileManager);
        HttpAction mockHttpAction = mock(HttpAction.class);
        legendSecurityLogic.setMockedRedirectResponse(mockHttpAction);

        when(legendSecurityLogic.getMatchingChecker()).thenReturn(matchingChecker);
        when(matchingChecker.matches(any(), anyString(), any(), anyList())).thenReturn(true);

        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustSaveProfileInSession(any(),anyList(),any(),any())).thenReturn(true);

        Clients clients = mock(Clients.class);
        when(config.getClients()).thenReturn(clients);
        when(clients.getAjaxRequestResolver()).thenReturn(null);

        legendSecurityLogic.setMultiProfile(true);
        legendSecurityLogic.perform(
                config,
                adapter,
                "client1, client2", "authorizers", "matchers", null
        );

        // Verify
        verify(profileManager,times(1)).save(true, userProfile,true);
        verify(adapter,times(0)).adapt(eq(webContext),any(),any());
        verify(httpActionAdapter,times(1)).adapt(eq(mockHttpAction),eq(webContext));

    }

    @Test
    public void testLoadProfiles_StaleSessionProfile_ClearsSession() throws Exception
    {
        UserProfile staleProfile = mock(UserProfile.class);
        when(staleProfile.getClientName()).thenReturn("clientA");
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.singletonList(staleProfile));

        List<UserProfile> loaded = loadProfiles(profileManager, true, "clientB");

        assertTrue(loaded.isEmpty());
        verifyCleared(profileManager, true);
    }

    @Test
    public void testLoadProfiles_MatchingSessionProfile_KeepsSession() throws Exception
    {
        UserProfile matchingProfile = mock(UserProfile.class);
        when(matchingProfile.getClientName()).thenReturn("clientB");
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.singletonList(matchingProfile));

        List<UserProfile> loaded = loadProfiles(profileManager, true, "clientB");

        assertEquals(Collections.singletonList(matchingProfile), loaded);
        verifyNotCleared(profileManager);
    }

    @Test
    public void testLoadProfiles_EmptySession_NoRemoval() throws Exception
    {
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(Collections.emptyList());

        assertTrue(loadProfiles(profileManager, true, "clientB").isEmpty());
        verifyNotCleared(profileManager);
    }

    @Test
    public void testLoadProfiles_MixedProfiles_AnyStaleClearsWholeSession() throws Exception
    {
        UserProfile validProfile = mock(UserProfile.class);
        when(validProfile.getClientName()).thenReturn("clientB");
        UserProfile staleProfile = mock(UserProfile.class);
        when(staleProfile.getClientName()).thenReturn("clientX");
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.getProfiles()).thenReturn(java.util.Arrays.asList(validProfile, staleProfile));

        assertTrue(loadProfiles(profileManager, true, "clientB", "clientC").isEmpty());
        verifyCleared(profileManager, true);
    }

    @Test
    public void testLoadProfiles_RefusedSessionRead_SeesOnlyThisRequestsProfiles() throws Exception
    {
        UserProfile requestProfile = mock(UserProfile.class);
        when(requestProfile.getClientName()).thenReturn("clientB");
        LinkedHashMap<String, UserProfile> requestProfiles = new LinkedHashMap<>();
        requestProfiles.put("clientB", requestProfile);
        when(webContext.getRequestAttribute(Pac4jConstants.USER_PROFILES)).thenReturn(Optional.of(requestProfiles));
        ProfileManager profileManager = mock(ProfileManager.class);

        List<UserProfile> loaded = loadProfiles(profileManager, false, "clientB");

        assertEquals(Collections.singletonList(requestProfile), loaded);
        verify(profileManager, never()).getProfiles();
        verifyNotCleared(profileManager);
    }

    @Test
    public void testLoadProfiles_RefusedSessionRead_StaleRequestProfileLeavesTheSessionAlone() throws Exception
    {
        UserProfile staleProfile = mock(UserProfile.class);
        when(staleProfile.getClientName()).thenReturn("clientA");
        LinkedHashMap<String, UserProfile> requestProfiles = new LinkedHashMap<>();
        requestProfiles.put("clientA", staleProfile);
        when(webContext.getRequestAttribute(Pac4jConstants.USER_PROFILES)).thenReturn(Optional.of(requestProfiles));
        ProfileManager profileManager = mock(ProfileManager.class);

        assertTrue(loadProfiles(profileManager, false, "clientB").isEmpty());
        verify(profileManager, never()).getProfiles();
        verifyCleared(profileManager, false);
    }

    private List<UserProfile> loadProfiles(ProfileManager profileManager, boolean loadFromSession, String... clientNames)
    {
        List<Client> clients = new ArrayList<>();
        for (String name : clientNames)
        {
            IndirectClient client = mock(IndirectClient.class);
            when(client.getName()).thenReturn(name);
            clients.add(client);
        }
        when(legendSecurityLogic.getProfileStorageDecision()).thenReturn(profileStorageDecision);
        when(profileStorageDecision.mustLoadProfilesFromSession(any(), anyList())).thenReturn(loadFromSession);
        return legendSecurityLogic.loadProfiles(legendSecurityLogic.buildContext(config, null), profileManager, clients);
    }

    private void verifyCleared(ProfileManager profileManager, boolean fromSession)
    {
        verify(profileManager, times(fromSession ? 1 : 0)).removeProfiles();
        verify(webContext, times(fromSession ? 0 : 1)).setRequestAttribute(Pac4jConstants.USER_PROFILES, new LinkedHashMap<>());
        verify(sessionStore, never()).set(any(), eq(Pac4jConstants.USER_PROFILES), any());
    }

    private void verifyNotCleared(ProfileManager profileManager)
    {
        verify(profileManager, never()).removeProfiles();
        verify(sessionStore, never()).set(any(), eq(Pac4jConstants.USER_PROFILES), any());
        verify(webContext, never()).setRequestAttribute(eq(Pac4jConstants.USER_PROFILES), any());
    }

    private class TestableLegendSecurityLogic extends LegendSecurityLogic {
        private HttpAction mockedRedirectResponse;
        private ProfileManagerFactory profileManagerFactory = ProfileManagerFactory.DEFAULT;

        public void setMockedRedirectResponse(HttpAction action) {
            this.mockedRedirectResponse = action;
        }

        public void setProfileManagerFactory(ProfileManagerFactory factory) {
            this.profileManagerFactory = factory;
        }

        @Override
        public CallContext buildContext(Config config, FrameworkParameters parameters) {
            return new CallContext(webContext, sessionStore, profileManagerFactory);
        }

        @Override
        protected HttpAction redirectToIdentityProvider(CallContext ctx, List<Client> currentClients) {
            return mockedRedirectResponse;
        }
    }

    private static class TestClient extends DirectClient
    {
        public static final org.pac4j.core.client.direct.AnonymousClient INSTANCE = new org.pac4j.core.client.direct.AnonymousClient();
        private static boolean warned;

        public TestClient() {
            if (!warned) {
                this.logger.warn("Be careful when using the 'AnonymousClient': an 'AnonymousProfile' is returned and the access is granted for the request.");
                warned = true;
            }

        }

        @Override
        protected void internalInit(boolean forceReinit) {
            this.setCredentialsExtractorIfUndefined(ctx -> Optional.of(AnonymousCredentials.INSTANCE));
            this.setAuthenticatorIfUndefined((ctx, cred) ->
            {
                cred.setUserProfile(AnonymousProfile.INSTANCE);
                return Optional.of(cred);
            });
        }
    }
}
