/*
 *  Copyright 2021 Goldman Sachs
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.finos.legend.server.pac4j.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Map;
import org.finos.legend.server.pac4j.SerializableProfile;
import org.pac4j.core.http.url.DefaultUrlResolver;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.credentials.authenticator.OidcAuthenticator;
import org.pac4j.oidc.profile.OidcProfile;
import org.pac4j.oidc.profile.creator.OidcProfileCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
@SerializableProfile
public class PingIndirectClient extends OidcClient<OidcConfiguration>
{
    private static final Logger logger = LoggerFactory.getLogger(PingIndirectClient.class);

    @JsonProperty
    private String clientId;

    @JsonProperty
    private String secret;

    @JsonProperty
    private String discoveryUri;

    @JsonProperty
    private String scope;

    @JsonProperty
    private String proxyHost;

    @JsonProperty
    private int proxyPort;

    @JsonProperty
    private Map<String, String> customParams;

    @Override
    protected void clientInit()
    {
        OidcConfiguration config = new OidcConfiguration();
        config.setClientId(clientId);
        config.setSecret(secret);
        config.setDiscoveryURI(discoveryUri);
        config.setCustomParams(customParams);

        DefaultResourceRetriever resourceRetriever =
                new DefaultResourceRetriever(config.getConnectTimeout(), config.getReadTimeout());
        if (proxyHost != null && !proxyHost.isEmpty())
        {
            logger.info("Using proxy {}:{}", proxyHost, proxyPort);
            resourceRetriever.setProxy(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }
        config.setResourceRetriever(resourceRetriever);

        if (scope == null || scope.isEmpty())
        {
            scope = "openid profile";
        }
        config.setScope(scope);

        setConfiguration(config);
        setAuthenticator(new OidcAuthenticator(config, this));
        setProfileCreator(new OidcProfileCreator<>(config,this));
        setUrlResolver(new DefaultUrlResolver(true));
        super.clientInit();
    }
}
