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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Just enough of an OpenID Connect provider for the static server's GitLab client to start a login and to
 * call the token endpoint from its callback. The token endpoint always refuses, so no login completes.
 */
final class FakeIdentityProvider implements AutoCloseable
{
  private final HttpServer server;
  private final List<String> requests = new CopyOnWriteArrayList<>();

  FakeIdentityProvider() throws IOException
  {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  String url()
  {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  List<String> requests()
  {
    return requests;
  }

  boolean wasAskedForAToken()
  {
    return requests.contains("POST /token");
  }

  /**
   * A static server configuration like Studio's: the GitLab client against this provider, sessions held in
   * the servlet session, and the session cookie named {@code LEGEND_TEST_JSESSIONID}.
   */
  Path writeConfiguration(Path directory, String sessionCookieSameSite) throws IOException
  {
    return Files.writeString(directory.resolve("staticServerIndirectLoginConfig.json"), "{\n"
        + "  \"server\": {\"type\": \"simple\", \"applicationContextPath\": \"/\", \"adminContextPath\": \"/ui/admin\",\n"
        + "             \"connector\": {\"type\": \"http\", \"port\": 0}},\n"
        + "  \"uiPath\": \"/ui\",\n"
        + "  \"html5Router\": true,\n"
        + "  \"sessionCookie\": \"LEGEND_TEST_JSESSIONID\",\n"
        + (sessionCookieSameSite == null ? "" : "  \"sessionCookieSameSite\": \"" + sessionCookieSameSite + "\",\n")
        + "  \"pac4j\": {\n"
        + "    \"callbackPrefix\": \"/ui/log.in\",\n"
        + "    \"bypassPaths\": [\"/ui/admin/healthcheck\"],\n"
        + "    \"clients\": [{\"org.finos.legend.server.pac4j.gitlab.GitlabClient\": {\n"
        + "      \"name\": \"gitlab\", \"clientId\": \"studio\", \"secret\": \"secret\", \"scope\": \"openid profile api\",\n"
        + "      \"discoveryUri\": \"" + url() + "/.well-known/openid-configuration\"}}],\n"
        + "    \"mongoSession\": {\"enabled\": false}\n"
        + "  },\n"
        + "  \"logging\": {\"level\": \"WARN\", \"appenders\": [{\"type\": \"console\"}]}\n"
        + "}\n");
  }

  private void handle(HttpExchange exchange) throws IOException
  {
    String path = exchange.getRequestURI().getPath();
    requests.add(exchange.getRequestMethod() + " " + path);
    switch (path)
    {
      case "/.well-known/openid-configuration":
        respond(exchange, 200, "{"
            + "\"issuer\": \"" + url() + "\","
            + "\"authorization_endpoint\": \"" + url() + "/authorize\","
            + "\"token_endpoint\": \"" + url() + "/token\","
            + "\"jwks_uri\": \"" + url() + "/jwks\","
            + "\"response_types_supported\": [\"code\"],"
            + "\"subject_types_supported\": [\"public\"],"
            + "\"id_token_signing_alg_values_supported\": [\"RS256\"],"
            + "\"token_endpoint_auth_methods_supported\": [\"client_secret_basic\", \"client_secret_post\"]"
            + "}");
        break;
      case "/jwks":
        respond(exchange, 200, "{\"keys\": []}");
        break;
      case "/token":
        respond(exchange, 400, "{\"error\": \"invalid_grant\"}");
        break;
      default:
        respond(exchange, 404, "{}");
    }
  }

  private static void respond(HttpExchange exchange, int status, String json) throws IOException
  {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    try (OutputStream out = exchange.getResponseBody())
    {
      out.write(body);
    }
  }

  @Override
  public void close()
  {
    server.stop(0);
  }
}
