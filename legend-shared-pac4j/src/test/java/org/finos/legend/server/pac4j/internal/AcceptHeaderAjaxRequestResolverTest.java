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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.pac4j.core.context.CallContext;
import org.pac4j.jee.context.JEEContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@RunWith(Parameterized.class)
public class AcceptHeaderAjaxRequestResolverTest
{
  private static final boolean REDIRECT_TO_LOG_IN = false;
  private static final boolean UNAUTHORIZED = true;

  @Parameterized.Parameters(name = "{0}: Accept [{1}]")
  public static Collection<Object[]> headers()
  {
    return Arrays.asList(new Object[][]{
        {"Chrome navigation", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7", REDIRECT_TO_LOG_IN},
        {"Firefox navigation", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8", REDIRECT_TO_LOG_IN},
        {"Safari navigation", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", REDIRECT_TO_LOG_IN},
        {"html with utf-8", "text/html;charset=utf-8", REDIRECT_TO_LOG_IN},
        {"text wildcard", "text/*", REDIRECT_TO_LOG_IN},
        {"bare star", "*", REDIRECT_TO_LOG_IN},
        // */* gives text/html a quality of 1, so a fetch/axios default counts as a browser, as it did with mimeparse
        {"fetch default", "application/json, text/plain, */*", REDIRECT_TO_LOG_IN},
        {"json", "application/json", UNAUTHORIZED},
        {"plain text", "text/plain", UNAUTHORIZED},
        {"empty", "", UNAUTHORIZED},
        {"html refused", "text/html;q=0", UNAUTHORIZED},
        {"everything refused", "*/*;q=0", UNAUTHORIZED},
        {"the most specific range wins", "text/html;q=0, */*", UNAUTHORIZED},
        {"a matching charset is more specific", "text/html;charset=utf-8;q=0, text/html", UNAUTHORIZED},
        {"the first range wins a tie", "text/html;q=0, text/html", UNAUTHORIZED},
        {"an unparseable quality counts as zero", "text/html;q=abc", UNAUTHORIZED},
        {"an out-of-range quality counts as one", "text/html;q=2", REDIRECT_TO_LOG_IN},
        {"an empty quality counts as one", "text/html;q=", REDIRECT_TO_LOG_IN},
        // Matching is case-sensitive, as it was with mimeparse
        {"upper-case type", "TEXT/HTML", UNAUTHORIZED},
        {"a range without a subtype is skipped", "text, text/html", REDIRECT_TO_LOG_IN},
        {"only a range without a subtype", "text", UNAUTHORIZED},
        {"only separators", ";, ,", UNAUTHORIZED},
    });
  }

  private final String accept;
  private final boolean expectedAjax;

  public AcceptHeaderAjaxRequestResolverTest(String description, String accept, boolean expectedAjax)
  {
    this.accept = accept;
    this.expectedAjax = expectedAjax;
  }

  @Test
  public void decidesBetweenRedirectingAndUnauthorized()
  {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept", accept);

    assertEquals(expectedAjax, new AcceptHeaderAjaxRequestResolver().isAjax(callContext(request)));
  }

  @Test
  public void aRequestWithoutAnAcceptHeaderIsUnauthorized()
  {
    assertEquals(UNAUTHORIZED, new AcceptHeaderAjaxRequestResolver().isAjax(callContext(new MockHttpServletRequest())));
  }

  private static CallContext callContext(MockHttpServletRequest request)
  {
    return new CallContext(new JEEContext(request, new MockHttpServletResponse()), null);
  }
}
