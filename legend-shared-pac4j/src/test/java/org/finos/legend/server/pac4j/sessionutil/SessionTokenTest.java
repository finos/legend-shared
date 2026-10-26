// Copyright 2026 Goldman Sachs
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

import org.junit.Test;
import org.pac4j.jee.context.JEEContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SessionTokenTest
{
    private static final String NAME = "LegendSSOTest";
    private static final String A = tokenValue();
    private static final String B = tokenValue();
    private static final String C = tokenValue();

    @Test
    public void candidatesAreEveryDistinctParseableTokenInRequestOrder()
    {
        JEEContext ctx = request(new Cookie(NAME, B), new Cookie(NAME, "x"), new Cookie("Other", C),
                new Cookie(NAME, A), new Cookie(NAME, B), new Cookie(NAME, "1-2/3"));

        assertEquals(Arrays.asList(sessionId(B), sessionId(A)), sessionIds(SessionToken.candidatesFromContext(NAME, ctx)));
    }

    @Test
    public void fromContextSkipsAMalformedCookieInFront()
    {
        assertEquals(sessionId(A), SessionToken.fromContext(NAME, request(new Cookie(NAME, "x"), new Cookie(NAME, A))).getSessionId());
    }

    @Test
    public void noCookieMeansNoCandidates()
    {
        JEEContext ctx = request(new Cookie("Other", A));

        assertTrue(SessionToken.candidatesFromContext(NAME, ctx).isEmpty());
        assertNull(SessionToken.fromContext(NAME, ctx));
    }

    @Test
    public void aPinnedTokenHidesTheCookies()
    {
        JEEContext ctx = request(new Cookie(NAME, A), new Cookie(NAME, B));
        SessionToken.fromContext(NAME, request(new Cookie(NAME, C))).pinInContext(NAME, ctx);

        assertEquals(Arrays.asList(sessionId(C)), sessionIds(SessionToken.candidatesFromContext(NAME, ctx)));
        assertTrue("pinning must not write a cookie", setCookies(ctx).isEmpty());
    }

    @Test
    public void aPinnedNoSessionHidesTheCookies()
    {
        JEEContext ctx = request(new Cookie(NAME, A));
        SessionToken.pinNoSessionInContext(NAME, ctx);

        assertTrue(SessionToken.candidatesFromContext(NAME, ctx).isEmpty());
        assertNull(SessionToken.fromContext(NAME, ctx));
    }

    @Test
    public void removingTheTokenLeavesNoSessionForTheRestOfTheRequest()
    {
        JEEContext ctx = request(new Cookie(NAME, A));
        SessionToken.fromContext(NAME, ctx).removeFromContext(NAME, ctx);

        assertNull("must not fall back to the cookie just expired", SessionToken.fromContext(NAME, ctx));
    }

    @Test
    public void savingTheTokenPinsIt()
    {
        JEEContext ctx = request(new Cookie(NAME, A));
        SessionToken minted = SessionToken.generate();
        minted.saveInContext(NAME, ctx, -1);

        assertEquals(minted.getSessionId(), SessionToken.fromContext(NAME, ctx).getSessionId());
    }

    @Test
    public void aLoneCandidateIsReturnedWithoutAskingTheStore()
    {
        AtomicInteger lookups = new AtomicInteger();
        JEEContext ctx = request(new Cookie(NAME, "x"), new Cookie(NAME, A));

        SessionToken resolved = SessionToken.resolveFromContext(NAME, ctx, t -> lookups.incrementAndGet() < 0);

        assertEquals("the store keeps its own handling of a lone token it does not have", sessionId(A), resolved.getSessionId());
        assertEquals(0, lookups.get());
    }

    @Test
    public void noCandidateResolvesToNoSessionWithoutAskingTheStore()
    {
        AtomicInteger lookups = new AtomicInteger();

        assertNull(SessionToken.resolveFromContext(NAME, request(new Cookie(NAME, "x")), t -> lookups.incrementAndGet() > 0));
        assertEquals(0, lookups.get());
    }

    @Test
    public void theFirstCandidateTheStoreHasWinsAndIsPinned()
    {
        AtomicInteger lookups = new AtomicInteger();
        JEEContext ctx = request(new Cookie(NAME, A), new Cookie(NAME, B), new Cookie(NAME, C));

        SessionToken resolved = SessionToken.resolveFromContext(NAME, ctx, t ->
        {
            lookups.incrementAndGet();
            return !t.getSessionId().equals(sessionId(A));
        });

        assertEquals(sessionId(B), resolved.getSessionId());
        assertEquals("C must not be looked up once B is found", 2, lookups.get());
        assertEquals(sessionId(B), SessionToken.resolveFromContext(NAME, ctx, t -> lookups.incrementAndGet() < 0).getSessionId());
        assertEquals("resolved once per request", 2, lookups.get());
        assertTrue("resolving must not write a cookie", setCookies(ctx).isEmpty());
    }

    @Test
    public void whenTheStoreHasNoneOfSeveralCandidatesNoSessionIsPinned()
    {
        AtomicInteger lookups = new AtomicInteger();
        JEEContext ctx = request(new Cookie(NAME, A), new Cookie(NAME, B));

        assertNull(SessionToken.resolveFromContext(NAME, ctx, t -> lookups.incrementAndGet() < 0));
        assertEquals(2, lookups.get());
        assertNull(SessionToken.resolveFromContext(NAME, ctx, t -> lookups.incrementAndGet() > 0));
        assertNull(SessionToken.fromContext(NAME, ctx));
        assertEquals("resolved once per request", 2, lookups.get());
    }

    @Test
    public void severalCandidatesAreLookedUpTogetherOnce()
    {
        AtomicInteger lookups = new AtomicInteger();
        JEEContext ctx = request(new Cookie(NAME, A), new Cookie(NAME, B), new Cookie(NAME, C));

        SessionToken resolved = SessionToken.resolveFromContextByIds(NAME, ctx, candidates ->
        {
            lookups.incrementAndGet();
            assertEquals(Arrays.asList(sessionId(A), sessionId(B), sessionId(C)), sessionIds(candidates));
            return new HashSet<>(Arrays.asList(sessionId(B), sessionId(C)));
        });

        assertEquals("the first candidate, in request order, that the store has", sessionId(B), resolved.getSessionId());
        assertEquals(sessionId(B), SessionToken.resolveFromContextByIds(NAME, ctx, candidates ->
        {
            lookups.incrementAndGet();
            return new HashSet<>();
        }).getSessionId());
        assertEquals("one lookup, once per request", 1, lookups.get());
    }

    @Test
    public void aLoneCandidateIsReturnedWithoutTheLookup()
    {
        AtomicInteger lookups = new AtomicInteger();

        SessionToken resolved = SessionToken.resolveFromContextByIds(NAME, request(new Cookie(NAME, A)), candidates ->
        {
            lookups.incrementAndGet();
            return new HashSet<>();
        });

        assertEquals(sessionId(A), resolved.getSessionId());
        assertEquals(0, lookups.get());
    }

    private static JEEContext request(Cookie... cookies)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(cookies);
        return new JEEContext(request, new MockHttpServletResponse());
    }

    private static List<String> setCookies(JEEContext ctx)
    {
        return ((MockHttpServletResponse) ctx.getNativeResponse()).getHeaders("Set-Cookie");
    }

    private static String tokenValue()
    {
        return UuidUtils.toHexString(UUID.randomUUID()) + "/" + UuidUtils.toHexString(UUID.randomUUID());
    }

    private static UUID sessionId(String tokenValue)
    {
        return UuidUtils.fromHexString(tokenValue.split("/")[0]);
    }

    private static List<UUID> sessionIds(List<SessionToken> tokens)
    {
        return tokens.stream().map(SessionToken::getSessionId).collect(Collectors.toList());
    }
}
