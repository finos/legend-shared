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

package org.finos.legend.server.pac4j;

import org.pac4j.jee.context.JEEContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The cookies a browser holds and sends, as RFC 6265 has it: a cookie set with a Domain goes to that host and its
 * subdomains, one set without only to the host that set it, and the two are different cookies even when the Domain
 * is that host. A Set-Cookie replaces the cookie with the same name, domain and host-only flag, and Max-Age=0
 * deletes it. As in Chrome, a replaced cookie with a new value goes to the back of the Cookie header, which lists
 * cookies oldest first. Paths are not modelled: the session cookie's is always /.
 */
public final class BrowserCookieJar
{
    private final List<StoredCookie> cookies = new ArrayList<>();

    /** A request to the host, carrying the cookies the browser sends it, on a node holding no HttpSession. */
    public JEEContext request(String host, MockHttpServletResponse response)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/thing");
        request.setServerName(host);
        request.addHeader("User-Agent", "curl/8.0");
        Cookie[] sent = this.cookies.stream()
                .filter(cookie -> cookie.isSentTo(host))
                .map(cookie -> new Cookie(cookie.name, cookie.value))
                .toArray(Cookie[]::new);
        if (sent.length > 0)
        {
            request.setCookies(sent);
        }
        return new JEEContext(request, response);
    }

    /** Applies the response's Set-Cookie headers, as received from the host. */
    public void receive(String host, MockHttpServletResponse response)
    {
        for (String header : response.getHeaders("Set-Cookie"))
        {
            receive(host, header);
        }
    }

    /** Adds a cookie set with this Domain by an earlier response. */
    public void addDomainCookie(String name, String value, String domain)
    {
        this.cookies.add(new StoredCookie(name, value, domain.toLowerCase(Locale.ROOT), false));
    }

    /** The values of the cookies with this name that the browser sends the host, in the order it sends them. */
    public List<String> valuesSentTo(String host, String name)
    {
        List<String> values = new ArrayList<>();
        this.cookies.stream()
                .filter(cookie -> cookie.name.equals(name) && cookie.isSentTo(host))
                .forEach(cookie -> values.add(cookie.value));
        return values;
    }

    private void receive(String host, String header)
    {
        String[] attributes = header.split(";");
        String[] nameAndValue = attributes[0].split("=", 2);
        String name = nameAndValue[0].trim();
        String value = nameAndValue.length > 1 ? nameAndValue[1].trim() : "";
        String domain = null;
        boolean expired = false;
        for (int i = 1; i < attributes.length; i++)
        {
            String[] attribute = attributes[i].split("=", 2);
            String key = attribute[0].trim();
            String argument = attribute.length > 1 ? attribute[1].trim() : "";
            if ("Domain".equalsIgnoreCase(key) && !argument.isEmpty())
            {
                domain = (argument.startsWith(".") ? argument.substring(1) : argument).toLowerCase(Locale.ROOT);
            }
            else if ("Max-Age".equalsIgnoreCase(key))
            {
                expired = Integer.parseInt(argument) <= 0;
            }
        }
        if (domain != null && !domainMatches(host, domain))
        {
            // A browser ignores a cookie whose Domain does not cover the host that set it.
            return;
        }
        StoredCookie received = new StoredCookie(name, value, domain == null ? host.toLowerCase(Locale.ROOT) : domain, domain == null);
        StoredCookie existing = this.cookies.stream().filter(received::isSameCookieAs).findFirst().orElse(null);
        if (expired)
        {
            this.cookies.remove(existing);
        }
        else if (existing == null || !existing.value.equals(value))
        {
            this.cookies.remove(existing);
            this.cookies.add(received);
        }
    }

    private static boolean domainMatches(String host, String domain)
    {
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals(domain) || h.endsWith("." + domain);
    }

    private static final class StoredCookie
    {
        private final String name;
        private final String value;
        private final String domain;
        private final boolean hostOnly;

        private StoredCookie(String name, String value, String domain, boolean hostOnly)
        {
            this.name = name;
            this.value = value;
            this.domain = domain;
            this.hostOnly = hostOnly;
        }

        private boolean isSentTo(String host)
        {
            return this.hostOnly ? this.domain.equals(host.toLowerCase(Locale.ROOT)) : domainMatches(host, this.domain);
        }

        private boolean isSameCookieAs(StoredCookie other)
        {
            return this.name.equals(other.name) && this.domain.equals(other.domain) && this.hostOnly == other.hostOnly;
        }

        @Override
        public String toString()
        {
            return this.name + "=" + this.value + (this.hostOnly ? " (host-only " : " (Domain=") + this.domain + ")";
        }
    }
}
