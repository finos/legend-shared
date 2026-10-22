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
import org.pac4j.jee.context.JEEContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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
}
