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

import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import org.finos.legend.server.shared.localassets.LocalAssetServlet;

public class LocalAssetBundle<C> implements ConfiguredBundle<C>
{

  private final String resourcePath;
  private final Function<C, Map<String, String>> getLocalAssetPaths;

  public LocalAssetBundle(String resourcePath,
                          Function<C, Map<String, String>> getLocalAssetPaths)
  {
    this.resourcePath = resourcePath;
    this.getLocalAssetPaths = getLocalAssetPaths;
  }

  @Override
  public void run(C config, Environment environment)
  {
    LocalAssetServlet servlet = new LocalAssetServlet(getLocalAssetPaths.apply(config),
        resourcePath, "/", "index.html", StandardCharsets.UTF_8);
    environment.servlets().addServlet("assets", servlet).addMapping("/*");
  }

  @Override
  public void initialize(Bootstrap<?> bootstrap)
  {
  }
}