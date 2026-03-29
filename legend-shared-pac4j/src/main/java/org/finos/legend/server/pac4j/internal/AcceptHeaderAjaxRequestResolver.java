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

import java.text.ParseException;
import java.util.List;

import org.glassfish.jersey.message.internal.AcceptableMediaType;
import org.glassfish.jersey.message.internal.HttpHeaderReader;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.http.ajax.DefaultAjaxRequestResolver;

import javax.ws.rs.core.MediaType;

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
    String mimeType = mimeParseBestMatch(acceptHeader);
      return Strings.isNullOrEmpty(mimeType);
  }

  private String mimeParseBestMatch(String acceptHeader)
  {
    List<AcceptableMediaType> requested;
    try
    {
      requested = HttpHeaderReader.readAcceptMediaType(acceptHeader);
      requested.sort(AcceptableMediaType.COMPARATOR);
    }
    catch (ParseException e)
    {
      return "";
    }
    MediaType supported = new MediaType("text", "html", "utf-8");
    for (AcceptableMediaType req : requested)
    {
      if (req.isCompatible(supported))
      {
        return supported.toString();
      }
    }
    return "";
  }
}
