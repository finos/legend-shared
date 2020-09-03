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
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import org.eclipse.jetty.servlets.CrossOriginFilter;

@SuppressWarnings("unused")
public class CorsBundleWrapper<T> implements ConfiguredBundle<T>
{

  private final ConfiguredBundle<T> authBundle;

  public CorsBundleWrapper(ConfiguredBundle<T> authBundle)
  {
    this.authBundle = authBundle;
  }

  @Override
  public void run(T config, Environment environment) throws Exception
  {
    this.authBundle.run(config, environment);

    final FilterRegistration.Dynamic cors =
        environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    cors.setInitParameter("allowedOrigins", "*");
    cors.setInitParameter(
        "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Access-Control-Allow-Credentials,"
            + "x-b3-parentspanid,x-b3-sampled,x-b3-spanid,x-b3-traceid");
    cors.setInitParameter("allowedMethods", "GET,PUT,POST,DELETE,HEAD");
    cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/*");
  }

  @Override
  public void initialize(Bootstrap<?> bootstrap)
  {
  }
}
