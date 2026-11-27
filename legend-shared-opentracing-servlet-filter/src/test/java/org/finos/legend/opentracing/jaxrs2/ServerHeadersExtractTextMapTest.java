// Copyright 2026 Harsh
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

package org.finos.legend.opentracing.jaxrs2;

import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerHeadersExtractTextMapTest
{
    @Test
    public void extractsOnlyFirstValuesAndSkipsEmptyLists()
    {
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put("trace-id", Arrays.asList("first", "second"));
        headers.put("empty", Collections.emptyList());
        headers.put("missing", null);
        headers.put("null-value", Collections.singletonList(null));
        Map<String, String> expected = new HashMap<>();
        expected.put("trace-id", "first");
        expected.put("null-value", null);
        ServerHeadersExtractTextMap carrier = new ServerHeadersExtractTextMap(headers);

        for (int i = 0; i < 2; i++)
        {
            Map<String, String> actual = new HashMap<>();
            Iterator<Map.Entry<String, String>> iterator = carrier.iterator();
            while (iterator.hasNext())
            {
                Assert.assertTrue(iterator.hasNext());
                Map.Entry<String, String> entry = iterator.next();
                actual.put(entry.getKey(), entry.getValue());
            }
            Assert.assertEquals(expected, actual);
            Assert.assertFalse(iterator.hasNext());
            Assert.assertThrows(NoSuchElementException.class, iterator::next);
        }
    }

    @Test
    public void readsOnlyTheValuesConsumedByTheTracer()
    {
        AtomicInteger reads = new AtomicInteger();
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        for (int i = 0; i < 100; i++)
        {
            headers.put("header-" + i, new ArrayList<String>(Collections.singletonList("value"))
            {
                @Override
                public String get(int index)
                {
                    reads.incrementAndGet();
                    return super.get(index);
                }
            });
        }
        Iterator<Map.Entry<String, String>> iterator = new ServerHeadersExtractTextMap(headers).iterator();
        Assert.assertEquals(0, reads.get());
        Assert.assertEquals("value", iterator.next().getValue());
        Assert.assertEquals(1, reads.get());
    }

    @Test
    public void emptyCarrierAndUnsupportedInjection()
    {
        ServerHeadersExtractTextMap carrier = new ServerHeadersExtractTextMap(new MultivaluedHashMap<>());
        Assert.assertFalse(carrier.iterator().hasNext());
        Assert.assertThrows(NoSuchElementException.class, () -> carrier.iterator().next());
        Assert.assertThrows(UnsupportedOperationException.class, () -> carrier.put("key", "value"));
    }
}
