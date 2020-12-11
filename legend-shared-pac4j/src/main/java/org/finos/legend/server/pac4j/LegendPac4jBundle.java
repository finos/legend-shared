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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.dropwizard.Configuration;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.server.SimpleServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import javax.security.auth.Subject;
import javax.servlet.DispatcherType;
import org.apache.commons.lang.StringUtils;
import org.bson.Document;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.finos.legend.server.pac4j.internal.AcceptHeaderAjaxRequestResolver;
import org.finos.legend.server.pac4j.internal.SecurityFilterHandler;
import org.finos.legend.server.pac4j.internal.UsernameFilter;
import org.finos.legend.server.pac4j.kerberos.SubjectExecutor;
import org.finos.legend.server.pac4j.mongostore.MongoDbSessionStore;
import org.pac4j.core.client.Client;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.session.J2ESessionStore;
import org.pac4j.core.engine.DefaultSecurityLogic;
import org.pac4j.core.engine.decision.AlwaysUseSessionProfileStorageDecision;
import org.pac4j.core.http.url.DefaultUrlResolver;
import org.pac4j.core.matching.Matcher;
import org.pac4j.core.matching.PathMatcher;
import org.pac4j.core.util.JavaSerializationHelper;
import org.pac4j.dropwizard.Pac4jBundle;
import org.pac4j.dropwizard.Pac4jFactory;
import org.pac4j.dropwizard.Pac4jFeatureSupport;
import org.pac4j.j2e.filter.SecurityFilter;
import org.pac4j.jax.rs.pac4j.JaxRsContext;
import org.pac4j.jax.rs.servlet.pac4j.ServletSessionStore;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
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
  private final Function<C, LegendPac4jConfiguration> configSupplier;
  private final Function<C, Supplier<Subject>> subjectSupplierSupplier;
  private ConfigurationSourceProvider configurationSourceProvider;
  private ObjectMapper objectMapper;

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

    String applicationContextPath = "/";
    if (configuration.getServerFactory() instanceof SimpleServerFactory)
    {
      applicationContextPath = ((SimpleServerFactory) configuration.getServerFactory())
          .getApplicationContextPath();
    }

    String callbackFilterUrl = cleanUrl(legendConfig.getCallbackPrefix() + callBackSuffix);
    String clientCallbackUrl = cleanUrl(applicationContextPath + callbackFilterUrl);

    final SubjectExecutor subjectExecutor = new SubjectExecutor(
        Objects.isNull(this.subjectSupplierSupplier) ? null : this.subjectSupplierSupplier.apply(configuration));

    MongoDatabase db = null;
    if (StringUtils.isNotEmpty(legendConfig.getMongoDb())
        && StringUtils.isNotEmpty(legendConfig.getMongoUri()))
    {
      MongoClient client = new MongoClient(new MongoClientURI(legendConfig.getMongoUri()));
      db = subjectExecutor.execute(() -> client.getDatabase(legendConfig.getMongoDb()));
    }

    MongoDatabase finalDb = db;
    Pac4jFactory factory =
        new Pac4jFactory()
        {
          @Override
          public Config build()
          {
            Config config = super.build();

            if (legendConfig.getMongoSession() != null
                && legendConfig.getMongoSession().isEnabled())
            {

              if (Objects.isNull(finalDb))
              {
                throw new RuntimeException(
                    "MongoDB needs to be configured if MongoSession is used");
              }

              MongoCollection<Document> userSessions = subjectExecutor.execute(
                  () -> finalDb.getCollection(legendConfig.getMongoSession().getCollection()));

              config.setSessionStore(
                  new MongoDbSessionStore(
                      legendConfig.getMongoSession().getCryptoAlgorithm(),
                      legendConfig.getMongoSession().getMaxSessionLength(),
                      userSessions, ImmutableMap.of(
                      J2EContext.class, new J2ESessionStore(),
                      JaxRsContext.class, new ServletSessionStore()),
                      subjectExecutor));
            }
            return config;
          }
        };
    factory.setCallbackUrl(clientCallbackUrl);
    factory.setAjaxRequestResolver(new AcceptHeaderAjaxRequestResolver());
    factory.setUrlResolver(new DefaultUrlResolver());
    Pac4jFactory.ServletConfiguration servletConfiguration =
        new Pac4jFactory.ServletConfiguration();
    Pac4jFactory.ServletSecurityFilterConfiguration securityFilterConfiguration =
        new Pac4jFactory.ServletSecurityFilterConfiguration();
    securityFilterConfiguration.setClients(
        legendConfig.getClients().stream().map(Client::getName).collect(Collectors.joining(",")));

    securityFilterConfiguration.setMatchers(String.join(",",
        new String[]{callbackMatcher, bypassMatcher}));

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

    legendConfig.getAuthorizers().stream()
        .filter(a -> a instanceof MongoDbConsumer)
        .forEach(a -> ((MongoDbConsumer) a).setupDb(finalDb));

    factory.setAuthorizers(legendConfig.getAuthorizers().stream()
        .collect(Collectors.toMap(a -> a.getClass().getName(), a -> a)));
    securityFilterConfiguration.setAuthorizers(String.join(",", factory.getAuthorizers().keySet()));
    DefaultSecurityLogic s = new DefaultSecurityLogic();
    s.setClientFinder(legendConfig.getDefaultSecurityClient());
    s.setProfileStorageDecision(new LegendUserProfileStorageDecision());
    factory.setSecurityLogic(s);
    factory.setServlet(servletConfiguration);

    PathMatcher matcher = new PathMatcher();
    if (legendConfig.getBypassPaths() != null && !legendConfig.getBypassPaths().isEmpty())
    {
      legendConfig.getBypassPaths().forEach(matcher::excludePath);
    }
    if (legendConfig.getBypassBranches() != null && !legendConfig.getBypassBranches().isEmpty())
    {
      legendConfig.getBypassBranches().forEach(matcher::excludeBranch);
    }
    Map<String, Matcher> matchers =
        ImmutableMap.of(callbackMatcher, new PathMatcher("^" + callbackFilterUrl + "$"),
            bypassMatcher, matcher);
    factory.setMatchers(matchers);
    factory.setClients(legendConfig.getClients());
    return factory;
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
                DefaultSecurityLogic<Object, J2EContext> logic =
                    (DefaultSecurityLogic<Object, J2EContext>) filter.getSecurityLogic();
                logic.setProfileStorageDecision(new AlwaysUseSessionProfileStorageDecision());
              }

              @Override
              protected void handleMapping(FilterMapping mapping)
              {
                mapping.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
              }
            });
    for (FilterHolder h: environment.getApplicationContext().getServletHandler().getFilters())
    {
      if (h.getFilter() instanceof SecurityFilter)
      {
        SecurityFilter filter = (SecurityFilter) h.getFilter();
        DefaultSecurityLogic securityLogic = (DefaultSecurityLogic) filter.getSecurityLogic();
        securityLogic.setClientFinder(((DefaultSecurityLogic)this.getConfig().getSecurityLogic()).getClientFinder());
        securityLogic.setProfileStorageDecision(new LegendUserProfileStorageDecision());
        filter.setSecurityLogic(securityLogic);
        h.setFilter(filter);
      }
    }
  }

  @Override
  public void setup(Bootstrap<?> bootstrap)
  {
    configurationSourceProvider = bootstrap.getConfigurationSourceProvider();
    objectMapper = bootstrap.getObjectMapper();
  }

  @Override
  protected Collection<Pac4jFeatureSupport> supportedFeatures()
  {
    Collection<Pac4jFeatureSupport> supportedFeatures = super.supportedFeatures();
    supportedFeatures.add(this);
    return supportedFeatures;
  }

  public static JavaSerializationHelper getSerializationHelper()
  {
    JavaSerializationHelper helper = new JavaSerializationHelper();
    helper.addTrustedPackage("org.finos.legend.server.pac4j."); // Required to serialize KerberosProfile
    helper.addTrustedPackage("org.pac4j.core.profile."); // Required to serialize UserProfile
    helper.addTrustedPackage("javax.security.auth."); // Required to serialize KerberosTicket
    helper.addTrustedPackage("[B"); // byte[] - Required to serialize KerberosTicket
    helper.addTrustedPackage("[Z"); // boolean[] - Required to serialize KerberosTicket
    return helper;
  }
}
