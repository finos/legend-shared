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

package org.finos.legend.opentracing;

import io.opentracing.Span;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EnvironmentSpanDecorator implements org.finos.legend.opentracing.ServerSpanDecorator
{

  private final String environment;

  public EnvironmentSpanDecorator(String environment)
  {

    this.environment = environment;
  }

  @Override
  public void decorateRequest(HttpServletRequest request, Span span)
  {
    span.setTag("environment", this.environment);
  }

  @Override
  public void decorateResponse(HttpServletResponse response, Span span)
  {
  }

}
