package org.finos.legend.server.pac4j;

import org.pac4j.core.context.WebContext;
import org.pac4j.core.engine.savedrequest.DefaultSavedRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public class LegendRequestHandler extends DefaultSavedRequestHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendRequestHandler.class);
    public static final String REDIRECT_PROTO_ATTRIBUTE = "DEFAULT_PROTO_ATTRIBUTE";

    @Override
    protected String getRequestedUrl(WebContext context)
    {
        Optional<String> protocol = context.getRequestAttribute(REDIRECT_PROTO_ATTRIBUTE);
        if (protocol.isPresent())
        {
            URI uri = URI.create(context.getFullRequestURL());
            URI updatedUri = getUpdatedUri(protocol.get(), uri);
            LOGGER.info("Rewriting requested URL from {} to {}", context.getFullRequestURL(), updatedUri);
            return updatedUri.toString();
        }
        LOGGER.info("Using requested URL as-is: {}", context.getFullRequestURL());
        return context.getFullRequestURL();
    }

    private static URI getUpdatedUri(String protocol, URI uri)
    {
        URI updatedUri;
        try
        {
            updatedUri = new URI(
                    protocol,
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
        return updatedUri;
    }
}
