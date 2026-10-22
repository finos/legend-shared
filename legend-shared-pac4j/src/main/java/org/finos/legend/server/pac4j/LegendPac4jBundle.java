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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.dropwizard.core.Configuration;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.core.server.SimpleServerFactory;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.eclipse.jetty.ee10.servlet.FilterMapping;
import org.finos.legend.server.pac4j.hazelcaststore.HazelcastSessionStore;
import org.finos.legend.server.pac4j.internal.AcceptHeaderAjaxRequestResolver;
import org.finos.legend.server.pac4j.internal.SecurityFilterHandler;
import org.finos.legend.server.pac4j.internal.UsernameFilter;
import org.finos.legend.server.pac4j.kerberos.SubjectExecutor;
import org.finos.legend.server.pac4j.mongostore.MongoDbSessionStore;
import org.finos.legend.server.pac4j.sessionutil.SessionToken;
import org.pac4j.core.authorization.generator.AuthorizationGenerator;
import org.pac4j.core.client.BaseClient;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.pac4j.core.http.url.DefaultUrlResolver;
import org.pac4j.core.matching.matcher.Matcher;
import org.pac4j.core.matching.matcher.PathMatcher;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.util.serializer.JavaSerializer;
import org.pac4j.dropwizard.Pac4jBundle;
import org.pac4j.dropwizard.Pac4jFactory;
import org.pac4j.dropwizard.Pac4jFeatureSupport;
import org.pac4j.dropwizard.Pac4jMixins;
import org.pac4j.jax.rs.pac4j.JaxRsContext;
import org.pac4j.jax.rs.servlet.pac4j.ServletJaxRsContext;
import org.pac4j.jax.rs.servlet.pac4j.ServletSessionStore;
import org.pac4j.jee.filter.SecurityFilter;

