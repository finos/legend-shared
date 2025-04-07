package org.finos.legend.server.pac4j;

import de.bwaldvogel.mongo.backend.Assert;
import org.checkerframework.checker.nullness.Opt;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.util.Pac4jConstants;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
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
        when(mockedClients.findClient("testclient")).thenReturn(Optional.of(new TestClient()));

        LegendClientFinder legendClientFinder = new LegendClientFinder();
        List<Client<? extends Credentials>> testClient = legendClientFinder.find(mockedClients, mockedWebContext, "testclient");

        Assert.notNull(testClient);
        verify(mockedWebContext).getRequestParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER);
    }
}