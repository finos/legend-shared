// Forked from:
//      https://github.com/opentracing-contrib/java-jaxrs/blob/master/opentracing-jaxrs2/src/main/java/io/opentracing/contrib/jaxrs2/server/ServerHeadersExtractTextMap.java
//
// Forked only to move it to jakarta.ws.rs: opentracing-jaxrs2 has no jakarta release.
// Delete this fork if it ever ships one, or when this module migrates to OpenTelemetry.

package org.finos.legend.opentracing.jaxrs2;

import io.opentracing.propagation.TextMap;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ServerHeadersExtractTextMap implements TextMap
{
    private final MultivaluedMap<String, String> headers;

    public ServerHeadersExtractTextMap(MultivaluedMap<String, String> headers)
    {
        this.headers = headers;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator()
    {
        Map<String, String> flattened = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet())
        {
            List<String> values = entry.getValue();
            if (values != null && !values.isEmpty())
            {
                flattened.put(entry.getKey(), values.get(0));
            }
        }
        return flattened.entrySet().iterator();
    }

    @Override
    public void put(String key, String value)
    {
        throw new UnsupportedOperationException("This class should be used only with Tracer.extract()");
    }
}