import javax.security.auth.Subject;
import jakarta.servlet.DispatcherType;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class LegendPac4jBundle<C extends Configuration> extends Pac4jBundle<C> implements Pac4jFeatureSupport
{

    private static final String logoutSuffix = "/logout";
    private static final String callBackSuffix = "/callback";
    private static final String callbackMatcher = "notCallback";
    private static final String bypassMatcher = "bypassPaths";
    public static final String PAC4J_SESSION_STORE = "PAC4J_SESSION_STORE";
    private SessionStore legendSessionStore;
    private final Function<C, LegendPac4jConfiguration> configSupplier;
    private final Function<C, Supplier<Subject>> subjectSupplierSupplier;
    private ConfigurationSourceProvider configurationSourceProvider;
    private ObjectMapper objectMapper;

    private String defaultSessionCookieName = "LegendSSO";

    @SuppressWarnings("WeakerAccess")
    public LegendPac4jBundle(Function<C, LegendPac4jConfiguration> configSupplier)
    {
        this(configSupplier, null);
    }

    @SuppressWarnings("WeakerAccess")
    public LegendPac4jBundle(Function<C, LegendPac4jConfiguration> configSupplier,
                               Function<C, Supplier<Subject>> subjectSupplierSupplier)
    {
        this.configSupplier = configSupplier;
        this.subjectSupplierSupplier = subjectSupplierSupplier;
    }

    private static String cleanUrl(String inUrl)
    {
        String url;
        try
        {
            url = new URI(inUrl).normalize().toString();
        } catch (URISyntaxException e)
        {
            throw new RuntimeException("Unable to normalize path " + inUrl, e);
        }
        // For some reason URI.normalize() doesn't clean the start of the URL
        while (url.startsWith("//"))
        {
            url = url.substring(1);
        }
        return url;
    }

    @Override
    public Pac4jFactory getPac4jFactory(C configuration)
    {
        LegendPac4jConfiguration legendConfig = configSupplier.apply(configuration);

        try
        {
            legendConfig.loadDefaults(configurationSourceProvider, objectMapper);
        } catch (IOException | ConfigurationException e)
        {
            throw new RuntimeException(e);
        }

        String applicationContextPath = legendConfig.getCallbackBaseUrl() != null && !legendConfig.getCallbackBaseUrl().isEmpty() ? legendConfig.getCallbackBaseUrl() : "/";
        if (configuration.getServerFactory() instanceof SimpleServerFactory)
        {
            applicationContextPath = ((SimpleServerFactory) configuration.getServerFactory())
                    .getApplicationContextPath();
        }

        final String callbackFilterUrl = cleanUrl(legendConfig.getCallbackPrefix() + callBackSuffix);

        final SubjectExecutor subjectExecutor = new SubjectExecutor(
                Objects.isNull(this.subjectSupplierSupplier) ? null : this.subjectSupplierSupplier.apply(configuration));

        MongoDatabase db = null;
        if (StringUtils.isNotEmpty(legendConfig.getMongoDb()) && StringUtils.isNotEmpty(legendConfig.getMongoUri()))
        {
            MongoClient client = MongoClients.create(legendConfig.getMongoUri());
            db = subjectExecutor.execute(() -> client.getDatabase(legendConfig.getMongoDb()));
        }

        final MongoDatabase finalDb = db;

        final AcceptHeaderAjaxRequestResolver ajaxRequestResolver = new AcceptHeaderAjaxRequestResolver();
        ajaxRequestResolver.setAddRedirectionUrlAsHeader(true);

        PathMatcher bypass = new PathMatcher();
        if (legendConfig.getBypassPaths() != null && !legendConfig.getBypassPaths().isEmpty())
        {
            legendConfig.getBypassPaths().forEach(bypass::excludePath);
        }
        if (legendConfig.getBypassBranches() != null && !legendConfig.getBypassBranches().isEmpty())
        {
            legendConfig.getBypassBranches().forEach(bypass::excludeBranch);
        }
        final Map<String, Matcher> matchers =
                ImmutableMap.of(callbackMatcher, new PathMatcher().excludeRegex("^" + callbackFilterUrl + "$"),
                        bypassMatcher, bypass);

        final LegendUserProfileStorageDecision profileStorageDecision = getProfileStorageDecision(legendConfig);
        // isMultiProfile() is a nullable Boolean: an explicit "multiProfile: null" would NPE on unboxing.
        final boolean multiProfile = Boolean.TRUE.equals(legendConfig.isMultiProfile());

        final LegendSecurityLogic legendSecurityLogic = new LegendSecurityLogic();
        legendSecurityLogic.setClientFinder(legendConfig.getDefaultSecurityClient());
        legendSecurityLogic.setProfileStorageDecision(profileStorageDecision);
        legendSecurityLogic.setMultiProfile(multiProfile);
        // Set once here, not per request: this logic instance is shared by every request.
        legendSecurityLogic.setSavedRequestHandler(new LegendRequestHandler());

        legendConfig.getAuthorizers().stream()
                .filter(a -> a instanceof MongoDbConsumer)
                .forEach(a -> ((MongoDbConsumer) a).setupDb(finalDb));
        Map<String, org.pac4j.core.authorization.authorizer.Authorizer> authorizers =
                legendConfig.getAuthorizers().stream()
                        .collect(Collectors.toMap(a -> a.getClass().getName(), a -> a));

        Pac4jFactory.ServletSecurityFilterConfiguration securityFilterConfiguration =
                new Pac4jFactory.ServletSecurityFilterConfiguration();
        securityFilterConfiguration.setClients(
                legendConfig.getClients().stream().map(Client::getName).collect(Collectors.joining(",")));
        securityFilterConfiguration.setMatchers(String.join(",",
                new String[]{callbackMatcher, bypassMatcher}));
        securityFilterConfiguration.setAuthorizers(String.join(",", authorizers.keySet()));

        Pac4jFactory.ServletConfiguration servletConfiguration =
                new Pac4jFactory.ServletConfiguration();
        servletConfiguration.setSecurity(Collections.singletonList(securityFilterConfiguration));

        Pac4jFactory.ServletCallbackFilterConfiguration callbackFilterConfiguration =
                new Pac4jFactory.ServletCallbackFilterConfiguration();
        callbackFilterConfiguration.setMapping(callbackFilterUrl);
        servletConfiguration.setCallback(Collections.singletonList(callbackFilterConfiguration));

        Pac4jFactory.ServletLogoutFilterConfiguration logoutConfiguration =
                new Pac4jFactory.ServletLogoutFilterConfiguration();
        String logoutUrl = cleanUrl(legendConfig.getCallbackPrefix() + logoutSuffix);
        logoutConfiguration.setMapping(logoutUrl);
        logoutConfiguration.setDestroySession(true);
        servletConfiguration.setLogout(Collections.singletonList(logoutConfiguration));

        final String clientCallbackUrl = cleanUrl(applicationContextPath + callbackFilterUrl);
        Pac4jFactory factory =
                new Pac4jFactory()
                {
                    @Override
                    public Config build()
                    {
                        // pac4j has no ProfileStorageDecision SPI: DefaultSecurityLogic, used whenever
                        // multiProfile is off or the call is non-browser, reads the client's
                        // saveProfileInSession flag, which DirectClient defaults to false. Mirror the
                        // storage decision onto direct clients so both paths agree; indirect clients
                        // default to true and are left alone. The decision only reads the client list.
                        boolean saveProfileInSession = profileStorageDecision
                                .mustSaveProfileInSession(null, legendConfig.getClients(), null, null);
                        legendConfig.getClients().stream()
                                .filter(c -> c instanceof DirectClient)
                                .forEach(c -> ((DirectClient) c).setSaveProfileInSession(saveProfileInSession));

                        // multiProfile is deliberately not mirrored onto the clients: with it set,
                        // DefaultSecurityLogic carries on past an authenticated direct client to the
                        // next one, and a direct client that redirects on missing credentials then
                        // answers an authenticated API call with that redirect. The configured value
                        // reaches the callback through LegendCallbackLogic instead.

                        Config config = super.build();

                        Clients pac4jClients = new Clients(clientCallbackUrl, legendConfig.getClients());
                        pac4jClients.setAjaxRequestResolver(ajaxRequestResolver);
                        pac4jClients.setUrlResolver(new DefaultUrlResolver());
                        config.setClients(pac4jClients);
                        config.setAuthorizers(authorizers);
                        config.setMatchers(matchers);
                        config.setSecurityLogic(legendSecurityLogic);
                        config.setCallbackLogic(new LegendCallbackLogic(multiProfile));

                        String sessionCookieName = legendConfig.getSessionTokenName() != null ? legendConfig.getSessionTokenName() : defaultSessionCookieName;
                        // Resolved eagerly so an invalid value fails startup; pac4j would silently write Lax.
                        String sessionCookieSameSite = SessionToken.sameSitePolicy(legendConfig.getSessionTokenSameSite());
                        if (legendConfig.getHazelcastSession() != null && legendConfig.getHazelcastSession().isEnabled())
                        {
                            HazelcastSessionStore hazelcastSessionStore = new HazelcastSessionStore(
                                    legendConfig.getHazelcastSession().getConfigFilePath(),
                                    ImmutableMap.of(
                                            JEEContext.class, new JEESessionStore(),
                                            JaxRsContext.class, ServletSessionStore.INSTANCE,
                                            ServletJaxRsContext.class, ServletSessionStore.INSTANCE), sessionCookieName);
                            hazelcastSessionStore.setSameSitePolicy(sessionCookieSameSite);
                            setSessionStore(config, hazelcastSessionStore);
                        }
                        else if (legendConfig.getMongoSession() != null && legendConfig.getMongoSession().isEnabled())
                        {

                            if (Objects.isNull(finalDb))
                            {
                                throw new RuntimeException(
                                        "MongoDB needs to be configured if MongoSession is used");
                            }

                            MongoCollection<Document> userSessions = subjectExecutor.execute(
                                    () -> finalDb.getCollection(legendConfig.getMongoSession().getCollection()));

                            MongoDbSessionStore mongoDbSessionStore = new MongoDbSessionStore(
                                    legendConfig.getMongoSession().getCryptoAlgorithm(),
                                    legendConfig.getMongoSession().getMaxSessionLength(),
                                    userSessions, ImmutableMap.of(
                                    JEEContext.class, new JEESessionStore(),
                                    JaxRsContext.class, ServletSessionStore.INSTANCE,
                                    ServletJaxRsContext.class, ServletSessionStore.INSTANCE),
                                    subjectExecutor, legendConfig.getTrustedPackages(), sessionCookieName);
                            mongoDbSessionStore.setSameSitePolicy(sessionCookieSameSite);
                            setSessionStore(config, mongoDbSessionStore);
                        }
                        return config;
                    }
                };

        factory.setServlet(servletConfiguration);
        return factory;
    }

    private void setSessionStore(Config config, SessionStore sessionStore)
    {
        this.legendSessionStore = sessionStore;
        config.setSessionStoreFactory(parameters -> sessionStore);
    }

    LegendUserProfileStorageDecision getProfileStorageDecision(LegendPac4jConfiguration legendConfig)
    {
        if (legendConfig.isAlwaysUseSessionStorage())
        {
            return new AlwaysUseSessionProfileStorageDecision();
        }
        return new LegendUserProfileStorageDecision();
    }

    @Override
    protected void setupJettySession(Environment environment)
    {
        super.setupJettySession(environment);
        environment
                .servlets()
                .addFilter("Username", new UsernameFilter())
                .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

        environment
                .getApplicationContext()
                .setServletHandler(
                        new SecurityFilterHandler(environment.getApplicationContext().getServletHandler())
                        {
                            @Override
                            protected void handleSecurityFilter(SecurityFilter filter)
                            {
                                // No-op, required to meet SecurityFilterHandler interface
                            }

                            @Override
                            protected void handleMapping(FilterMapping mapping)
                            {
                                mapping.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
                            }
                        });
        // Publish only a store Legend installed, not getSessionStoreFactory()'s result: that is never
        // null and defaults to the jax-rs ServletSessionStore, which fails with an AssertionError
        // when consumers pair it with a JEEContext from a servlet filter.
        environment.getApplicationContext()
                        .setAttribute(PAC4J_SESSION_STORE, this.legendSessionStore);
    }

    @Override
    public void setup(Bootstrap<?> bootstrap)
    {
        configurationSourceProvider = bootstrap.getConfigurationSourceProvider();
        objectMapper = bootstrap.getObjectMapper();
        objectMapper.addMixIn(BaseClient.class, IgnoreClientConfigMixin.class);
    }

    /**
     * pac4j 6 overloads setAuthorizationGenerators on List and on varargs, which Jackson rejects as
     * conflicting setters; hiding the varargs one leaves the List setter. The Config pac4j 6 added to
     * BaseClient is wired by this bundle, never read from configuration, so Jackson is kept out of it.
     *
     * <p>Must extend {@link Pac4jMixins.BaseClientMixin}: addMixIn replaces rather than merges the
     * mixin dropwizard-pac4j has already registered, and its {@code @JsonProperty} on the List
     * overload is what stops authorizationGenerators entries from being silently dropped.
     */
    abstract static class IgnoreClientConfigMixin extends Pac4jMixins.BaseClientMixin
    {
        @JsonIgnore
        public abstract void setAuthorizationGenerators(AuthorizationGenerator... authorizationGenerators);

        @JsonIgnore
        public abstract Config getConfig();

        @JsonIgnore
        public abstract BaseClient setConfig(Config config);
    }

    @Override
    protected Collection<Pac4jFeatureSupport> supportedFeatures()
    {
        Collection<Pac4jFeatureSupport> supportedFeatures = super.supportedFeatures();
        supportedFeatures.add(this);
        return supportedFeatures;
    }

    public static JavaSerializer getSerializationHelper(List<String> extraPackages)
    {
        JavaSerializer helper = new JavaSerializer();
        helper.addTrustedPackage("org.finos.legend.server.pac4j."); // Required to serialize KerberosProfile
        helper.addTrustedPackage("org.pac4j.core.profile."); // Required to serialize UserProfile
        helper.addTrustedPackage("javax.security.auth."); // Required to serialize KerberosTicket
        helper.addTrustedPackage("[B"); // byte[] - Required to serialize KerberosTicket
        helper.addTrustedPackage("[Z"); // boolean[] - Required to serialize KerberosTicket
        for (String p:extraPackages)
        {
            helper.addTrustedPackage(p);
        }
        return helper;
    }
}
