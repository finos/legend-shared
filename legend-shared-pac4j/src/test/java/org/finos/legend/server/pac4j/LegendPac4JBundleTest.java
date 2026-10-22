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

import com.google.common.collect.ImmutableList;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Environment;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.junit.Test;
import org.pac4j.core.client.BaseClient;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.client.finder.ClientFinder;
import org.pac4j.core.config.Config;
import org.pac4j.core.engine.DefaultSecurityLogic;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.dropwizard.Pac4jFactory;
import org.pac4j.jee.filter.SecurityFilter;

import java.util.Collections;

import static org.junit.Assert.*;

public class LegendPac4JBundleTest
{
  @Test
  public void testPac4jFactory()
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.<Client>of(new TestClient()));
    LegendPac4jBundle<Configuration> bundle = new LegendPac4jBundle<>(c -> config);
    Pac4jFactory factory = bundle.getPac4jFactory(new Configuration());
    Config builtConfig = factory.build();
    assertEquals("/test/callback", builtConfig.getClients().getCallbackUrl());
    assertEquals(config.getClients(), builtConfig.getClients().getClients());
  }

  /**
   * With {@code multiProfile} off, pac4j 6's {@code DefaultSecurityLogic} persists a direct client's
   * profile based on {@code DirectClient.getSaveProfileInSession}, not the profile storage decision.
   */
  @Test
  public void serializableDirectClientSavesProfileInSessionOnTheDefaultPath()
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.<Client>of(new TestClient()));
    assertFalse("this test is only meaningful with multiProfile off", config.isMultiProfile());

    Config builtConfig = new LegendPac4jBundle<>(c -> config)
            .getPac4jFactory(new Configuration()).build();

    DirectClient client = (DirectClient) builtConfig.getClients().getClients().get(0);
    assertEquals("a @SerializableProfile DirectClient must save its profile in the session",
            Boolean.TRUE, client.getSaveProfileInSession());
  }

  @Test
  public void unannotatedClientDoesNotSaveProfileInSession()
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.of((Client) new TestClient(), new SecondTestClient()));

    Config builtConfig = new LegendPac4jBundle<>(c -> config)
            .getPac4jFactory(new Configuration()).build();

    for (Client client : builtConfig.getClients().getClients())
    {
      assertNotEquals("no client may save profiles when any client is unannotated: " + client,
              Boolean.TRUE, ((DirectClient) client).getSaveProfileInSession());
    }
  }

  @Test
  public void alwaysUseSessionStorageReachesTheDefaultPath()
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.of((Client) new TestClient(), new SecondTestClient()));
    config.setAlwaysUseSessionStorage(true);

    Config builtConfig = new LegendPac4jBundle<>(c -> config)
            .getPac4jFactory(new Configuration()).build();

    for (Client client : builtConfig.getClients().getClients())
    {
      assertEquals("alwaysUseSessionStorage must override the annotation check: " + client,
              Boolean.TRUE, ((DirectClient) client).getSaveProfileInSession());
    }
  }

  @Test
  public void saveProfileInSessionSurvivesClientInit()
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.<Client>of(new TestClient()));

    Config builtConfig = new LegendPac4jBundle<>(c -> config)
            .getPac4jFactory(new Configuration()).build();

    DirectClient client = (DirectClient) builtConfig.getClients().getClients().get(0);
    try
    {
      client.init();
    }
    catch (TechnicalException expected)
    {
      // TestClient has no credentialsExtractor, so afterInternalInit throws; beforeInternalInit,
      // which re-defaults saveProfileInSession, has already run by then.
      assertTrue(expected.getMessage(), expected.getMessage().contains("credentialsExtractor"));
    }

    assertEquals("client init() must not reset the storage decision",
            Boolean.TRUE, client.getSaveProfileInSession());
  }

  /**
   * The clients must stay single-profile: pac4j 6's {@code DefaultSecurityLogic} also reads
   * {@code BaseClient.isMultiProfile}, and a true flag there makes a non-browser call carry on past
   * the client that authenticated it.
   */
  @Test
  public void multiProfileConfigurationReachesTheCallbackLogicButNotTheClients()
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setMultiProfile(true);
    config.setClients(ImmutableList.of((Client) new TestClient(), new SecondTestClient()));

    Config builtConfig = new LegendPac4jBundle<>(c -> config)
            .getPac4jFactory(new Configuration()).build();

    assertTrue("the callback filter must use LegendCallbackLogic",
            builtConfig.getCallbackLogic() instanceof LegendCallbackLogic);
    assertTrue("multiProfile: true must reach the callback filter",
            ((LegendCallbackLogic) builtConfig.getCallbackLogic()).isMultiProfile());
    for (Client client : builtConfig.getClients().getClients())
    {
      assertFalse("multiProfile must not be mirrored onto the clients: " + client.getName(),
              ((BaseClient) client).isMultiProfile());
    }
  }

  @Test
  public void multiProfileStaysOffByDefault()
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.of((Client) new TestClient(), new SecondTestClient()));
    assertFalse("this test is only meaningful with multiProfile off", config.isMultiProfile());

    Config builtConfig = new LegendPac4jBundle<>(c -> config)
            .getPac4jFactory(new Configuration()).build();

    assertFalse("multiProfile must stay off at the callback when it is not configured",
            ((LegendCallbackLogic) builtConfig.getCallbackLogic()).isMultiProfile());
    for (Client client : builtConfig.getClients().getClients())
    {
      assertFalse("multiProfile must stay off when it is not configured: " + client.getName(),
              ((BaseClient) client).isMultiProfile());
    }
  }

  @Test
  public void explicitlyNullMultiProfileIsTreatedAsFalse()
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.<Client>of(new TestClient()));
    config.setMultiProfile(null);

    Config builtConfig = new LegendPac4jBundle<>(c -> config)
            .getPac4jFactory(new Configuration()).build();

    assertFalse(((LegendSecurityLogic) builtConfig.getSecurityLogic()).isMultiProfile());
    assertFalse(((BaseClient) builtConfig.getClients().getClients().get(0)).isMultiProfile());
    assertFalse(((LegendCallbackLogic) builtConfig.getCallbackLogic()).isMultiProfile());
  }

  @Test
  public void anUnsupportedSessionTokenSameSiteFailsTheBuild()
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.<Client>of(new TestClient()));
    config.setSessionTokenSameSite("Strictt");

    try
    {
      new LegendPac4jBundle<>(c -> config).getPac4jFactory(new Configuration()).build();
      fail("an unsupported sessionTokenSameSite must fail the build");
    }
    catch (IllegalArgumentException expected)
    {
      assertTrue(expected.getMessage(), expected.getMessage().contains("Strictt"));
    }
  }

  @Test
  public void savedRequestHandlerIsInstalledWhenTheSecurityLogicIsBuilt()
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.<Client>of(new TestClient()));

    Config builtConfig = new LegendPac4jBundle<>(c -> config)
            .getPac4jFactory(new Configuration()).build();

    assertTrue("the saved request handler must be installed before any request is served",
            ((DefaultSecurityLogic) builtConfig.getSecurityLogic()).getSavedRequestHandler()
                    instanceof LegendRequestHandler);
  }

  @Test
  public void testPac4jFactoryWithMultipleClients() throws Exception
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.of((Client) new TestClient(), new SecondTestClient()));
    config.setDefaultClient(Collections.singletonList("SecondTestClient"));
    LegendPac4jBundle<Configuration> bundle = new LegendPac4jBundle<>(c -> config);
    Pac4jFactory factory = bundle.getPac4jFactory(new Configuration());
    Config builtConfig = factory.build();
    Environment e = new Environment("serverEnv");
    bundle.run(new Configuration(),e);
    assertEquals("/test/callback", builtConfig.getClients().getCallbackUrl());
    assertEquals(Collections.singletonList("SecondTestClient"), ((LegendClientFinder)((DefaultSecurityLogic)builtConfig.getSecurityLogic()).getClientFinder()).getDefaultClients());
    assertEquals(config.getClients(), builtConfig.getClients().getClients());
    FilterHolder securityHolder = e.getApplicationContext().getServletHandler().getFilter(SecurityFilter.class.getName());
    assertNotNull("Security filter holder cannot be null", securityHolder);
    //initialize the filter so we can confirm swaps
    ServletHandler s =  new ServletHandler();
    s.addFilter(securityHolder);
    s.initialize();
    ClientFinder finder = ((DefaultSecurityLogic)((SecurityFilter)s.getFilters()[0].getFilter()).getConfig().getSecurityLogic()).getClientFinder();
    assertTrue(finder instanceof LegendClientFinder);
    LegendUserProfileStorageDecision storageDecision = ((LegendSecurityLogic)((SecurityFilter)s.getFilters()[0].getFilter()).getConfig().getSecurityLogic()).getProfileStorageDecision();
    assertTrue(storageDecision instanceof LegendUserProfileStorageDecision);
  }

  @Test
  public void testPac4jFactoryWithAlwaysUseSessionStorageSet() throws Exception
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.of((Client) new TestClient(), new SecondTestClient()));
    config.setDefaultClient(Collections.singletonList("SecondTestClient"));
    config.setAlwaysUseSessionStorage(true);
    LegendPac4jBundle<Configuration> bundle = new LegendPac4jBundle<>(c -> config);
    Pac4jFactory factory = bundle.getPac4jFactory(new Configuration());
    Config builtConfig = factory.build();
    Environment e = new Environment("serverEnv");
    bundle.run(new Configuration(),e);
    assertEquals("/test/callback", builtConfig.getClients().getCallbackUrl());
    assertEquals(Collections.singletonList("SecondTestClient"), ((LegendClientFinder)((DefaultSecurityLogic)builtConfig.getSecurityLogic()).getClientFinder()).getDefaultClients());
    assertEquals(config.getClients(), builtConfig.getClients().getClients());
    FilterHolder securityHolder = e.getApplicationContext().getServletHandler().getFilter(SecurityFilter.class.getName());
    assertNotNull("Security filter holder cannot be null", securityHolder);
    //initialize the filter so we can confirm swaps
    ServletHandler s =  new ServletHandler();
    s.addFilter(securityHolder);
    s.initialize();
    ClientFinder finder = ((DefaultSecurityLogic)((SecurityFilter)s.getFilters()[0].getFilter()).getConfig().getSecurityLogic()).getClientFinder();
    assertTrue(finder instanceof LegendClientFinder);
    LegendUserProfileStorageDecision storageDecision = ((LegendSecurityLogic)((SecurityFilter)s.getFilters()[0].getFilter()).getConfig().getSecurityLogic()).getProfileStorageDecision();
    assertTrue(storageDecision instanceof AlwaysUseSessionProfileStorageDecision);
  }

}
