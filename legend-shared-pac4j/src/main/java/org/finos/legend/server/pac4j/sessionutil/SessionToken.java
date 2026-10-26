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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
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

    /** Pinned for a request with no session: unlike the empty string, it does not fall back to the cookies. */
    private static final String NO_SESSION = "none";

    /** Pinned once the request's session is destroyed: nothing is read, written or minted for the rest of it. */
    private static final String DESTROYED = "destroyed";

    private static final String ISSUED_SUFFIX = ".issued";

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

    /**
     * The first token in the request that parses, or the one pinned for it; null if none. Stores should use
     * {@link #resolveFromContext}, which picks a candidate the store has.
     */
    public static SessionToken fromContext(String cookieName, WebContext context)
    {
        List<SessionToken> candidates = candidatesFromContext(cookieName, context);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Every distinct token in the request that parses, in the order the browser sent them, or only what was
     * pinned for this request. A browser can hold several cookies of one name: a cookie set with a Domain is
     * also sent to its subdomains, so a Legend app on a parent domain adds its LegendSSO to ours.
     */
    public static List<SessionToken> candidatesFromContext(String cookieName, WebContext context)
    {
        String pinned = (String) context.getRequestAttribute(cookieName).orElse("");
        if (!Strings.isNullOrEmpty(pinned))
        {
            SessionToken token = fromTokenString(pinned);
            return token == null ? Collections.emptyList() : Collections.singletonList(token);
        }

        Map<String, SessionToken> candidates = new LinkedHashMap<>();
        for (Cookie cookie : context.getRequestCookies())
        {
            if (cookie.getName().equals(cookieName) && !Strings.isNullOrEmpty(cookie.getValue()) && !candidates.containsKey(cookie.getValue()))
            {
                SessionToken token = fromTokenString(cookie.getValue());
                if (token != null)
                {
                    candidates.put(cookie.getValue(), token);
                }
            }
        }
        return new ArrayList<>(candidates.values());
    }

    /**
     * The request's token for a store, or null for no session. A lone candidate is returned as is, and
     * the store keeps its own handling of a token it does not have. With several, the first the store
     * has wins; if it has none of them, no session is pinned: adopting or expiring one would act on a
     * cookie that may belong to another app, and an expiry carrying our Domain would delete our own cookie.
     */
    public static SessionToken resolveFromContext(String cookieName, WebContext context, Predicate<SessionToken> storeHasSession)
    {
        List<SessionToken> candidates = candidatesFromContext(cookieName, context);
        if (candidates.size() <= 1)
        {
            return candidates.isEmpty() ? null : candidates.get(0);
        }
        for (SessionToken candidate : candidates)
        {
            if (storeHasSession.test(candidate))
            {
                candidate.pinInContext(cookieName, context);
                return candidate;
            }
        }
        pinNoSessionInContext(cookieName, context);
        return null;
    }

    /**
     * {@link #resolveFromContext(String, WebContext, Predicate)} with one lookup for all the candidates:
     * {@code storedSessionIds} returns the session ids, among the candidates', that the store has.
     */
    public static SessionToken resolveFromContextByIds(String cookieName, WebContext context, Function<List<SessionToken>, Set<UUID>> storedSessionIds)
    {
        List<SessionToken> candidates = candidatesFromContext(cookieName, context);
        if (candidates.size() <= 1)
        {
            return candidates.isEmpty() ? null : candidates.get(0);
        }
        Set<UUID> stored = storedSessionIds.apply(candidates);
        for (SessionToken candidate : candidates)
        {
            if (stored.contains(candidate.getSessionId()))
            {
                candidate.pinInContext(cookieName, context);
                return candidate;
            }
        }
        pinNoSessionInContext(cookieName, context);
        return null;
    }

    /**
     * Uses this token for the rest of the request without writing a cookie.
     */
    public void pinInContext(String cookieName, WebContext context)
    {
        context.setRequestAttribute(cookieName, toValue());
    }

    /**
     * Treats the rest of the request as having no session, whatever cookies it carries, so the next
     * write mints a fresh token instead of reusing one the store does not have.
     */
    public static void pinNoSessionInContext(String cookieName, WebContext context)
    {
        context.setRequestAttribute(cookieName, NO_SESSION);
    }

    public static boolean isDestroyedInContext(String cookieName, WebContext context)
    {
        return DESTROYED.equals(context.getRequestAttribute(cookieName).orElse(null));
    }

    /**
     * Whether this request minted this token, so nobody else can have held it.
     */
    public boolean isIssuedInContext(String cookieName, WebContext context)
    {
        return toValue().equals(context.getRequestAttribute(cookieName + ISSUED_SUFFIX).orElse(null));
    }

    /**
     * Returns null for a value this class did not write (truncated, tampered or foreign cookie), the same as no
     * cookie: throwing would fail every request from that browser, including login and logout, until it expires.
     */
    private static SessionToken fromTokenString(String val)
    {
        String[] uuids = val.split("/", 2);
        if (uuids.length != 2)
        {
            return null;
        }
        try
        {
            return new SessionToken(UuidUtils.fromHexString(uuids[0]), UuidUtils.fromHexString(uuids[1]));
        }
        catch (IllegalArgumentException | IndexOutOfBoundsException e)
        {
            return null;
        }
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
        Cookie cookie = new Cookie(cookieName, toValue());
        cookie.setSameSitePolicy(sameSitePolicy);
        return cookie;
    }

    private String toValue()
    {
        return String.format("%s/%s", UuidUtils.toHexString(sessionId), UuidUtils.toHexString(sessionKey));
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

    /**
     * {@link #saveInContext} for a token this request has just minted.
     */
    public void issueInContext(String cookieName, WebContext context, int ttl, String sameSitePolicy)
    {
        saveInContext(cookieName, context, ttl, sameSitePolicy);
        context.setRequestAttribute(cookieName + ISSUED_SUFFIX, toValue());
    }

    public void removeFromContext(String cookieName, WebContext context)
    {
        removeFromContext(cookieName, context, DEFAULT_SAME_SITE_POLICY);
    }

    public void removeFromContext(String cookieName, WebContext context, String sameSitePolicy)
    {
        expireCookie(cookieName, context, sameSitePolicy);
        // Not null: that falls back to the cookie just expired, and a later write in this request would
        // go to a session the store does not have.
        pinNoSessionInContext(cookieName, context);
    }

    /**
     * Expires the cookie of a session that has been destroyed. Unlike {@link #removeFromContext}, a later
     * write in this request mints nothing: it would either go to the destroyed session or start a new one
     * in the logout response.
     */
    public void destroyInContext(String cookieName, WebContext context, String sameSitePolicy)
    {
        expireCookie(cookieName, context, sameSitePolicy);
        context.setRequestAttribute(cookieName, DESTROYED);
    }

    private void expireCookie(String cookieName, WebContext context, String sameSitePolicy)
    {
        Cookie cookie = toCookie(cookieName, sameSitePolicy);
        cookie.setDomain(context.getServerName());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        context.addResponseCookie(cookie);
    }
}
