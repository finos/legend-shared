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

import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DropwizardExtensionsSupport.class)
public class ServerIndirectLoginTest
{
  static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";

  private static final FakeIdentityProvider IDENTITY_PROVIDER = startIdentityProvider();

  public static final DropwizardAppExtension<StaticServerConfiguration> APP = new DropwizardAppExtension<>(
      Server.class, writeConfiguration());

  private final HttpClient http = HttpClient.newHttpClient();

  @AfterAll
  public static void stopIdentityProvider()
  {
    IDENTITY_PROVIDER.close();
  }

  @BeforeEach
  public void forgetEarlierRequests()
  {
    IDENTITY_PROVIDER.requests().clear();
  }

  @Test
  public void aBrowserIsRedirectedToTheIdentityProvider() throws Exception
  {
    HttpResponse<String> response = get("/ui/studio/edit", BROWSER_ACCEPT, null);

    assertEquals(302, response.statusCode(), response.body());
    String location = response.headers().firstValue("Location").orElse("");
    assertTrue(location.startsWith(IDENTITY_PROVIDER.url() + "/authorize?"), location);
    assertTrue(location.contains("client_id=studio"), location);
    assertTrue(location.contains("state="), location);
  }

  @Test
  public void aScriptIsToldItIsUnauthorized() throws Exception
  {
    assertEquals(401, get("/ui/studio/edit", "application/json", null).statusCode());
  }

  @Test
  public void aRequestWithoutAnAcceptHeaderIsToldItIsUnauthorized() throws Exception
  {
    assertEquals(401, get("/ui/studio/edit", null, null).statusCode());
  }

  @Test
  public void theSessionCookieIsSecureHttpOnlyAndSameSiteStrictByDefault() throws Exception
  {
    String sessionCookie = sessionCookie(get("/ui/studio/edit", BROWSER_ACCEPT, null));

    List<String> attributes = Arrays.stream(sessionCookie.split(";")).map(String::trim).collect(Collectors.toList());
    assertTrue(attributes.contains("Secure"), sessionCookie);
    assertTrue(attributes.contains("HttpOnly"), sessionCookie);
    assertTrue(attributes.contains("SameSite=Strict"), sessionCookie);
  }

  @Test
  public void theCallbackFindsTheLoginStateThroughTheSessionCookie() throws Exception
  {
    HttpResponse<String> login = get("/ui/studio/edit", BROWSER_ACCEPT, null);

    get(callback(login), BROWSER_ACCEPT, sessionCookie(login).split(";")[0]);

    assertTrue(IDENTITY_PROVIDER.wasAskedForAToken(), IDENTITY_PROVIDER.requests().toString());
  }

  @Test
  public void withoutTheSessionCookieTheCallbackCannotFindTheLoginState() throws Exception
  {
    HttpResponse<String> login = get("/ui/studio/edit", BROWSER_ACCEPT, null);

    get(callback(login), BROWSER_ACCEPT, null);

    assertFalse(IDENTITY_PROVIDER.wasAskedForAToken(), IDENTITY_PROVIDER.requests().toString());
  }

  private HttpResponse<String> get(String path, String accept, String cookie) throws Exception
  {
    return get(http, APP.getLocalPort(), path, accept, cookie);
  }

  static HttpResponse<String> get(HttpClient http, int port, String path, String accept, String cookie) throws Exception
  {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
    if (accept != null)
    {
      request.header("Accept", accept);
    }
    if (cookie != null)
    {
      request.header("Cookie", cookie);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  static String sessionCookie(HttpResponse<String> response)
  {
    List<String> cookies = response.headers().allValues("Set-Cookie").stream()
        .filter(c -> c.startsWith("LEGEND_TEST_JSESSIONID="))
        .collect(Collectors.toList());
    assertEquals(1, cookies.size(), "expected one session cookie: " + response.headers().map());
    return cookies.get(0);
  }

  private static String callback(HttpResponse<String> login)
  {
    String location = login.headers().firstValue("Location").orElseThrow();
    String state = Arrays.stream(URI.create(location).getRawQuery().split("&"))
        .filter(p -> p.startsWith("state="))
        .map(p -> p.substring("state=".length()))
        .findFirst()
        .orElseThrow();
    return "/ui/log.in/callback?client_name=gitlab&code=an-authorization-code&state=" + state;
  }

  private static FakeIdentityProvider startIdentityProvider()
  {
    try
    {
      return new FakeIdentityProvider();
    }
    catch (IOException e)
    {
      throw new UncheckedIOException(e);
    }
  }

  private static String writeConfiguration()
  {
    try
    {
      return IDENTITY_PROVIDER.writeConfiguration(Files.createTempDirectory("indirect-login"), null).toString();
    }
    catch (IOException e)
    {
      throw new UncheckedIOException(e);
    }
  }
}
