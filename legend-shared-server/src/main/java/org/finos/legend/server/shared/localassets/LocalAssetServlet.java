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

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import io.dropwizard.servlets.assets.AssetServlet;
import io.dropwizard.servlets.assets.ResourceURL;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

public class LocalAssetServlet extends AssetServlet
{

  private final Map<String, String> localPaths;
  private final String resourcePath;

  /**
   * Creates a new {@code AssetServlet} that serves static assets loaded from {@code resourceURL}
   * (typically a file: or jar: URL). The assets are served at URIs rooted at {@code uriPath}. For
   * example, given a {@code resourceURL} of {@code "file:/data/assets"} and a {@code uriPath} of
   * {@code "/js"}, an {@code AssetServlet} would serve the contents of {@code
   * /data/assets/example.js} in response to a request for {@code /js/example.js}. If a directory is
   * requested and {@code indexFile} is defined, then {@code AssetServlet} will attempt to serve a
   * file with that name in that directory. If a directory is requested and {@code indexFile} is
   * null, it will serve a 404.
   *
   * @param localPaths     the base URL from which assets are loaded
   * @param uriPath        the URI path fragment in which all requests are rooted
   * @param indexFile      the filename to use when dirs are requested, or null to serve no indexes
   * @param defaultCharset the default character set
   */
  public LocalAssetServlet(Map<String, String> localPaths, String resourcePath, String uriPath,
                           String indexFile, Charset defaultCharset)
  {
    super("/", uriPath, indexFile, defaultCharset);
    this.localPaths = localPaths;
    this.resourcePath = resourcePath;
  }

  private boolean shouldCacheContentType(String contentType)
  {
    boolean shouldCache = true;
    if (contentType != null && contentType.startsWith(MediaType.TEXT_HTML))
    {
      shouldCache = false;
    }
    if (contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON))
    {
      shouldCache = false;
    }
    return shouldCache;
  }

  private String getPath(HttpServletRequest req)
  {
    final StringBuilder builder = new StringBuilder(req.getServletPath());
    if (req.getPathInfo() != null)
    {
      builder.append(req.getPathInfo());
    }
    return builder.toString();
  }


  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException
  {
    final String absoluteRequestedResourcePath = getPath(req);
    try
    {
      URL requestedResourceUrl = getResourceUrl(absoluteRequestedResourcePath);
      try
      {
        if (ResourceURL.isDirectory(requestedResourceUrl) && !absoluteRequestedResourcePath
            .endsWith("/"))
        {
          resp.sendRedirect(absoluteRequestedResourcePath + "/");
          req = new HttpServletRequestWrapper(req)
          {
            @Override
            public String getPathInfo()
            {
              String path = super.getPathInfo();
              return path != null ? path + "/" : "/";
            }
          };
        }
      } catch (URISyntaxException e)
      {
        throw new IOException(e);
      }

      resp = new HttpServletResponseWrapper(resp)
      {
        @Override
        public void setContentType(String contentType)
        {
          super.setContentType(contentType);
          if (!shouldCacheContentType(contentType))
          {
            CacheControl cacheControl = new CacheControl();
            cacheControl.setMustRevalidate(true);
            cacheControl.setNoCache(true);
            this.addHeader(HttpHeaders.CACHE_CONTROL, cacheControl.toString());
          }
        }
      };

      super.doGet(req, resp);
    } catch (FileNotFoundException | IllegalArgumentException e)
    {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Unable to find " + absoluteRequestedResourcePath);
    }
  }

  @Override
  protected URL getResourceUrl(String requestedResourcePath)
  {
    final String absoluteRequestedResourcePath =
        requestedResourcePath.startsWith("/") ? requestedResourcePath : "/" + requestedResourcePath;
    Optional<String> matchingPath = localPaths.keySet().stream()
        .filter(absoluteRequestedResourcePath::startsWith).findFirst();
    if (matchingPath.isPresent())
    {
      try
      {
        return new URL("file:///" + localPaths.get(matchingPath.get()).replace('\\', '/')
            + (absoluteRequestedResourcePath).replace(matchingPath.get(), "/"));
      } catch (MalformedURLException e)
      {
        throw new RuntimeException(e);
      }
    } else if (Strings.isNullOrEmpty(this.resourcePath))
    {
      return super.getResourceUrl(absoluteRequestedResourcePath.substring(1));
    } else
    {
      return super.getResourceUrl(this.resourcePath + absoluteRequestedResourcePath);
    }
  }

  @Override
  protected byte[] readResource(URL requestedResourceUrl) throws IOException
  {
    if (requestedResourceUrl.getProtocol().equals("file"))
    {
      try (InputStream stream = requestedResourceUrl.openStream())
      {
        return ByteStreams.toByteArray(stream);
      }
    }
    return super.readResource(requestedResourceUrl);
  }
}
