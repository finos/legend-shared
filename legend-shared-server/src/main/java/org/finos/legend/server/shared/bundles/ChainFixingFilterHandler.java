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

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Priority;
import javax.servlet.FilterChain;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

@SuppressWarnings("unused")
public class ChainFixingFilterHandler extends ServletHandler
{

  private static final Logger log = Logger.getLogger(ChainFixingFilterHandler.class.getName());
  private final Map<String, Integer> priorityOverrides;
  private boolean reordered = false;

  private ChainFixingFilterHandler(
      ServletHandler servletHandler, Map<String, Integer> priorityOverrides)
  {
    this.priorityOverrides = priorityOverrides;
    this.setFilters(servletHandler.getFilters());
    this.setFilterMappings(servletHandler.getFilterMappings());
    this.setBeans(servletHandler.getBeans());
    this.setServlets(servletHandler.getServlets());
    this.setServletMappings(servletHandler.getServletMappings());
  }

  public static void apply(
      ServletContextHandler servletContextHandler, Map<String, Integer> priorityOverrides)
  {
    servletContextHandler.setServletHandler(
        new ChainFixingFilterHandler(servletContextHandler.getServletHandler(), priorityOverrides));
  }

  private void reorderFilters()
  {
    List<FilterHolder> filters = Arrays.asList(this.getFilters());
    List<FilterMapping> mappings = Arrays.asList(this.getFilterMappings());

    Map<String, Integer> priorities =
        filters.stream()
            .collect(
                Collectors.toMap(
                    FilterHolder::getName,
                    filter ->
                    {
                      Priority priority =
                          filter.getFilter().getClass().getAnnotation(Priority.class);
                      if (priority == null)
                      {
                        priority =
                            new Priority()
                            {
                              @Override
                              public Class<? extends Annotation> annotationType()
                              {
                                return null;
                              }

                              @Override
                              public int value()
                              {
                                return 0;
                              }
                            };
                      }
                      return priority.value();
                    }));

    if (priorityOverrides != null)
    {
      priorities.putAll(priorityOverrides);
    }

    mappings.sort(Comparator.comparingInt(m -> -priorities.get(m.getFilterName())));
    setFilterMappings(mappings.toArray(new FilterMapping[0]));

    log.info(
        "Reordered filters as "
            + mappings.stream()
            .map(FilterMapping::getFilterName)
            .collect(Collectors.joining(" -> ")));
  }

  @Override
  protected synchronized FilterChain getFilterChain(
      Request baseRequest, String pathInContext, ServletHolder servletHolder)
  {
    if (!reordered)
    {
      reordered = true;
      reorderFilters();
    }

    return super.getFilterChain(baseRequest, pathInContext, servletHolder);
  }
}
