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

package org.finos.legend.server.shared.bundles;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletContextListener;
import java.util.Collections;
import org.eclipse.jetty.ee10.servlet.ListenerHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.junit.jupiter.api.Test;

public class ChainFixingFilterHandlerTest
{
  @Test
  public void decodingAmbiguousUrisSurvivesTheHandlerReplacement()
  {
    ServletContextHandler context = new ServletContextHandler();
    context.getServletHandler().setDecodeAmbiguousURIs(true);

    ChainFixingFilterHandler.apply(context, Collections.emptyMap());

    assertInstanceOf(ChainFixingFilterHandler.class, context.getServletHandler());
    assertTrue(context.getServletHandler().isDecodeAmbiguousURIs());
  }

  @Test
  public void theOtherHandlerSettingsSurviveTheHandlerReplacement()
  {
    ServletContextHandler context = new ServletContextHandler();
    ServletHandler original = context.getServletHandler();
    original.setEnsureDefaultServlet(false);
    original.setStartWithUnavailable(true);
    original.setAllowDuplicateMappings(true);
    original.setFilterChainsCached(false);
    original.setMaxFilterChainsCacheSize(7);
    ListenerHolder listener = new ListenerHolder();
    listener.setListener(new ServletContextListener()
    {
    });
    original.setListeners(new ListenerHolder[]{listener});

    ChainFixingFilterHandler.apply(context, Collections.emptyMap());

    ServletHandler replacement = context.getServletHandler();
    assertFalse(replacement.isEnsureDefaultServlet());
    assertTrue(replacement.isStartWithUnavailable());
    assertTrue(replacement.isAllowDuplicateMappings());
    assertFalse(replacement.isFilterChainsCached());
    assertEquals(7, replacement.getMaxFilterChainsCacheSize());
    assertArrayEquals(new ListenerHolder[]{listener}, replacement.getListeners());
  }
}
