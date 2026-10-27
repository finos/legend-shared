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

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import java.util.List;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.http.ajax.DefaultAjaxRequestResolver;

/**
 * A request is treated as a browser navigation, and redirected to log in, when its Accept header gives
 * {@code text/html; charset=utf-8} a non-zero quality; anything else gets a 401.
 *
 * <p>The header is weighed exactly as the mimeparse library weighed it when this class called it: the most
 * specific matching range wins (type, then subtype, then a {@code charset=utf-8} parameter), the first on a
 * tie, and an unparseable {@code q} counts as zero. mimeparse is not used because it needs commons-lang 2,
 * which has no release fixing CVE-2025-48924. The one difference is that a range without a subtype is
 * skipped, where mimeparse threw.
 */
public class AcceptHeaderAjaxRequestResolver extends DefaultAjaxRequestResolver
{
  private static final Splitter RANGES = Splitter.on(',').omitEmptyStrings();
  private static final Splitter PARAMETERS = Splitter.on(';').omitEmptyStrings();
  private static final Splitter NAME_AND_VALUE = Splitter.on('=').omitEmptyStrings();
  private static final Splitter TYPE_AND_SUBTYPE = Splitter.on('/').omitEmptyStrings();

  @Override
  public boolean isAjax(CallContext ctx)
  {
    String acceptHeader = ctx.webContext().getRequestHeader(HttpHeaders.ACCEPT).orElse("");
    return Strings.isNullOrEmpty(acceptHeader) || Float.compare(htmlQuality(acceptHeader), 0) == 0;
  }

  static float htmlQuality(String acceptHeader)
  {
    int bestFitness = -1;
    float bestQuality = 0;
    for (String range : RANGES.split(acceptHeader))
    {
      List<String> parameters = PARAMETERS.splitToList(range);
      if (parameters.isEmpty())
      {
        continue;
      }
      String fullType = parameters.get(0).trim();
      List<String> typeAndSubtype = TYPE_AND_SUBTYPE.splitToList("*".equals(fullType) ? "*/*" : fullType);
      if (typeAndSubtype.size() < 2)
      {
        continue;
      }
      String type = typeAndSubtype.get(0).trim();
      String subtype = typeAndSubtype.get(1).trim();
      if (!(type.equals("text") || type.equals("*")) || !(subtype.equals("html") || subtype.equals("*")))
      {
        continue;
      }
      String charset = null;
      String quality = null;
      for (String parameter : parameters.subList(1, parameters.size()))
      {
        List<String> nameAndValue = NAME_AND_VALUE.splitToList(parameter);
        if (nameAndValue.size() == 2)
        {
          String name = nameAndValue.get(0).trim();
          if (name.equals("charset"))
          {
            charset = nameAndValue.get(1).trim();
          }
          else if (name.equals("q"))
          {
            quality = nameAndValue.get(1).trim();
          }
        }
      }
      int fitness = (type.equals("text") ? 100 : 0) + (subtype.equals("html") ? 10 : 0) + ("utf-8".equals(charset) ? 1 : 0);
      if (fitness > bestFitness)
      {
        bestFitness = fitness;
        bestQuality = quality(quality);
      }
    }
    return bestQuality;
  }

  private static float quality(String q)
  {
    float lenient = parse(q, 1);
    if (q == null || q.chars().allMatch(Character::isWhitespace) || lenient < 0 || lenient > 1)
    {
      return 1;
    }
    return parse(q, 0);
  }

  private static float parse(String value, float otherwise)
  {
    if (value == null)
    {
      return otherwise;
    }
    try
    {
      return Float.parseFloat(value);
    }
    catch (NumberFormatException e)
    {
      return otherwise;
    }
  }
}
