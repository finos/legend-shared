// Copyright 2023 Goldman Sachs
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

package org.finos.legend.server.pac4j;

import org.finos.legend.server.pac4j.internal.HttpSessionStore;
import org.finos.legend.server.pac4j.sessionutil.SessionToken;
import org.finos.legend.server.pac4j.sessionutil.UuidUtils;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SessionStoreTestUtil {

    /**
     * pac4j writes a raw Set-Cookie header rather than calling HttpServletResponse.addCookie,
     * so MockHttpServletResponse.getCookies() does not see the cookie.
     */
    public static Cookie[] responseCookies(MockHttpServletResponse response)
    {
        List<Cookie> cookies = new ArrayList<>();
        for (String header : response.getHeaders("Set-Cookie"))
        {
            String[] attributes = header.split(";");
            String[] nameAndValue = attributes[0].split("=", 2);
            Cookie cookie = new Cookie(nameAndValue[0].trim(), nameAndValue.length > 1 ? nameAndValue[1].trim() : "");
            for (int i = 1; i < attributes.length; i++)
            {
                String[] attribute = attributes[i].split("=", 2);
                if ("Max-Age".equalsIgnoreCase(attribute[0].trim()) && attribute.length > 1)
                {
                    cookie.setMaxAge(Integer.parseInt(attribute[1].trim()));
                }
            }
            cookies.add(cookie);
        }
        return cookies.toArray(new Cookie[0]);
    }

    public static void testSetCreatesCookie(HttpSessionStore store)
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(new MockHttpServletRequest(), response);
        store.set(requestContext, "testKey", "testValue");
        Cookie[] cookies = responseCookies(response);
        assertEquals(1, cookies.length);
        Cookie cookie = cookies[0];
        assertEquals("LegendSSOTest", cookie.getName());
        String val = cookie.getValue();
        // UuidUtils.toHexString drops leading zeros, so each half can be as short as one hex char.
        Pattern acceptable = Pattern.compile("[0-9a-f]{1,16}-[0-9a-f]{1,16}/[0-9a-f]{1,16}-[0-9a-f]{1,16}");
        assertTrue("testing " + val, acceptable.matcher(val).matches());
    }

    public static void testMultipleSetsOnlyCreateOneCookie(HttpSessionStore store)
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(new MockHttpServletRequest(), response);
        store.set(requestContext, "testKey", "testValue");
        store.set(requestContext, "testKey2", "testValue");
        store.set(requestContext, "testKey3", "testValue");
        store.set(requestContext, "testKey4", "testValue");
        Cookie[] cookies = responseCookies(response);
        assertEquals(1, cookies.length);
    }

    public static void testSetThenGetFromSession(HttpSessionStore store)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(request, response);
        store.set(requestContext, "testKey", "testValue");

        assertEquals("testValue", store.get(requestContext, "testKey").get());

        // Copy the session to the new request
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setSession(request.getSession());
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        requestContext = new JEEContext(newRequest, newResponse);

        assertEquals("testValue", store.get(requestContext, "testKey").get());
    }

    public static void testSetThenGetFromStore(HttpSessionStore store)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(request, response);
        store.set(requestContext, "testKey", "testValue");

        assertEquals("testValue", store.get(requestContext, "testKey").get());

        // Copy the cookie to the new request
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setCookies(responseCookies(response));
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        requestContext = new JEEContext(newRequest, newResponse);

        assertEquals("testValue", store.get(requestContext, "testKey").get());
    }

    public static void testSimulateCookieExpiryThenGetFromSession(HttpSessionStore store)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext requestContext = new JEEContext(request, response);
        store.set(requestContext, "testKey", "testValue");

        assertEquals("testValue", store.get(requestContext, "testKey").get());
        Cookie[] initialResponseCookies = responseCookies(response);
        // Copy the session to the new request but not the cookie. This should force creation of a new cookie;
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setSession(request.getSession());
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        requestContext = new JEEContext(newRequest, newResponse);

        assertEquals("testValue", store.get(requestContext, "testKey").get());
        Cookie[] secondaryResponseCookies = responseCookies(newResponse);
        assertNotEquals(secondaryResponseCookies, initialResponseCookies);
    }

    public static void testMalformedTokenCookieIsNoSession(HttpSessionStore store)
    {
        for (String value : new String[]{"x", "a-b", "zz-zz/1-2", "1-2/", "1-2/3"})
        {
            assertNull(value, SessionToken.fromContext("LegendSSOTest", malformedCookieRequest(value, new MockHttpServletResponse())));
            assertFalse(value, store.get(malformedCookieRequest(value, new MockHttpServletResponse()), Pac4jConstants.USER_PROFILES).isPresent());

            MockHttpServletResponse logoutResponse = new MockHttpServletResponse();
            store.destroySession(malformedCookieRequest(value, logoutResponse));
            assertEquals(value, 0, logoutResponse.getHeaders("Set-Cookie").size());

            MockHttpServletResponse loginResponse = new MockHttpServletResponse();
            store.set(malformedCookieRequest(value, loginResponse), "testKey", "testValue");
            Cookie[] cookies = responseCookies(loginResponse);
            assertEquals(value, 1, cookies.length);
            assertEquals(value, "LegendSSOTest", cookies[0].getName());
            assertNotEquals(value, value, cookies[0].getValue());
        }
    }

    private static JEEContext malformedCookieRequest(String value, MockHttpServletResponse response)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("LegendSSOTest", value));
        return new JEEContext(request, response);
    }

    public static void testOwnSessionIsFoundBehindOtherTokens(HttpSessionStore store, Predicate<String> storeHasSession)
    {
        String own = login(store, "testValue");
        String foreign = foreignTokenValue();
        String otherForeign = foreignTokenValue();
        List<List<String>> arrangements = Arrays.asList(
                Arrays.asList("x", own),
                Arrays.asList(own, "x"),
                Arrays.asList(foreign, own),
                Arrays.asList(own, foreign),
                Arrays.asList(foreign, "x", otherForeign, own));
        for (List<String> cookies : arrangements)
        {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertEquals(cookies.toString(), "testValue", store.get(requestWith(response, cookies), "testKey").orElse(null));
            assertEquals(cookies + ": reading must not expire or replace a cookie", 0, response.getHeaders("Set-Cookie").size());
        }
        assertFalse("a foreign token must not be adopted", storeHasSession.test(foreign));
        assertFalse("a foreign token must not be adopted", storeHasSession.test(otherForeign));
    }

    public static void testNoneOfSeveralTokensIsNoSession(HttpSessionStore store, Predicate<String> storeHasSession)
    {
        String foreign = foreignTokenValue();
        String otherForeign = foreignTokenValue();
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext context = requestWith(response, Arrays.asList(foreign, otherForeign));

        assertFalse(store.get(context, "testKey").isPresent());
        assertEquals("an expiry could only hit our own cookie, not the foreign ones", 0, response.getHeaders("Set-Cookie").size());
        assertFalse(storeHasSession.test(foreign));
        assertFalse(storeHasSession.test(otherForeign));

        store.set(context, "testKey", "testValue");
        Cookie[] cookies = responseCookies(response);
        assertEquals(1, cookies.length);
        assertNotEquals(foreign, cookies[0].getValue());
        assertNotEquals(otherForeign, cookies[0].getValue());
        assertTrue(storeHasSession.test(cookies[0].getValue()));
        assertFalse(storeHasSession.test(foreign));
        assertFalse(storeHasSession.test(otherForeign));
        assertEquals("testValue", store.get(requestWith(new MockHttpServletResponse(), Arrays.asList(cookies[0].getValue())), "testKey").orElse(null));
    }

    public static void testWritesGoToTheOwnSessionBehindAForeignToken(HttpSessionStore store, Predicate<String> storeHasSession)
    {
        String own = login(store, "testValue");
        String foreign = foreignTokenValue();
        MockHttpServletResponse response = new MockHttpServletResponse();

        store.set(requestWith(response, Arrays.asList(foreign, own)), "testKey", "newValue");

        assertEquals("the own session exists, so no new cookie", 0, response.getHeaders("Set-Cookie").size());
        assertEquals("newValue", store.get(requestWith(new MockHttpServletResponse(), Arrays.asList(own)), "testKey").orElse(null));
        assertFalse(storeHasSession.test(foreign));
    }

    public static void testLogoutDestroysTheOwnSessionBehindAForeignToken(HttpSessionStore store, Predicate<String> storeHasSession)
    {
        String own = login(store, "testValue");
        String foreign = foreignTokenValue();
        MockHttpServletResponse response = new MockHttpServletResponse();

        store.destroySession(requestWith(response, Arrays.asList(foreign, own)));

        assertFalse(storeHasSession.test(own));
        Cookie[] cookies = responseCookies(response);
        assertEquals(1, cookies.length);
        assertEquals("the own cookie is the one expired", own, cookies[0].getValue());
        assertEquals(0, cookies[0].getMaxAge());
    }

    public static void testTheFirstLiveSessionWins(HttpSessionStore store)
    {
        String first = login(store, "first");
        String second = login(store, "second");

        assertEquals("first", store.get(requestWith(new MockHttpServletResponse(), Arrays.asList(first, second)), "testKey").orElse(null));
        assertEquals("second", store.get(requestWith(new MockHttpServletResponse(), Arrays.asList(second, first)), "testKey").orElse(null));
    }

    public static void testSignInRotatesAPreSignInToken(HttpSessionStore store)
    {
        MockHttpServletResponse anonymousResponse = new MockHttpServletResponse();
        store.set(new JEEContext(new MockHttpServletRequest(), anonymousResponse), Pac4jConstants.REQUESTED_URL, "/requested");
        String preSignIn = responseCookies(anonymousResponse)[0].getValue();

        MockHttpServletResponse signInResponse = new MockHttpServletResponse();
        signIn(requestWith(signInResponse, Arrays.asList(preSignIn)), store, false, profile("A"));

        Cookie[] cookies = responseCookies(signInResponse);
        assertEquals(1, cookies.length);
        String signedIn = cookies[0].getValue();
        assertNotEquals("sign-in must issue a new token", preSignIn, signedIn);
        assertTrue(profiles(store, preSignIn).isEmpty());
        assertNull("the pre-sign-in token carries no data",
                store.get(requestWith(new MockHttpServletResponse(), Arrays.asList(preSignIn)), Pac4jConstants.REQUESTED_URL).orElse(null));
        assertEquals("alice", profiles(store, signedIn).get(0).getId());
        assertEquals("the session's data moves to the new token", "/requested",
                store.get(requestWith(new MockHttpServletResponse(), Arrays.asList(signedIn)), Pac4jConstants.REQUESTED_URL).orElse(null));
    }

    public static void testSignInRotatesAChosenToken(HttpSessionStore store)
    {
        String chosen = foreignTokenValue();
        JEEContext victim = requestWith(new MockHttpServletResponse(), Arrays.asList(chosen));

        store.get(victim, Pac4jConstants.USER_PROFILES);
        signIn(victim, store, false, profile("A"));

        assertTrue(profiles(store, chosen).isEmpty());
    }

    public static void testATokenMintedBySigningInIsNotRotated(HttpSessionStore store)
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        signIn(new JEEContext(new MockHttpServletRequest(), response), store, true, profile("A"), profile("B"));

        assertEquals(1, responseCookies(response).length);
        assertEquals(2, profiles(store, responseCookies(response)[0].getValue()).size());
    }

    public static void testEverySignInRotates(HttpSessionStore store)
    {
        MockHttpServletResponse first = new MockHttpServletResponse();
        signIn(new JEEContext(new MockHttpServletRequest(), first), store, true, profile("A"));
        String token = responseCookies(first)[0].getValue();

        MockHttpServletResponse second = new MockHttpServletResponse();
        signIn(requestWith(second, Arrays.asList(token)), store, true, profile("B"));

        Cookie[] cookies = responseCookies(second);
        assertEquals(1, cookies.length);
        assertNotEquals(token, cookies[0].getValue());
        assertEquals(2, profiles(store, cookies[0].getValue()).size());
        assertTrue(profiles(store, token).isEmpty());
    }

    public static void testAProfileWriteOutsideASignInKeepsTheToken(HttpSessionStore store)
    {
        MockHttpServletResponse anonymousResponse = new MockHttpServletResponse();
        store.set(new JEEContext(new MockHttpServletRequest(), anonymousResponse), Pac4jConstants.REQUESTED_URL, "/requested");
        String token = responseCookies(anonymousResponse)[0].getValue();

        MockHttpServletResponse response = new MockHttpServletResponse();
        new ProfileManager(requestWith(response, Arrays.asList(token)), store).save(true, profile("A"), false);

        assertEquals(0, response.getHeaders("Set-Cookie").size());
        assertEquals("alice", profiles(store, token).get(0).getId());
    }

    public static void signIn(JEEContext context, HttpSessionStore store, boolean multiProfile, UserProfile... profiles)
    {
        ProfileManager manager = new StorageDecisionProfileManager(context, store, new LegendUserProfileStorageDecision());
        for (UserProfile profile : profiles)
        {
            manager.save(true, profile, multiProfile);
        }
    }

    public static void testNothingIsWrittenAfterLogout(HttpSessionStore store, Predicate<String> storeHasSession)
    {
        String token = login(store, "before");
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext ctx = requestWith(response, Arrays.asList(token));

        store.destroySession(ctx);
        store.set(ctx, Pac4jConstants.REQUESTED_URL, "after");
        store.getSessionId(ctx, true);

        assertFalse("the destroyed session is not recreated", storeHasSession.test(token));
        Cookie[] cookies = responseCookies(response);
        assertEquals("only the expired cookie", 1, cookies.length);
        assertEquals(0, cookies[0].getMaxAge());
    }

    public static void testRemoveProfilesClearsASessionReachedByItsCookieAlone(HttpSessionStore store)
    {
        MockHttpServletResponse signIn = new MockHttpServletResponse();
        new ProfileManager(new JEEContext(new MockHttpServletRequest(), signIn), store).save(true, profile("A"), false);
        String token = responseCookies(signIn)[0].getValue();

        JEEContext otherNode = requestWith(new MockHttpServletResponse(), Arrays.asList(token));
        assertTrue(store.getSessionId(otherNode, false).isPresent());
        new ProfileManager(otherNode, store).removeProfiles();

        assertTrue(profiles(store, token).isEmpty());
    }

    public static void testTheSessionIdOfASessionReachedByItsCookieAloneCreatesNothing(HttpSessionStore store)
    {
        String token = login(store, "value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext otherNode = requestWith(response, Arrays.asList(token));

        assertTrue(store.getSessionId(otherNode, false).isPresent());
        assertNull(otherNode.getNativeRequest().getSession(false));
        assertEquals(0, response.getHeaders("Set-Cookie").size());
    }

    public static void testNoSessionIdWithoutAStoredSession(HttpSessionStore store)
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JEEContext ctx = requestWith(response, Arrays.asList(foreignTokenValue()));

        assertFalse(store.getSessionId(ctx, false).isPresent());
        assertEquals(0, response.getHeaders("Set-Cookie").size());
        assertNull(ctx.getNativeRequest().getSession(false));
    }

    private static CommonProfile profile(String clientName)
    {
        CommonProfile profile = new CommonProfile();
        profile.setId("alice");
        profile.setClientName(clientName);
        return profile;
    }

    public static List<UserProfile> profiles(HttpSessionStore store, String token)
    {
        return new ProfileManager(requestWith(new MockHttpServletResponse(), Arrays.asList(token)), store).getProfiles();
    }

    /** Returns the SSO cookie's value. */
    public static String login(HttpSessionStore store, String value)
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        store.set(new JEEContext(new MockHttpServletRequest(), response), "testKey", value);
        return responseCookies(response)[0].getValue();
    }

    public static String foreignTokenValue()
    {
        return UuidUtils.toHexString(UUID.randomUUID()) + "/" + UuidUtils.toHexString(UUID.randomUUID());
    }

    public static JEEContext requestWith(MockHttpServletResponse response, List<String> ssoCookieValues)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/thing");
        request.setCookies(ssoCookieValues.stream().map(v -> new Cookie("LegendSSOTest", v)).toArray(Cookie[]::new));
        request.addHeader("User-Agent", "curl/8.0");
        return new JEEContext(request, response);
    }
}
