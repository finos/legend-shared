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

package org.finos.legend.server.pac4j.sessionutil;

import com.google.common.base.Strings;
import java.util.UUID;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.WebContext;

public class SessionToken
{
    private final UUID sessionId;
    private final UUID sessionKey;

    private SessionToken(UUID sessionId, UUID sessionKey)
    {
        this.sessionId = sessionId;
        this.sessionKey = sessionKey;
    }

    public static SessionToken generate()
    {
        return new SessionToken(UuidUtils.newUuid(), UuidUtils.newUuid());
    }

    public static SessionToken fromContext(String cookieName, WebContext context)
    {
        String val = (String) context.getRequestAttribute(cookieName).orElse("");
        if (!Strings.isNullOrEmpty(val))
        {
            return fromTokenString(val);
        }

        Cookie cookie =
                context.getRequestCookies().stream()
                        .filter(c -> c.getName().equals(cookieName))
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

    private Cookie toCookie(String cookieName)
    {
        return new Cookie(
                cookieName,
                String.format("%s/%s", UuidUtils.toHexString(sessionId), UuidUtils.toHexString(sessionKey)));
    }

    public UUID getSessionId()
    {
        return sessionId;
    }

    public UUID getSessionKey()
    {
        return sessionKey;
    }

    public void saveInContext(String cookieName, WebContext context, int ttl)
    {
        Cookie cookie = toCookie(cookieName);
        cookie.setDomain(context.getServerName());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(ttl);
        context.addResponseCookie(cookie);
        context.setRequestAttribute(cookieName, cookie.getValue());
    }

    public void removeFromContext(String cookieName, WebContext context)
    {
        Cookie cookie = toCookie(cookieName);
        cookie.setDomain(context.getServerName());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        context.addResponseCookie(cookie);
        context.setRequestAttribute(cookieName, null);
    }
}
