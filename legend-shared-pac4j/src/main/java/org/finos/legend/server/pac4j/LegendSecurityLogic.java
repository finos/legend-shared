package org.finos.legend.server.pac4j;

import org.pac4j.core.client.Client;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.client.IndirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.WebContext;
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

import static org.finos.legend.server.pac4j.LegendRequestHandler.REDIRECT_PROTO_ATTRIBUTE;

public class LegendSecurityLogic<R, C extends WebContext> extends DefaultSecurityLogic<R, C>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendSecurityLogic.class);

    @Override
    public R perform(C context,
                     Config config,
                     SecurityGrantedAccessAdapter<R, C> securityGrantedAccessAdapter,
                     HttpActionAdapter<R, C> httpActionAdapter,
                     String clients,
                     String authorizers,
                     String matchers,
                     Boolean inputMultiProfile,
                     Object... parameters)
    {
        boolean multiProfile = inputMultiProfile != null && inputMultiProfile;
        if (!multiProfile)
        {
            LOGGER.info("MultiProfile turned off falling back to default handling");
            return callParentPerform(context, config, securityGrantedAccessAdapter, httpActionAdapter, clients, authorizers, matchers, inputMultiProfile, parameters);
        }
        if (nonBrowserCall(context))
        {
            LOGGER.info("Non-browser call detected, falling back to default handling");
            return callParentPerform(context, config, securityGrantedAccessAdapter, httpActionAdapter, clients, authorizers, matchers, false, parameters);
        }
        LOGGER.info("Browser call detected, using LegendSecurityLogic handling");
        LOGGER.debug("url: {}", context.getFullRequestURL());
        LOGGER.debug("clients: {}", clients);
        try
        {
            List<Client<? extends Credentials>> inputClients = getClientFinder().find(config.getClients(), context, clients);
            LOGGER.debug("inputClients: {}", inputClients);
            if (!this.getMatchingChecker().matches(context, matchers, config.getMatchers(), inputClients))
            {
                LOGGER.debug("no matching for this request -> grant access");
                return securityGrantedAccessAdapter.adapt(context, Collections.emptyList(), parameters);
            }

            boolean loadProfilesFromSession = getProfileStorageDecision().mustLoadProfilesFromSession(context, inputClients);
            LOGGER.debug("loadProfilesFromSession: {}", loadProfilesFromSession);
            context.setRequestAttribute("pac4jLoadProfilesFromSession", loadProfilesFromSession);
            ProfileManager<UserProfile> manager = this.getProfileManager(context);
            manager.setConfig(config);
            List<UserProfile> profiles = manager.getAll(loadProfilesFromSession);
            LOGGER.debug("existing profiles: {}", profiles);

            for (Client currentClient : inputClients)
            {
                if (isValidProfilePresent(profiles,currentClient))
                {
                    LOGGER.debug("Valid profile found skipping new profile creation for client : {}", currentClient);
                    continue;
                }
                if (currentClient instanceof IndirectClient)
                {
                    return handleIndirectClient(context, config, httpActionAdapter,(IndirectClient<? extends Credentials>) currentClient, inputClients);
                }
                Optional<Credentials> credentials = currentClient.getCredentials(context);
                LOGGER.debug("credentials: {}", credentials);
                if (!credentials.isPresent())
                {
                    LOGGER.debug("unauthorized");
                    return httpActionAdapter.adapt(this.unauthorized(context, inputClients), context);
                }
                Optional<UserProfile> profile = currentClient.getUserProfile(credentials.get(), context);
                if (profile.isPresent())
                {
                    LOGGER.debug("profile created. Saving profile for client: {}", currentClient);
                    boolean saveProfileInSession = getProfileStorageDecision().mustSaveProfileInSession(context, inputClients, (DirectClient)currentClient, profile.get());
                    manager.save(saveProfileInSession, profile.get(), multiProfile);
                }
            }
            return callParentPerform(context, config, securityGrantedAccessAdapter, httpActionAdapter, clients, CommonHelper.isBlank(authorizers) ? "none" : authorizers, matchers, inputMultiProfile, parameters);
        } catch (Exception e)
        {
            LOGGER.error("An error occurred during security logic processing", e);
            return this.handleException(e, httpActionAdapter, context);
        }
    }

    private R handleIndirectClient(C context, Config config, HttpActionAdapter<R, C> httpActionAdapter, IndirectClient<? extends Credentials> oidcClient, List<Client<? extends Credentials>> currentClients)
    {
        LOGGER.debug("redirecting to indirect client: {}", oidcClient.getName());
        context.setRequestAttribute(REDIRECT_PROTO_ATTRIBUTE,oidcClient.getUrlResolver().compute(oidcClient.getCallbackUrl(),context).startsWith("https") ? "https" : "http");
        this.setSavedRequestHandler(new LegendRequestHandler());
        this.saveRequestedUrl(context, currentClients, config.getClients().getAjaxRequestResolver());
        HttpAction action = this.redirectToIdentityProvider(context, Collections.singletonList(oidcClient));
        return httpActionAdapter.adapt(action, context);
    }

    private boolean isValidProfilePresent(List<UserProfile> profiles, Client<? extends Credentials> client)
    {
        return profiles.stream().anyMatch(userProfile -> userProfile.getClientName().equals(client.getName()) && !userProfile.isExpired());
    }

    R callParentPerform(C context, Config config, SecurityGrantedAccessAdapter<R, C> securityGrantedAccessAdapter, HttpActionAdapter<R, C> httpActionAdapter, String clients, String authorizers, String matchers, Boolean inputMultiProfile, Object[] parameters)
    {
        LOGGER.debug("Calling parent perform method");
        return super.perform(context, config, securityGrantedAccessAdapter,
                httpActionAdapter, clients, authorizers, matchers, inputMultiProfile, parameters);
    }

    private boolean nonBrowserCall(C context)
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
