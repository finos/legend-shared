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

package org.finos.legend.server.shared.localassets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import org.glassfish.grizzly.servlet.WebappContext;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class LocalAssetServletTest
{
  @Test
  public void testIndexCacheControl() throws ServletException, IOException
  {
    LocalAssetServlet servlet = new LocalAssetServlet(ImmutableMap.of(), "web", "/", "index.html", StandardCharsets.UTF_8);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getServletPath()).thenReturn("/ui/index.html");
    when(request.getRequestURI()).thenReturn("/ui/index.html");
    when(request.getServletContext()).thenReturn(new WebappContext("testApp"));
    HttpServletResponse response = mock(HttpServletResponse.class);
    servlet.doGet(request, response);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(response).addHeader(eq(HttpHeaders.CACHE_CONTROL), captor.capture());
    Assert.assertEquals("no-cache, no-transform, must-revalidate", captor.getValue());
  }


  @Test
  public void testImageCacheControl() throws ServletException, IOException
  {
    LocalAssetServlet servlet = new LocalAssetServlet(ImmutableMap.of(), "web", "/", "index.html", StandardCharsets.UTF_8);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getServletPath()).thenReturn("/ui/gs.svg");
    when(request.getRequestURI()).thenReturn("/ui/gs.svg");
    when(request.getServletContext()).thenReturn(new WebappContext("testApp"));
    HttpServletResponse response = mock(HttpServletResponse.class);
    servlet.doGet(request, response);
    verify(response, never()).addHeader(eq(HttpHeaders.CACHE_CONTROL), any());
  }
}
