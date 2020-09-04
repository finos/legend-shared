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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.Configuration;
import java.util.List;
import java.util.Map;
import org.finos.legend.server.pac4j.LegendPac4jConfiguration;

@SuppressWarnings({"unused", "FieldMayBeFinal"})
public class StaticServerConfiguration extends Configuration
{

  private String uiPath;
  private boolean html5Router;

  private LegendPac4jConfiguration pac4j;
  private Map<String, String> localAssetPaths = ImmutableMap.of();
  private List<String> routerExemptPaths = ImmutableList.of();

  public List<String> getRouterExemptPaths()
  {
    return routerExemptPaths;
  }

  public String getUiPath()
  {
    return uiPath;
  }

  public boolean isHtml5Router()
  {
    return html5Router;
  }

  public LegendPac4jConfiguration getPac4j()
  {
    return pac4j;
  }

  public Map<String, String> getLocalAssetPaths()
  {
    return localAssetPaths;
  }
}
