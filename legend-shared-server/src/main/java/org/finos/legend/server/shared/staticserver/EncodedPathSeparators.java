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

package org.finos.legend.server.shared.staticserver;

import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.ServerFactory;
import io.dropwizard.core.server.SimpleServerFactory;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import jakarta.annotation.Priority;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.FilterMapping;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.http.UriCompliance;

/**
 * Lets an SPA deep link carry an encoded {@code /} ({@code %2F}) again, as Jetty 9.4 did. Studio
 * puts {@code encodeURIComponent(showcasePath)} in its showcase route, and Jetty 12 answers 400 to it.
 *
 * <p>Jetty 9.4 normalised dot segments after decoding; Jetty 12 does not. So a {@code %2F} path whose
 * decoded form has a {@code .} or {@code ..} segment still gets 400, before anything else sees it:
 * decoded, it would climb out of the asset root.
 */
final class EncodedPathSeparators
{
  private static final Pattern ENCODED_SEPARATOR = Pattern.compile("%2F", Pattern.CASE_INSENSITIVE);
  private static final String GUARD_NAME = "EncodedPathSeparatorGuard";

  private EncodedPathSeparators()
  {
  }

  static UriCompliance allow(UriCompliance compliance)
  {
    return compliance.allows(UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR)
        ? compliance
        : compliance.with(compliance.getName() + ",AMBIGUOUS_PATH_SEPARATOR", UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR);
  }

  /**
   * In code because a custom compliance string in the server config deserializes to null. Admin
   * connectors are left alone. Must run before Dropwizard builds the server from its factory.
   */
  static void allowOnApplicationConnectors(ServerFactory serverFactory)
  {
    List<ConnectorFactory> connectors = serverFactory instanceof DefaultServerFactory
        ? ((DefaultServerFactory) serverFactory).getApplicationConnectors()
        : serverFactory instanceof SimpleServerFactory
        ? Collections.singletonList(((SimpleServerFactory) serverFactory).getConnector())
        : Collections.emptyList();
    for (ConnectorFactory connector : connectors)
    {
      if (connector instanceof HttpConnectorFactory)
      {
        HttpConnectorFactory http = (HttpConnectorFactory) connector;
        http.setUriCompliance(allow(http.getUriCompliance()));
      }
    }
  }

  static void install(ServletContextHandler context)
  {
    ServletHandler handler = context.getServletHandler();
    handler.setDecodeAmbiguousURIs(true);

    FilterHolder holder = new FilterHolder(new Guard());
    holder.setName(GUARD_NAME);
    handler.prependFilter(holder);
    FilterMapping mapping = new FilterMapping();
    mapping.setFilterName(GUARD_NAME);
    mapping.setPathSpec("/*");
    mapping.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
    handler.prependFilterMapping(mapping);
  }

  static boolean isAllowed(String encodedRequestUri)
  {
    return !ENCODED_SEPARATOR.matcher(encodedRequestUri).find() || hasNoDotSegment(encodedRequestUri);
  }

  private static boolean hasNoDotSegment(String encodedPath)
  {
    String decoded;
    try
    {
      decoded = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8);
    }
    catch (IllegalArgumentException e)
    {
      return false;
    }
    for (String segment : decoded.split("/", -1))
    {
      if (segment.equals(".") || segment.equals(".."))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * The highest priority, so it stays first if {@code ChainFixingFilterHandler} reorders the chain.
   */
  @Priority(Integer.MAX_VALUE)
  static final class Guard implements Filter
  {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException
    {
      if (!isAllowed(((HttpServletRequest) request).getRequestURI()))
      {
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Ambiguous URI path separator");
        return;
      }
      chain.doFilter(request, response);
    }
  }
}
