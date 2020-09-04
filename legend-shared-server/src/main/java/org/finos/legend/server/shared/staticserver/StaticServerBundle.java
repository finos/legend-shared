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

package org.finos.legend.server.shared.staticserver;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.util.List;
import java.util.function.Function;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.finos.legend.server.pac4j.LegendPac4jBundle;
import org.finos.legend.server.shared.bundles.HostnameHeaderBundle;
import org.finos.legend.server.shared.bundles.HtmlRouterRedirectBundle;
import org.finos.legend.server.shared.bundles.LocalAssetBundle;
import org.finos.legend.server.shared.bundles.OpenTracingBundle;

@SuppressWarnings("unused")
public class StaticServerBundle<C extends Configuration> implements ConfiguredBundle<C>
{
  private final String staticPath;
  private final Function<C, org.finos.legend.server.shared.staticserver.StaticServerConfiguration> configSupplier;

  public StaticServerBundle(String staticPath)
  {
    this(staticPath, c -> (org.finos.legend.server.shared.staticserver.StaticServerConfiguration) c);
  }

  public StaticServerBundle(
      String staticPath,
      Function<C, org.finos.legend.server.shared.staticserver.StaticServerConfiguration> configSupplier)
  {
    this.staticPath = staticPath;
    this.configSupplier = configSupplier;
  }

  private String getStaticPath(org.finos.legend.server.shared.staticserver.StaticServerConfiguration config)
  {
    if (!Strings.isNullOrEmpty(this.staticPath))
    {
      return this.staticPath;
    }
    return config.getUiPath();
  }

  @Override
  public void run(C config, Environment environment)
  {
    ErrorPageErrorHandler eph = new ErrorPageErrorHandler();
    eph.addErrorPage(403, "/auth/forbidden");
    eph.addErrorPage(404, "/auth/notfound");
    environment.getApplicationContext().setErrorHandler(eph);

    org.finos.legend.server.shared.staticserver.StaticServerConfiguration staticConfig = configSupplier.apply(config);
    String staticPath = getStaticPath(staticConfig);
    environment.jersey().setUrlPattern(staticPath + "/api/*");
    if (staticConfig.isHtml5Router())
    {
      List<String> skipPaths = Lists.newArrayList("/api", "/auth", "/static");
      if (staticConfig.getRouterExemptPaths() != null)
      {
        skipPaths.addAll(staticConfig.getRouterExemptPaths());
      }
      new HtmlRouterRedirectBundle(staticPath, skipPaths, staticPath + "/index.html")
          .run(environment);
    }
    environment.healthChecks().register("Static", new org.finos.legend.server.shared.staticserver.StaticHealthcheck());
  }

  @SuppressWarnings("unchecked")
  @Override
  public void initialize(Bootstrap<?> bootstrap)
  {
    bootstrap.addBundle(new HostnameHeaderBundle());
    bootstrap.addBundle(new LegendPac4jBundle<>(c -> configSupplier.apply((C) c).getPac4j()));
    bootstrap.addBundle(new OpenTracingBundle());
    bootstrap.addBundle(
        new LocalAssetBundle<>("web", c -> configSupplier.apply((C) c).getLocalAssetPaths()));
  }
}
