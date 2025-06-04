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

package org.finos.legend.server.pac4j.internal;

import java.util.Arrays;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.component.LifeCycle;
import org.pac4j.jee.filter.SecurityFilter;

public abstract class SecurityFilterHandler extends ServletHandler
{

  protected SecurityFilterHandler(ServletHandler servletHandler)
  {
    this.setFilters(servletHandler.getFilters());
    this.setFilterMappings(servletHandler.getFilterMappings());
    this.setBeans(servletHandler.getBeans());
    this.setServlets(servletHandler.getServlets());
    this.setServletMappings(servletHandler.getServletMappings());
  }

  protected abstract void handleSecurityFilter(SecurityFilter filter);

  protected abstract void handleMapping(FilterMapping mapping);

  @Override
  protected synchronized void doStart() throws Exception
  {
    SecurityFilter filter =
        (SecurityFilter) this.getFilter(SecurityFilter.class.getCanonicalName()).getFilter();
    FilterMapping mapping =
        Arrays.stream(this.getFilterMappings())
            .filter(m -> m.getFilterName().equals(SecurityFilter.class.getCanonicalName()))
            .findFirst()
            .orElse(null);
    handleSecurityFilter(filter);
    handleMapping(mapping);
    super.doStart();
  }

  @Override
  protected void start(LifeCycle l) throws Exception
  {
    super.start(l);
  }

  @Override
  public void initialize() throws Exception
  {
    super.initialize();
  }
}
