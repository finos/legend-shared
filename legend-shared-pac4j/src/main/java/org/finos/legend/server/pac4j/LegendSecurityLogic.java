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

package org.finos.legend.server.pac4j;

import org.pac4j.core.client.Client;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.client.IndirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.context.FrameworkParameters;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.engine.DefaultSecurityLogic;
import org.pac4j.core.engine.SecurityGrantedAccessAdapter;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.http.adapter.HttpActionAdapter;
import org.pac4j.core.profile.ProfileHelper;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.profile.factory.ProfileManagerFactory;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.util.Pac4jConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.finos.legend.server.pac4j.LegendRequestHandler.REDIRECT_PROTO_ATTRIBUTE;

public class LegendSecurityLogic extends DefaultSecurityLogic
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendSecurityLogic.class);
    public static final String IS_CONSTRAINED_KERBEROS_FLOW = "IS_CONSTRAINED_KERBEROS_FLOW";

    private LegendUserProfileStorageDecision profileStorageDecision = new LegendUserProfileStorageDecision();

    private boolean multiProfile;

    public LegendUserProfileStorageDecision getProfileStorageDecision()
    {
        return this.profileStorageDecision;
    }

    public void setProfileStorageDecision(LegendUserProfileStorageDecision profileStorageDecision)
    {
        this.profileStorageDecision = profileStorageDecision;
    }

    public boolean isMultiProfile()
    {
        return this.multiProfile;
    }

    /**
     * Selects Legend's browser branch only. The clients keep pac4j's default of {@code false}, so
     * every fall-back to {@link DefaultSecurityLogic} stops at the first direct client that authenticates.
     */
    public void setMultiProfile(boolean multiProfile)
    {
        this.multiProfile = multiProfile;
    }

    @Override
    public Object perform(Config config,
                          SecurityGrantedAccessAdapter securityGrantedAccessAdapter,
                          String clients,
                          String authorizers,
                          String matchers,
                          FrameworkParameters parameters)
    {
        CallContext ctx = buildContext(config, parameters);
        WebContext context = ctx.webContext();
        SessionStore sessionStore = ctx.sessionStore();
        HttpActionAdapter httpActionAdapter = config.getHttpActionAdapter();

        if (!this.multiProfile)
        {
            LOGGER.debug("MultiProfile turned off falling back to default handling");
            return callParentPerform(config, securityGrantedAccessAdapter, clients, authorizers, matchers, parameters);
        }
        if (nonBrowserCall(context))
        {
            LOGGER.debug("Non-browser call detected, falling back to default handling");
            return callParentPerform(config, securityGrantedAccessAdapter, clients, CommonHelper.isBlank(authorizers) ? "none" : authorizers, matchers, parameters);
        }
        LOGGER.debug("Browser call detected, using LegendSecurityLogic handling");
        LOGGER.debug("url: {}", context.getFullRequestURL());
        LOGGER.debug("clients: {}", clients);
        try
        {
            List<Client> inputClients = getClientFinder().find(config.getClients(), context, clients);
            LOGGER.debug("inputClients: {}", inputClients);
            if (!this.getMatchingChecker().matches(ctx, matchers, config.getMatchers(), inputClients))
            {
                LOGGER.debug("no matching for this request -> grant access");
                return securityGrantedAccessAdapter.adapt(context, sessionStore, Collections.emptyList());
            }

            boolean loadProfilesFromSession =
                    getProfileStorageDecision().mustLoadProfilesFromSession(context, inputClients);
            LOGGER.debug("loadProfilesFromSession: {}", loadProfilesFromSession);
            // Not setLoadProfilesFromSession(...): this instance is shared by every request.
            ProfileManager manager = ctx.profileManagerFactory().apply(context, sessionStore);
            manager.setConfig(config);
            List<UserProfile> profiles = readProfiles(context, manager, loadProfilesFromSession);
            LOGGER.debug("existing profiles: {}", profiles);

            for (Client currentClient : inputClients)
            {
                if (isValidProfilePresent(profiles, currentClient))
                {
                    LOGGER.debug("Valid profile found skipping new profile creation for client : {}", currentClient);
                    continue;
                }
                if (currentClient instanceof IndirectClient)
                {
                    return handleIndirectClient(ctx, config, httpActionAdapter, (IndirectClient) currentClient, inputClients);
                }
                Optional<Credentials> credentials = currentClient.getCredentials(ctx)
                        .flatMap(c -> currentClient.validateCredentials(ctx, c));
                LOGGER.debug("credentials: {}", credentials);
                if (!credentials.isPresent())
                {
                    LOGGER.debug("unauthorized");
                    return httpActionAdapter.adapt(this.unauthorized(ctx, inputClients), context);
                }
                Optional<UserProfile> profile = currentClient.getUserProfile(ctx, credentials.get());
                if (profile.isPresent())
                {
                    LOGGER.debug("profile created. Saving profile for client: {}", currentClient);
                    // The client's flag can still veto: an application may turn it off after LegendPac4jBundle set it.
                    boolean saveProfileInSession = getProfileStorageDecision().mustSaveProfileInSession(context, inputClients, (DirectClient) currentClient, profile.get())
                            && Boolean.TRUE.equals(((DirectClient) currentClient).getSaveProfileInSession(context, profile.get()));
                    manager.save(saveProfileInSession, profile.get(), true);
                }
            }
            return callParentPerform(config, securityGrantedAccessAdapter, clients, CommonHelper.isBlank(authorizers) ? "none" : authorizers, matchers, parameters);
        } catch (Exception e)
        {
            LOGGER.error("An error occurred during security logic processing", e);
            return this.handleException(e, httpActionAdapter, context);
        }
    }

    private Object handleIndirectClient(CallContext ctx, Config config, HttpActionAdapter httpActionAdapter, IndirectClient oidcClient, List<Client> currentClients)
    {
        WebContext context = ctx.webContext();
        LOGGER.debug("redirecting to indirect client: {}", oidcClient.getName());
        context.setRequestAttribute(REDIRECT_PROTO_ATTRIBUTE, oidcClient.getUrlResolver().compute(oidcClient.getCallbackUrl(), context).startsWith("https") ? "https" : "http");
        this.saveRequestedUrl(ctx, currentClients, config.getClients().getAjaxRequestResolver());
        HttpAction action = this.redirectToIdentityProvider(ctx, Collections.singletonList(oidcClient));
        return httpActionAdapter.adapt(action, context);
    }

    private boolean isValidProfilePresent(List<UserProfile> profiles, Client client)
    {
        return profiles.stream().anyMatch(userProfile -> userProfile.getClientName().equals(client.getName()) && !userProfile.isExpired());
    }

    /**
     * Saves a direct client's profile only where the storage decision also allows it, decided over the same
     * clients as the session read (see {@link StorageDecisionProfileManager}); pac4j 6 goes by the client's
     * flag alone. A profile manager factory configured on purpose is left alone.
     */
    @Override
    protected CallContext buildContext(Config config, FrameworkParameters parameters)
    {
        CallContext ctx = super.buildContext(config, parameters);
        if (ctx.profileManagerFactory() != ProfileManagerFactory.DEFAULT)
        {
            return ctx;
        }
        return new CallContext(ctx.webContext(), ctx.sessionStore(),
                (context, sessionStore) -> new StorageDecisionProfileManager(context, sessionStore, getProfileStorageDecision()));
    }

    Object callParentPerform(Config config, SecurityGrantedAccessAdapter securityGrantedAccessAdapter, String clients, String authorizers, String matchers, FrameworkParameters parameters)
    {
        LOGGER.debug("Calling parent perform method");
        return super.perform(config, securityGrantedAccessAdapter, clients, authorizers, matchers, parameters);
    }

    /**
     * Applies the storage decision, which {@link DefaultSecurityLogic} does not consult. A profile from a
     * client outside {@code clients} clears them all, forcing re-authentication.
     */
    @Override
    protected List<UserProfile> loadProfiles(CallContext ctx, ProfileManager manager, List<Client> clients)
    {
        WebContext context = ctx.webContext();
        if (manager instanceof StorageDecisionProfileManager)
        {
            ((StorageDecisionProfileManager) manager).setCurrentClients(clients);
        }
        boolean loadProfilesFromSession = getProfileStorageDecision().mustLoadProfilesFromSession(context, clients);
        LOGGER.debug("loadProfilesFromSession: {}", loadProfilesFromSession);
        List<UserProfile> profiles = readProfiles(context, manager, loadProfilesFromSession);

        Set<String> requested = clients.stream()
                .map(Client::getName)
                .collect(Collectors.toSet());
        boolean hasStaleProfile = profiles.stream()
                .anyMatch(p -> !requested.contains(p.getClientName()));
        if (!hasStaleProfile)
        {
            return profiles;
        }
        LOGGER.debug("Profiles outside requested clients {} -> clearing them", requested);
        // A refused session read leaves the session alone: only this request's profiles were looked at.
        if (loadProfilesFromSession)
        {
            manager.removeProfiles();
        }
        else
        {
            context.setRequestAttribute(Pac4jConstants.USER_PROFILES, new LinkedHashMap<String, UserProfile>());
        }
        return Collections.emptyList();
    }

    /**
     * The profiles saved earlier in this request: pac4j 6 has no public equivalent of pac4j 4's
     * {@code getAll(false)}.
     */
    @SuppressWarnings("unchecked")
    private static List<UserProfile> readProfiles(WebContext context, ProfileManager manager, boolean loadProfilesFromSession)
    {
        if (loadProfilesFromSession)
        {
            return manager.getProfiles();
        }
        Optional<Object> requestProfiles = context.getRequestAttribute(Pac4jConstants.USER_PROFILES);
        if (!requestProfiles.isPresent())
        {
            return Collections.emptyList();
        }
        return ProfileHelper.flatIntoAProfileList((Map<String, UserProfile>) requestProfiles.get()).stream()
                .filter(p -> !p.isExpired())
                .collect(Collectors.toList());
    }

    private boolean nonBrowserCall(WebContext context)
    {
        Optional<String> userAgent = context.getRequestHeader("User-Agent");
        if (userAgent.isPresent())
        {
            String agent = userAgent.get().toLowerCase();
            return !agent.contains("mozilla");
        }
        return true;
    }

}
