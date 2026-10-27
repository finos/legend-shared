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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dropwizard.testing.DropwizardTestSupport;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.HttpCookieUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ServerSessionCookieSameSiteTest
{
  private static FakeIdentityProvider identityProvider;

  @TempDir
  Path directory;

  @BeforeAll
  public static void startIdentityProvider() throws Exception
  {
    identityProvider = new FakeIdentityProvider();
  }

  @AfterAll
  public static void stopIdentityProvider()
  {
    identityProvider.close();
  }

  @ParameterizedTest(name = "sessionCookieSameSite \"{0}\"")
  @CsvSource({"Lax, Lax, LAX", "lax, Lax, LAX", "None, None, NONE", "STRICT, Strict, STRICT", "' Lax ', Lax, LAX"})
  public void theConfiguredSameSiteReachesTheSessionCookieAndTheServletDefault(String configured, String attribute, HttpCookie.SameSite servletDefault) throws Exception
  {
    DropwizardTestSupport<StaticServerConfiguration> server = start(configured);
    try
    {
      String sessionCookie = ServerIndirectLoginTest.sessionCookie(ServerIndirectLoginTest.get(
          HttpClient.newHttpClient(), server.getLocalPort(), "/ui/studio/edit", ServerIndirectLoginTest.BROWSER_ACCEPT, null));

      List<String> attributes = Arrays.stream(sessionCookie.split(";")).map(String::trim).collect(Collectors.toList());
      assertTrue(attributes.contains("SameSite=" + attribute), sessionCookie);
      assertTrue(attributes.contains("Secure"), sessionCookie);
      assertTrue(attributes.contains("HttpOnly"), sessionCookie);
      assertEquals(servletDefault, HttpCookieUtils.getSameSiteDefault(server.getEnvironment().getApplicationContext().getContext()));
    }
    finally
    {
      server.after();
    }
  }

  @Test
  public void anUnknownSameSiteStopsTheServerFromStarting() throws Exception
  {
    DropwizardTestSupport<StaticServerConfiguration> server = new DropwizardTestSupport<>(
        Server.class, identityProvider.writeConfiguration(directory, "Relaxed").toString());

    Exception failure = assertThrows(Exception.class, server::before);

    assertTrue(causes(failure).contains("sessionCookieSameSite must be Strict, Lax or None, not 'Relaxed'"), causes(failure));
  }

  private DropwizardTestSupport<StaticServerConfiguration> start(String sessionCookieSameSite) throws Exception
  {
    DropwizardTestSupport<StaticServerConfiguration> server = new DropwizardTestSupport<>(
        Server.class, identityProvider.writeConfiguration(directory, sessionCookieSameSite).toString());
    server.before();
    return server;
  }

  private static String causes(Throwable failure)
  {
    StringBuilder messages = new StringBuilder();
    for (Throwable t = failure; t != null; t = t.getCause())
    {
      messages.append(t).append('\n');
    }
    return messages.toString();
  }
}
