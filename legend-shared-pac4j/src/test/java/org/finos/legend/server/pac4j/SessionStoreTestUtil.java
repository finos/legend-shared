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
import org.pac4j.core.context.JEEContext;
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
        JEEContext requestContext = new JEEContext(new MockHttpServletRequest(), response);
        store.set(requestContext, "testKey", "testValue");
        Cookie[] cookies = response.getCookies();
        assertEquals(1, cookies.length);
        Cookie cookie = cookies[0];
        assertEquals("LegendSSOTest", cookie.getName());
        String val = cookie.getValue();
        Pattern acceptable = Pattern.compile("[0-9a-f]{15,16}-[0-9a-f]{15,16}/[0-9a-f]{15,16}-[0-9a-f]{15,16}");
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
        Cookie[] cookies = response.getCookies();
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
        newRequest.setCookies(response.getCookies());
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
        Cookie[] initialResponseCookies = response.getCookies();
        // Copy the session to the new request but not the cookie. This should force creation of a new cookie;
        MockHttpServletRequest newRequest = new MockHttpServletRequest();
        newRequest.setSession(request.getSession());
        MockHttpServletResponse newResponse = new MockHttpServletResponse();
        requestContext = new JEEContext(newRequest, newResponse);

        assertEquals("testValue", store.get(requestContext, "testKey").get());
        Cookie[] secondaryResponseCookies = newResponse.getCookies();
        assertNotEquals(secondaryResponseCookies, initialResponseCookies);
    }
}
