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

package org.finos.legend.server.pac4j.mongostore;

import com.google.common.base.Strings;
import java.util.UUID;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.WebContext;

class SessionToken
{
  private static final String SESSION_COOKIE_NAME = "LegendSSO";

  private final UUID sessionId;
  private final UUID sessionKey;

  private SessionToken(UUID sessionId, UUID sessionKey)
  {
    this.sessionId = sessionId;
    this.sessionKey = sessionKey;
  }

  static SessionToken generate()
  {
    return new SessionToken(UuidUtils.newUuid(), UuidUtils.newUuid());
  }

  static SessionToken fromContext(WebContext context)
  {
    String val = (String) context.getRequestAttribute(SESSION_COOKIE_NAME);
    if (!Strings.isNullOrEmpty(val))
    {
      return fromTokenString(val);
    }

    Cookie cookie =
        context.getRequestCookies().stream()
            .filter(c -> c.getName().equals(SESSION_COOKIE_NAME))
            .findFirst()
            .orElse(null);
    if (cookie != null && cookie.getValue() != null && !"".equals(cookie.getValue()))
    {
      return fromTokenString(cookie.getValue());
    }
    return null;
  }

  private static SessionToken fromTokenString(String val)
  {
    String[] uuids = val.split("/", 2);
    return new SessionToken(UuidUtils.fromHexString(uuids[0]), UuidUtils.fromHexString(uuids[1]));
  }

  private Cookie toCookie()
  {
    return new Cookie(
        SESSION_COOKIE_NAME,
        String.format(
            "%s/%s", UuidUtils.toHexString(sessionId), UuidUtils.toHexString(sessionKey)));
  }

  UUID getSessionId()
  {
    return sessionId;
  }

  UUID getSessionKey()
  {
    return sessionKey;
  }

  void saveInContext(WebContext context, int ttl)
  {
    Cookie cookie = toCookie();
    cookie.setDomain(context.getServerName());
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setMaxAge(ttl);
    context.addResponseCookie(cookie);
    context.setRequestAttribute(SESSION_COOKIE_NAME, cookie.getValue());
  }
}
