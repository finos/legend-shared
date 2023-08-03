package org.finos.legend.server.pac4j;

import org.finos.legend.server.pac4j.internal.HttpSessionStore;
import org.pac4j.core.context.J2EContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.Cookie;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SessionStoreTestUtil {

    public static void testSetCreatesCookie(HttpSessionStore store)
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        J2EContext requestContext = new J2EContext(new MockHttpServletRequest(), response);
        store.set(requestContext, "testKey", "testValue");
        Cookie[] cookies = response.getCookies();
        assertEquals(1, cookies.length);
        Cookie cookie = cookies[0];
        assertEquals("LegendSSO", cookie.getName());
        String val = cookie.getValue();
        Pattern acceptable = Pattern.compile("[0-9a-f]{15,16}-[0-9a-f]{15,16}/[0-9a-f]{15,16}-[0-9a-f]{15,16}");
        assertTrue("testing " + val, acceptable.matcher(val).matches());
    }

    public static void testMultipleSetsOnlyCreateOneCookie(HttpSessionStore store)
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        J2EContext requestContext = new J2EContext(new MockHttpServletRequest(), response);
        store.set(requestContext, "testKey", "testValue");
        store.set(requestContext, "testKey2", "testValue");
        store.set(requestContext, "testKey3", "testValue");
        store.set(requestContext, "testKey4", "testValue");
        Cookie[] cookies = response.getCookies();
        assertEquals(1, cookies.length);
    }

    public static void testSetThenGetFromSession(HttpSessionStore store)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        J2EContext requestContext = new J2EContext(request, response);
        store.set(requestContext, "testKey", "testValue");

        assertEquals("testValue", store.get(requestContext, "testKey"));

        // Copy the session to the new request
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setSession(request.getSession());
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        requestContext = new J2EContext(newRequest, newResponse);

        assertEquals("testValue", store.get(requestContext, "testKey"));
    }

    public static void testSetThenGetFromStore(HttpSessionStore store)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        J2EContext requestContext = new J2EContext(request, response);
        store.set(requestContext, "testKey", "testValue");

        assertEquals("testValue", store.get(requestContext, "testKey"));

        // Copy the cookie to the new request
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setCookies(response.getCookies());
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        requestContext = new J2EContext(newRequest, newResponse);

        assertEquals("testValue", store.get(requestContext, "testKey"));
    }

    public static void testSimulateCookieExpiryThenGetFromSession(HttpSessionStore store)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        J2EContext requestContext = new J2EContext(request, response);
        store.set(requestContext, "testKey", "testValue");

        assertEquals("testValue", store.get(requestContext, "testKey"));
        Cookie[] initialResponseCookies = response.getCookies();
        // Copy the session to the new request but not the cookie. This should force creation of a new cookie;
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setSession(request.getSession());
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        requestContext = new J2EContext(newRequest, newResponse);

        assertEquals("testValue", store.get(requestContext, "testKey"));
        Cookie[] secondaryResponseCookies = newResponse.getCookies();
        assertNotEquals(secondaryResponseCookies, initialResponseCookies);
    }
}
