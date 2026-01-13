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

package org.finos.legend.server.pac4j.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.legend.server.pac4j.LegendClientFinder;
import org.finos.legend.server.pac4j.LegendPac4jConfiguration;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class StringOrArrayDeserializerTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testDeserializeSingleString() throws Exception
    {
        String json = "{\"defaultClient\": \"client1\"}";
        LegendPac4jConfiguration config = objectMapper.readValue(json, LegendPac4jConfiguration.class);
        List<String> defaultClients = ((LegendClientFinder)config.getDefaultSecurityClient()).getDefaultClients();

        assertNotNull(defaultClients);
        assertEquals(1, defaultClients.size());
        assertEquals("client1", defaultClients.get(0));
    }

    @Test
    public void testDeserializeArray() throws Exception
    {
        String json = "{\"defaultClient\": [\"client1\", \"client2\", \"client3\"]}";
        LegendPac4jConfiguration config = objectMapper.readValue(json, LegendPac4jConfiguration.class);
        List<String> defaultClients = ((LegendClientFinder)config.getDefaultSecurityClient()).getDefaultClients();

        assertNotNull(defaultClients);
        assertEquals(3, defaultClients.size());
        assertEquals("client1", defaultClients.get(0));
        assertEquals("client2", defaultClients.get(1));
        assertEquals("client3", defaultClients.get(2));
    }

    @Test
    public void testDeserializeEmptyArray() throws Exception
    {
        String json = "{\"defaultClient\": []}";
        LegendPac4jConfiguration config = objectMapper.readValue(json, LegendPac4jConfiguration.class);
        List<String> defaultClients = ((LegendClientFinder)config.getDefaultSecurityClient()).getDefaultClients();

        assertNotNull(defaultClients);
        assertTrue(defaultClients.isEmpty());
    }

    @Test
    public void testDeserializeNullValue() throws Exception
    {
        String json = "{\"defaultClient\": null}";
        LegendPac4jConfiguration config = objectMapper.readValue(json, LegendPac4jConfiguration.class);
        List<String> defaultClients = ((LegendClientFinder)config.getDefaultSecurityClient()).getDefaultClients();

        assertNotNull(defaultClients);
        assertTrue(defaultClients.isEmpty());
    }

    @Test
    public void testDeserializeWithMissingDefaultClientField() throws Exception
    {
        String json = "{}";
        LegendPac4jConfiguration config = objectMapper.readValue(json, LegendPac4jConfiguration.class);
        List<String> defaultClients = ((LegendClientFinder)config.getDefaultSecurityClient()).getDefaultClients();

        assertNotNull(defaultClients);
        assertTrue(defaultClients.isEmpty());
    }
}