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

package org.finos.legend.server.pac4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

public class LegendPac4JConfigurationTest
{

  @Test
  public void testDefaults() throws IOException, ConfigurationException
  {
    LegendPac4jConfiguration config = new LegendPac4jConfiguration();
    config.setDefaults("defaults.json");
    config.setMongoDb("overrideMongoDb");

    config.loadDefaults(new ResourceConfigurationSourceProvider(), new ObjectMapper());


    Assert.assertEquals("overrideMongoDb", config.getMongoDb());
    Assert.assertEquals("defaultMongoUri", config.getMongoUri());
    Assert.assertEquals("defaultMongoSession", config.getMongoSession().getCollection());

  }
}
