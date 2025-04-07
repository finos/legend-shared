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

import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import java.util.Collections;
import org.commonjava.mimeparse.MIMEParse;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.http.ajax.DefaultAjaxRequestResolver;

public class AcceptHeaderAjaxRequestResolver extends DefaultAjaxRequestResolver
{
  @Override
  public boolean isAjax(WebContext context)
  {
    String acceptHeader = context.getRequestHeader(HttpHeaders.ACCEPT).orElse("");
    if (Strings.isNullOrEmpty(acceptHeader))
    {
      return true;
    }
    String mimeType = MIMEParse.bestMatch(Collections.singleton(MediaType.HTML_UTF_8.toString()),
        acceptHeader);
    return Strings.isNullOrEmpty(mimeType);
  }
}
