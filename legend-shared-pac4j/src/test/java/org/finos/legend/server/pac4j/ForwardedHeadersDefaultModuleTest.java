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

package org.finos.legend.server.pac4j;

import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.SimpleServerFactory;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jetty.HttpConnectorFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The configuration is parsed the way a server parses it: a Bootstrap that the bundle was added to, and the
 * configuration factory Dropwizard builds from it.
 */
public class ForwardedHeadersDefaultModuleTest
{
  private static final String OVERRIDE_PROPERTY = "dw.server.applicationConnectors[0].useForwardedHeaders";

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void withoutTheBundleDropwizard5DoesNotHonourForwardedHeaders() throws Exception
  {
    // the regression the module undoes; if Dropwizard's default changes back, the module can go
    assertFalse(applicationConnector(parse(false, defaultServer(""))).isUseForwardedHeaders());
  }

  @Test
  public void anApplicationConnectorThatDoesNotSayHonoursForwardedHeaders() throws Exception
  {
    assertTrue(applicationConnector(parse(true, defaultServer(""))).isUseForwardedHeaders());
  }

  @Test
  public void anAdminConnectorThatDoesNotSayHonoursForwardedHeadersAsOnDropwizard13() throws Exception
  {
    DefaultServerFactory server = (DefaultServerFactory) parse(true, defaultServer("")).getServerFactory();
    assertTrue(((HttpConnectorFactory) server.getAdminConnectors().get(0)).isUseForwardedHeaders());
  }

  @Test
  public void anExplicitFalseIsKept() throws Exception
  {
    assertFalse(applicationConnector(parse(true, defaultServer("      useForwardedHeaders: false\n"))).isUseForwardedHeaders());
  }

  @Test
  public void aSystemPropertyOverrideStillWins() throws Exception
  {
    System.setProperty(OVERRIDE_PROPERTY, "false");
    try
    {
      assertFalse(applicationConnector(parse(true, defaultServer(""))).isUseForwardedHeaders());
    }
    finally
    {
      System.clearProperty(OVERRIDE_PROPERTY);
    }
  }

  @Test
  public void theSimpleServersConnectorHonoursForwardedHeaders() throws Exception
  {
    String yaml = "server:\n  type: simple\n  applicationContextPath: /\n  connector:\n    type: http\n    port: 0\n";
    SimpleServerFactory server = (SimpleServerFactory) parse(true, yaml).getServerFactory();
    assertTrue(((HttpConnectorFactory) server.getConnector()).isUseForwardedHeaders());
  }

  @Test
  public void addingTheBundleTwiceIsHarmless() throws Exception
  {
    Bootstrap<Configuration> bootstrap = new Bootstrap<>(new EmptyApplication());
    bootstrap.addBundle(new LegendPac4jBundle<>(c -> null));
    bootstrap.addBundle(new LegendPac4jBundle<>(c -> null));
    assertTrue(applicationConnector(parse(bootstrap, defaultServer(""))).isUseForwardedHeaders());
  }

  private static HttpConnectorFactory applicationConnector(Configuration configuration)
  {
    return (HttpConnectorFactory) ((DefaultServerFactory) configuration.getServerFactory()).getApplicationConnectors().get(0);
  }

  private static String defaultServer(String extraConnectorLines)
  {
    return "server:\n  applicationConnectors:\n    - type: http\n      port: 0\n" + extraConnectorLines +
        "  adminConnectors:\n    - type: http\n      port: 0\n";
  }

  private Configuration parse(boolean withBundle, String yaml) throws Exception
  {
    Bootstrap<Configuration> bootstrap = new Bootstrap<>(new EmptyApplication());
    if (withBundle)
    {
      bootstrap.addBundle(new LegendPac4jBundle<>(c -> null));
    }
    return parse(bootstrap, yaml);
  }

  private Configuration parse(Bootstrap<Configuration> bootstrap, String yaml) throws Exception
  {
    File file = this.folder.newFile();
    Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
    return bootstrap.getConfigurationFactoryFactory()
        .create(Configuration.class, bootstrap.getValidatorFactory().getValidator(), bootstrap.getObjectMapper(), "dw")
        .build(new FileConfigurationSourceProvider(), file.getPath());
  }

  private static class EmptyApplication extends Application<Configuration>
  {
    @Override
    public void run(Configuration configuration, Environment environment)
    {
    }
  }
}
