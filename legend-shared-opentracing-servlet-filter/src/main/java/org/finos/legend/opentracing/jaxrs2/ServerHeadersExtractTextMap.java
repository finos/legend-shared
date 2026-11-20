// Forked from:
//      https://github.com/opentracing-contrib/java-jaxrs/blob/master/opentracing-jaxrs2/src/main/java/io/opentracing/contrib/jaxrs2/server/ServerHeadersExtractTextMap.java
//
// Forked only to move it to jakarta.ws.rs: opentracing-jaxrs2 has no jakarta release.
// Header iteration is lazy to avoid building a flattened map for each extraction.
// Delete this fork if it ever ships one, or when this module migrates to OpenTelemetry.

package org.finos.legend.opentracing.jaxrs2;

import io.opentracing.propagation.TextMap;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;

public class ServerHeadersExtractTextMap implements TextMap
{
    private final MultivaluedMap<String, String> headers;

    public ServerHeadersExtractTextMap(MultivaluedMap<String, String> headers)
    {
        this.headers = headers;
    }

    /**
     * Iterates over the first value of each non-empty header without copying the headers.
     * The backing headers must not be modified during iteration.
     */
    @Override
    public Iterator<Map.Entry<String, String>> iterator()
    {
        return headers.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .<Map.Entry<String, String>>map(entry ->
                        new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue().get(0)))
                .iterator();
    }

    @Override
    public void put(String key, String value)
    {
        throw new UnsupportedOperationException("This class should be used only with Tracer.extract()");
    }
}
