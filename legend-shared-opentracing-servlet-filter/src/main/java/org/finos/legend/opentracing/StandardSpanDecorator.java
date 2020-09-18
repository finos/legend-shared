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
import io.opentracing.tag.Tags;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StandardSpanDecorator implements ServerSpanDecorator
{

  @Override
  public void decorateRequest(HttpServletRequest request, Span span)
  {
    Tags.HTTP_METHOD.set(span, request.getMethod());

    String url = request.getRequestURI();
    if (url != null)
    {
      Tags.HTTP_URL.set(span, url);
    }
  }

  @Override
  public void decorateResponse(HttpServletResponse response, Span span)
  {
    Tags.HTTP_STATUS.set(span, response.getStatus());
  }
}
