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
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.util.CommonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
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
            return callParentPerform(ctx, config, securityGrantedAccessAdapter, clients, authorizers, matchers, parameters, null, null);
        }
        if (nonBrowserCall(context))
        {
            LOGGER.debug("Non-browser call detected, falling back to default handling");
            return callParentPerform(ctx, config, securityGrantedAccessAdapter, clients, CommonHelper.isBlank(authorizers) ? "none" : authorizers, matchers, parameters, null, null);
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
            // Gated as DefaultSecurityLogic gates it: a refused session read must not honour a stored profile.
            List<UserProfile> profiles = loadProfilesFromSession
                    ? manager.getProfiles()
                    : Collections.emptyList();
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
                    boolean saveProfileInSession = getProfileStorageDecision().mustSaveProfileInSession(context, inputClients, (DirectClient) currentClient, profile.get());
                    manager.save(saveProfileInSession, profile.get(), true);
                }
            }
            // profiles is reusable only if it was read from the session; null makes callParentPerform read it.
            return callParentPerform(ctx, config, securityGrantedAccessAdapter, clients, CommonHelper.isBlank(authorizers) ? "none" : authorizers, matchers, parameters,
                    inputClients, loadProfilesFromSession ? profiles : null);
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
     * Clears session profiles from clients outside {@code clients}, then delegates to
     * {@link DefaultSecurityLogic#perform}. A null {@code knownClients} or {@code knownProfiles} is
     * resolved here.
     */
    Object callParentPerform(CallContext ctx, Config config, SecurityGrantedAccessAdapter securityGrantedAccessAdapter, String clients, String authorizers, String matchers, FrameworkParameters parameters,
                             List<Client> knownClients, List<UserProfile> knownProfiles)
    {
        WebContext context = ctx.webContext();
        List<Client> currentClients = knownClients != null
                ? knownClients
                : getClientFinder().find(config.getClients(), context, clients);
        Set<String> requested = currentClients.stream()
                .map(Client::getName)
                .collect(Collectors.toSet());

        ProfileManager manager = ctx.profileManagerFactory().apply(context, ctx.sessionStore());
        manager.setConfig(config);
        List<UserProfile> currentProfiles = knownProfiles != null ? knownProfiles : manager.getProfiles();
        boolean hasStaleProfile = currentProfiles.stream()
                .anyMatch(p -> !requested.contains(p.getClientName()));
        if (hasStaleProfile)
        {
            LOGGER.debug("Session contains profiles outside requested clients {} -> clearing session", requested);
            manager.removeProfiles();
        }
        LOGGER.debug("Calling parent perform method");
        return super.perform(config, securityGrantedAccessAdapter, clients, authorizers, matchers, parameters);
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
