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

package org.finos.legend.server.shared.staticserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.core.Response;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DropwizardExtensionsSupport.class)
public class ServerSameSiteFromDefaultsTest
{
  private static final MongoServer MONGO = new MongoServer(new MemoryBackend());
  private static final InetSocketAddress MONGO_ADDRESS = MONGO.bind();

  public static final DropwizardAppExtension<StaticServerConfiguration> APP = new DropwizardAppExtension<>(
      Server.class, ResourceHelpers.resourceFilePath("staticServerSameSiteConfig.json"),
      ConfigOverride.config("pac4j.mongoUri", "mongodb://127.0.0.1:" + MONGO_ADDRESS.getPort()),
      ConfigOverride.config("pac4j.defaults", ResourceHelpers.resourceFilePath("staticServerSameSiteDefaults.yaml")));

  @AfterAll
  public static void stopMongo()
  {
    MONGO.shutdown();
  }

  @Test
  public void theSameSiteFromTheDefaultsFileReachesTheSsoCookie()
  {
    Response response = APP.client()
        .target(String.format("http://localhost:%d/ui/anything", APP.getLocalPort()))
        .request()
        .get();

    List<String> ssoCookies = response.getStringHeaders().get("Set-Cookie").stream()
        .filter(c -> c.startsWith("LegendSSO="))
        .collect(Collectors.toList());
    assertEquals(1, ssoCookies.size(), "expected one SSO cookie: " + response.getStringHeaders());
    assertTrue(ssoCookies.get(0).contains("SameSite=None"), ssoCookies.get(0));
  }
}
