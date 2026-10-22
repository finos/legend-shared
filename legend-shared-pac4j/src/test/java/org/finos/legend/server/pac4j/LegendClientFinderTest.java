// Copyright 2025 Goldman Sachs
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

package org.finos.legend.server.pac4j;


import org.junit.Test;
import org.mockito.Mockito;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.util.Pac4jConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.finos.legend.server.pac4j.LegendClientFinder.CLIENT_TO_EXCLUDE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LegendClientFinderTest
{

    @Test
    public void testFinderOnlyUnderstandClientNameAsInputRequestParamToInitializeClient()
    {
        WebContext mockedWebContext = Mockito.mock(WebContext.class);
        when(mockedWebContext.getRequestParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER)).thenReturn(Optional.of("testclient"));
        Clients mockedClients = Mockito.mock(Clients.class);
        when(mockedClients.findClient("testclient")).thenReturn(Optional.of((Client) new TestClient()));

        LegendClientFinder legendClientFinder = new LegendClientFinder(Collections.emptyList());
        List<Client> testClient = legendClientFinder.find(mockedClients, mockedWebContext, "testclient");

        assertNotNull(testClient);
        verify(mockedWebContext).getRequestParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER);
    }

    @Test
    public void clientNameParameterForcesASingleClient()
    {
        WebContext mockedWebContext = Mockito.mock(WebContext.class);
        when(mockedWebContext.getRequestParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER)).thenReturn(Optional.of("testclient1"));

        List<Client> found = findWithTwoDefaultClients(mockedWebContext);

        assertEquals("?client_name=testclient1 must force exactly that client", 1, found.size());
        assertEquals("testclient1", found.get(0).getName());
    }

    /**
     * Deliberately deviates from pac4j 6: only {@code client_name} is honoured, which is what UIs,
     * deep links and bookmarks already carry.
     */
    @Test
    public void forceClientParameterIsIgnored()
    {
        WebContext mockedWebContext = Mockito.mock(WebContext.class);
        when(mockedWebContext.getRequestParameter(Pac4jConstants.DEFAULT_FORCE_CLIENT_PARAMETER)).thenReturn(Optional.of("testclient1"));

        assertEquals("?force_client= must be ignored", 2, findWithTwoDefaultClients(mockedWebContext).size());
    }

    @Test
    public void clientNameDecidesWhenForceClientIsAlsoPresent()
    {
        WebContext mockedWebContext = Mockito.mock(WebContext.class);
        when(mockedWebContext.getRequestParameter(Pac4jConstants.DEFAULT_FORCE_CLIENT_PARAMETER)).thenReturn(Optional.of("testclient1"));
        when(mockedWebContext.getRequestParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER)).thenReturn(Optional.of("testclient2"));

        List<Client> found = findWithTwoDefaultClients(mockedWebContext);

        assertEquals(1, found.size());
        assertEquals("testclient2", found.get(0).getName());
    }

    @Test
    public void anUnknownForceClientIsIgnored()
    {
        WebContext mockedWebContext = Mockito.mock(WebContext.class);
        when(mockedWebContext.getRequestParameter(Pac4jConstants.DEFAULT_FORCE_CLIENT_PARAMETER)).thenReturn(Optional.of("bogus"));

        assertEquals(2, findWithTwoDefaultClients(mockedWebContext).size());
    }

    @Test
    public void withNeitherParameterTheDefaultClientsAreUsed()
    {
        WebContext mockedWebContext = Mockito.mock(WebContext.class);

        Clients mockedClients = Mockito.mock(Clients.class);
        when(mockedClients.findClient("testclient1")).thenReturn(Optional.of((Client) new TestClient("testclient1")));
        when(mockedClients.findClient("testclient2")).thenReturn(Optional.of((Client) new TestClient("testclient2")));

        LegendClientFinder legendClientFinder =
                new LegendClientFinder(Arrays.asList("testclient1", "testclient2"));

        assertEquals(2,
                legendClientFinder.find(mockedClients, mockedWebContext, "testclient1,testclient2").size());
    }

    private static List<Client> findWithTwoDefaultClients(WebContext context)
    {
        Clients mockedClients = Mockito.mock(Clients.class);
        when(mockedClients.findClient("testclient1")).thenReturn(Optional.of((Client) new TestClient("testclient1")));
        when(mockedClients.findClient("testclient2")).thenReturn(Optional.of((Client) new TestClient("testclient2")));

        LegendClientFinder legendClientFinder = new LegendClientFinder(Arrays.asList("testclient1", "testclient2"));
        return legendClientFinder.find(mockedClients, context, "testclient1,testclient2");
    }

    @Test
    public void testMultipleDefaultClients()
    {
        WebContext mockedWebContext = Mockito.mock(WebContext.class);

        Clients mockedClients = Mockito.mock(Clients.class);
        when(mockedClients.findClient("testclient1")).thenReturn(Optional.of((Client) new TestClient()));
        when(mockedClients.findClient("testclient2")).thenReturn(Optional.of((Client) new TestClient()));

        LegendClientFinder legendClientFinder = new LegendClientFinder(Arrays.asList("testclient1","testclient2"));
        List<Client> testClient = legendClientFinder.find(mockedClients, mockedWebContext, "testclient1,testclient2");

        assertNotNull(testClient);
        assertEquals(2,testClient.size());

    }

    @Test
    public void testMultipleDefaultClientsWithSingleExclusionInRequest()
    {
        WebContext mockedWebContext = Mockito.mock(WebContext.class);
        when(mockedWebContext.getRequestAttribute(CLIENT_TO_EXCLUDE)).thenReturn(Optional.of(Collections.singletonList("testclient")));

        Clients mockedClients = Mockito.mock(Clients.class);
        when(mockedClients.findClient("testclient1")).thenReturn(Optional.of((Client) new TestClient("testclient1")));
        when(mockedClients.findClient("testclient2")).thenReturn(Optional.of((Client) new TestClient("testclient2")));
        when(mockedClients.findClient("testclient")).thenReturn(Optional.of((Client) new TestClient("testclient")));

        LegendClientFinder legendClientFinder = new LegendClientFinder(Arrays.asList("testclient1","testclient2","testclient"));
        List<Client> testClient = legendClientFinder.find(mockedClients, mockedWebContext, "testclient1,testclient2,testclient");

        assertNotNull(testClient);
        assertEquals(2,testClient.size());
        assertEquals("testclient1",testClient.get(0).getName());
        assertEquals("testclient2",testClient.get(1).getName());

    }

    @Test
    public void testMultipleDefaultClientsWithMultipleExclusionInRequest()
    {
        WebContext mockedWebContext = Mockito.mock(WebContext.class);
        when(mockedWebContext.getRequestAttribute(CLIENT_TO_EXCLUDE)).thenReturn(Optional.of(Arrays.asList("testclient","testclient1")));

        Clients mockedClients = Mockito.mock(Clients.class);
        when(mockedClients.findClient("testclient1")).thenReturn(Optional.of((Client) new TestClient("testclient1")));
        when(mockedClients.findClient("testclient2")).thenReturn(Optional.of((Client) new TestClient("testclient2")));
        when(mockedClients.findClient("testclient")).thenReturn(Optional.of((Client) new TestClient("testclient")));

        LegendClientFinder legendClientFinder = new LegendClientFinder(Arrays.asList("testclient1","testclient2","testclient"));
        List<Client> testClient = legendClientFinder.find(mockedClients, mockedWebContext, "testclient1,testclient2,testclient");

        assertNotNull(testClient);
        assertEquals(1,testClient.size());
        assertEquals("testclient2",testClient.get(0).getName());

    }
}