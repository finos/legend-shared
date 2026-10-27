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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.SimpleServerFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jetty.http.UriCompliance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DropwizardExtensionsSupport.class)
public class EncodedPathSeparatorsTest
{
  private static final String SECRET = "outside the asset root";
  private static final MongoServer MONGO = new MongoServer(new MemoryBackend());
  private static final InetSocketAddress MONGO_ADDRESS = MONGO.bind();
  private static final Path ASSET_ROOT = createAssetRootNextToASecret();

  public static final DropwizardAppExtension<StaticServerConfiguration> APP = new DropwizardAppExtension<>(
      Server.class, ResourceHelpers.resourceFilePath("staticServerEncodedPathConfig.json"),
      ConfigOverride.config("pac4j.mongoUri", "mongodb://127.0.0.1:" + MONGO_ADDRESS.getPort()),
      ConfigOverride.config("localAssetPaths./ui/static/local", ASSET_ROOT.toString()));

  @AfterAll
  public static void stopMongo()
  {
    MONGO.shutdown();
  }

  @Test
  public void aDeepLinkWithAnEncodedSeparatorServesTheApp() throws Exception
  {
    assertServesTheApp("/ui/showcase/a%2Fb%2Fc.pure");
    assertServesTheApp("/ui/showcase/a%2fb");
    assertServesTheApp("/ui/showcase/plain");
  }

  @Test
  public void anEncodedSeparatorCannotClimbOutOfTheAssetRoot() throws Exception
  {
    HttpResponse<String> asset = get("/ui/static/local/asset.txt");
    assertEquals(200, asset.statusCode());
    assertEquals("inside the asset root", asset.body());

    assertRejected("/ui/static/local/..%2Fsecret.txt");
    assertRejected("/ui/static/local%2F..%2Fsecret.txt");
    assertRejected("/ui/static/local/%2E%2E%2Fsecret.txt");
    assertRejected("/ui/showcase/%2F..%2F..%2Fadmin%2Fhealthcheck");
    assertRejected("/ui/showcase/a%2F.%2Fb");
  }

  @Test
  public void anEncodedPercentIsStillRejected() throws Exception
  {
    assertEquals(400, get("/ui/showcase/a%25b").statusCode());
  }

  @Test
  public void anEmptySegmentIsStillRejected() throws Exception
  {
    assertEquals(400, get("/ui//showcase").statusCode());
  }

  @Test
  public void theRuleItself()
  {
    assertTrue(EncodedPathSeparators.isAllowed("/ui/showcase/plain"));
    assertTrue(EncodedPathSeparators.isAllowed("/ui/showcase/a%2Fb"));
    assertTrue(EncodedPathSeparators.isAllowed("/ui/showcase/a%2F..b"));
    assertTrue(EncodedPathSeparators.isAllowed("/ui/static/../x"));
    assertFalse(EncodedPathSeparators.isAllowed("/ui/showcase/a%2F..%2Fb"));
    assertFalse(EncodedPathSeparators.isAllowed("/ui/showcase/a%2f%2e%2E%2fb"));
    assertFalse(EncodedPathSeparators.isAllowed("/ui/showcase/a%2F."));
    assertFalse(EncodedPathSeparators.isAllowed("/ui/showcase/a%2F%2"));
  }

  @Test
  public void everyApplicationConnectorOfADefaultServerTakesAnEncodedSeparator()
  {
    DefaultServerFactory factory = new DefaultServerFactory();
    HttpConnectorFactory admin = (HttpConnectorFactory) factory.getAdminConnectors().get(0);
    UriCompliance adminBefore = admin.getUriCompliance();

    EncodedPathSeparators.allowOnApplicationConnectors(factory);

    HttpConnectorFactory application = (HttpConnectorFactory) factory.getApplicationConnectors().get(0);
    assertTrue(application.getUriCompliance().allows(UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR));
    assertEquals(adminBefore, admin.getUriCompliance());
  }

  @Test
  public void theConnectorOfASimpleServerTakesAnEncodedSeparatorAndKeepsWhatWasConfigured()
  {
    SimpleServerFactory factory = new SimpleServerFactory();
    HttpConnectorFactory connector = (HttpConnectorFactory) factory.getConnector();
    connector.setUriCompliance(UriCompliance.DEFAULT.with("configured", UriCompliance.Violation.SUSPICIOUS_PATH_CHARACTERS));

    EncodedPathSeparators.allowOnApplicationConnectors(factory);

    assertTrue(connector.getUriCompliance().allows(UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR));
    assertTrue(connector.getUriCompliance().allows(UriCompliance.Violation.SUSPICIOUS_PATH_CHARACTERS));
    assertFalse(connector.getUriCompliance().allows(UriCompliance.Violation.AMBIGUOUS_PATH_ENCODING));
  }

  private static void assertServesTheApp(String path) throws Exception
  {
    HttpResponse<String> response = get(path);
    assertEquals(200, response.statusCode(), path);
    assertTrue(response.body().contains("TEST INDEX.HTML"), path);
  }

  private static void assertRejected(String path) throws Exception
  {
    HttpResponse<String> response = get(path);
    assertEquals(400, response.statusCode(), path);
    assertFalse(response.body().contains(SECRET), path);
  }

  private static HttpResponse<String> get(String path) throws Exception
  {
    return HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + APP.getLocalPort() + path)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static Path createAssetRootNextToASecret()
  {
    try
    {
      Path parent = Files.createTempDirectory("encoded-path-separators");
      Files.write(parent.resolve("secret.txt"), SECRET.getBytes(StandardCharsets.UTF_8));
      Path root = Files.createDirectory(parent.resolve("assets"));
      Files.write(root.resolve("asset.txt"), "inside the asset root".getBytes(StandardCharsets.UTF_8));
      return root;
    }
    catch (IOException e)
    {
      throw new UncheckedIOException(e);
    }
  }
}
