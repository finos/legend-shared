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

package org.finos.legend.server.shared.bundles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import org.junit.ClassRule;
import org.junit.Test;

public class HtmlRouterRedirectBundleTest
{

  @ClassRule
  public static final DropwizardAppRule<TestConfig> RULE =
      new DropwizardAppRule<>(TestApp.class, ResourceHelpers.resourceFilePath("testConfig.json"));

  @Test
  public void testHtmlRouterRedirect()
  {
    testUrl("/ui/", 200, "TEST INDEX.HTML");
    testUrl("/ui", 200, "TEST INDEX.HTML");
    testUrl("/ui/index.html", 200, "TEST INDEX.HTML");
    testUrl("/ui/some/route", 200, "TEST INDEX.HTML");
    testUrl("/ui/some/route/with/some.periods", 200, "TEST INDEX.HTML");
    testUrl("/ui/static/blah.html", 404, null);
  }

  private void testUrl(String url, int status, String contains)
  {
    Client client = RULE.client();

    Response response =
        client
            .target(String.format("http://localhost:%d" + url, RULE.getLocalPort()))
            .request()
            .get();

    assertEquals(status, response.getStatus());
    if (contains != null)
    {
      assertTrue(response.readEntity(String.class).contains(contains));
    }
  }

  public static class TestApp extends Application<TestConfig>
  {

    @Override
    public void initialize(Bootstrap<TestConfig> bootstrap)
    {
      super.initialize(bootstrap);
      bootstrap.addBundle(new LocalAssetBundle<>("web", (c) -> ImmutableMap.of()));
      bootstrap.addBundle(
          new HtmlRouterRedirectBundle(
              "/ui", ImmutableList.of("/static", "/gs.svg"), "/ui/index.html"));
    }

    @Override
    public void run(TestConfig testConfig, Environment environment)
    {
      environment.jersey().setUrlPattern("/api/*");
    }
  }

  public static class TestConfig extends Configuration
  {
  }
}
