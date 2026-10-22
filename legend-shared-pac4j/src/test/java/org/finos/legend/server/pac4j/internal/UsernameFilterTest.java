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

package org.finos.legend.server.pac4j.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.security.Principal;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UsernameFilterTest
{
    private static class RecordingChain implements FilterChain
    {
        boolean invoked;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
        {
            invoked = true;
        }
    }

    @Test
    public void requestWithNoPrincipalPassesStraightThrough() throws Exception
    {
        RecordingChain chain = new RecordingChain();

        new UsernameFilter().doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertTrue("an unauthenticated request must continue down the chain", chain.invoked);
    }

    /**
     * Off a container, Jetty 12's {@code ServletContextRequest.getServletContextRequest} throws
     * rather than returning null, so the exception shows the filter did attempt the lookup.
     */
    @Test
    public void requestWithAPrincipalReachesTheJettyLookup() throws IOException
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal((Principal) () -> "alice");
        RecordingChain chain = new RecordingChain();

        try
        {
            new UsernameFilter().doFilter(request, new MockHttpServletResponse(), chain);
            fail("expected the Jetty core-request lookup to be attempted");
        }
        catch (IllegalStateException expected)
        {
            assertTrue(expected.getMessage(), expected.getMessage().contains("could not find"));
        }
        catch (Exception e)
        {
            throw new AssertionError("unexpected failure from the Jetty lookup", e);
        }
    }
}
