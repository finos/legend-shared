package org.finos.legend.server.pac4j;

import org.junit.Test;
import org.pac4j.core.context.WebContext;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LegendRequestHandlerTest
{

    @Test
    public void testGetRequestedUrlWithRedirectProtocol()
    {
        LegendRequestHandler handler = new LegendRequestHandler();
        WebContext context = mock(WebContext.class);

        when(context.getRequestAttribute(LegendRequestHandler.REDIRECT_PROTO_ATTRIBUTE))
                .thenReturn(Optional.of("https"));
        when(context.getFullRequestURL())
                .thenReturn("http://localhost.com:8080/path?query=value#fragment");

        String result = handler.getRequestedUrl(context);

        assertEquals("https://localhost.com:8080/path?query=value#fragment", result);
    }

    @Test
    public void testGetRequestedUrlWithoutRedirectProtocol()
    {
        LegendRequestHandler handler = new LegendRequestHandler();
        WebContext context = mock(WebContext.class);

        when(context.getRequestAttribute(LegendRequestHandler.REDIRECT_PROTO_ATTRIBUTE))
                .thenReturn(Optional.empty());
        when(context.getFullRequestURL())
                .thenReturn("http://localhost.com:8080/path");

        String result = handler.getRequestedUrl(context);

        assertEquals("http://localhost.com:8080/path", result);
    }

}