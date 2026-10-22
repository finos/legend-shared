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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.WebContext;

public class SessionToken
{
    /**
     * pac4j 6 writes the {@code Set-Cookie} header itself and always emits {@code SameSite},
     * so a servlet container default no longer applies. {@code Lax} is not sent on a cross-site
     * POST: {@code response_mode=form_post} or cross-site embedding needs {@code None}.
     */
    public static final String DEFAULT_SAME_SITE_POLICY = "Lax";

    private static final List<String> SAME_SITE_POLICIES = Arrays.asList("Strict", "Lax", "None");

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

    /**
     * pac4j silently turns an unrecognised policy into {@code Lax}, so a typo is rejected here instead.
     */
    public static String sameSitePolicy(String configured)
    {
        if (Strings.isNullOrEmpty(configured))
        {
            return DEFAULT_SAME_SITE_POLICY;
        }
        return SAME_SITE_POLICIES.stream()
                .filter(policy -> policy.equalsIgnoreCase(configured.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported SameSite policy for the session token cookie: '" + configured + "'. Expected one of " + SAME_SITE_POLICIES));
    }

    private Cookie toCookie(String cookieName, String sameSitePolicy)
    {
        Cookie cookie = new Cookie(
                cookieName,
                String.format("%s/%s", UuidUtils.toHexString(sessionId), UuidUtils.toHexString(sessionKey)));
        cookie.setSameSitePolicy(sameSitePolicy);
        return cookie;
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
        saveInContext(cookieName, context, ttl, DEFAULT_SAME_SITE_POLICY);
    }

    public void saveInContext(String cookieName, WebContext context, int ttl, String sameSitePolicy)
    {
        Cookie cookie = toCookie(cookieName, sameSitePolicy);
        cookie.setDomain(context.getServerName());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(ttl);
        context.addResponseCookie(cookie);
        context.setRequestAttribute(cookieName, cookie.getValue());
    }

    public void removeFromContext(String cookieName, WebContext context)
    {
        removeFromContext(cookieName, context, DEFAULT_SAME_SITE_POLICY);
    }

    public void removeFromContext(String cookieName, WebContext context, String sameSitePolicy)
    {
        Cookie cookie = toCookie(cookieName, sameSitePolicy);
        cookie.setDomain(context.getServerName());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        context.addResponseCookie(cookie);
        context.setRequestAttribute(cookieName, null);
    }
}
