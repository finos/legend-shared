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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.ImmutableList;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.Test;
import org.pac4j.core.client.finder.ClientFinder;
import org.pac4j.core.config.Config;
import org.pac4j.core.engine.DefaultSecurityLogic;
import org.pac4j.core.engine.decision.ProfileStorageDecision;
import org.pac4j.dropwizard.Pac4jFactory;
import org.pac4j.jee.filter.SecurityFilter;

import static org.junit.Assert.*;

public class LegendPac4JBundleTest
{
  @Test
  public void testPac4jFactory()
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.of(new TestClient()));
    LegendPac4jBundle<Configuration> bundle = new LegendPac4jBundle<>(c -> config);
    Pac4jFactory factory = bundle.getPac4jFactory(new Configuration());
    assertEquals("/test/callback", factory.getCallbackUrl());
    assertEquals(config.getClients(), factory.getClients());
    Config builtConfig = factory.build();
    assertEquals(config.getClients(), builtConfig.getClients().getClients());
  }

  @Test
  public void testPac4jFactoryWithMultipleClients() throws Exception
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setCallbackPrefix("/test");
    config.setClients(ImmutableList.of(new TestClient(), new SecondTestClient()));
    config.setDefaultClient("SecondTestClient");
    LegendPac4jBundle<Configuration> bundle = new LegendPac4jBundle<>(c -> config);
    Pac4jFactory factory = bundle.getPac4jFactory(new Configuration());
    Config builtConfig = factory.build();
    Environment e = new Environment("serverEnv", null, null, new MetricRegistry(), null, new HealthCheckRegistry());
    bundle.run(new Configuration(),e);
    assertEquals("/test/callback", factory.getCallbackUrl());
    assertEquals(config.getClients(), factory.getClients());
    assertEquals("SecondTestClient", ((LegendClientFinder)((DefaultSecurityLogic)builtConfig.getSecurityLogic()).getClientFinder()).getDefaultClient());
    assertEquals(config.getClients(), builtConfig.getClients().getClients());
    FilterHolder securityHolder = e.getApplicationContext().getServletHandler().getFilter(SecurityFilter.class.getName());
    assertNotNull("Security filter holder cannot be null", securityHolder);
    //initialize the filter so we can confirm swaps
    ServletHandler s =  new ServletHandler();
    s.addFilter(securityHolder);
    s.initialize();
    ClientFinder finder = ((DefaultSecurityLogic)((SecurityFilter)s.getFilters()[0].getFilter()).getSecurityLogic()).getClientFinder();
    assertTrue(finder instanceof LegendClientFinder);
    ProfileStorageDecision storageDecision = ((DefaultSecurityLogic)((SecurityFilter)s.getFilters()[0].getFilter()).getSecurityLogic()).getProfileStorageDecision();
    assertTrue(storageDecision instanceof LegendUserProfileStorageDecision);
  }

}