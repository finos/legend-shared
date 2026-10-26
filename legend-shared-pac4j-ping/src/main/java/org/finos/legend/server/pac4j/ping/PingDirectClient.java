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
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.http.client.direct.DirectBearerAuthClient;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.profile.creator.OidcProfileCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
@SerializableProfile
public class PingDirectClient extends DirectBearerAuthClient
{
    private static final Logger logger = LoggerFactory.getLogger(PingDirectClient.class);

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

    /**
     * Not rebuilt on {@code reinit()}: its inputs are fixed at deserialization, and it caches the
     * resolved OP metadata.
     */
    private OidcConfiguration oidcConfiguration;
    private OidcProfileCreator oidcProfileCreator;

    @Override
    protected void internalInit(boolean forceReinit)
    {
        if (this.oidcConfiguration == null)
        {
            this.oidcConfiguration = buildConfiguration();
            // OidcProfileCreator needs an OidcClient, which this is not. The throwaway client is only
            // dereferenced on the authorization-code flow, never on the bearer path.
            this.oidcProfileCreator =
                    new OidcProfileCreator(this.oidcConfiguration, new OidcClient(this.oidcConfiguration))
                    {
                        @Override
                        protected void internalInit(boolean forceReinit)
                        {
                            // Here, on the first bearer request, because it fetches the discovery document;
                            // nothing else initialises the configuration. Never forced: a forced init
                            // replaces the proxy-aware resource retriever.
                            configuration.init();
                            super.internalInit(forceReinit);
                        }
                    };
        }

        // ALWAYS_VALIDATE is not "no validation": it passes the token to OidcProfileCreator, whose
        // userinfo call yields no profile (a 401) for a revoked or invalid token. NEVER_VALIDATE
        // would reject every request before the profile creator runs.
        setAuthenticator(Authenticator.ALWAYS_VALIDATE);
        setProfileCreator(this.oidcProfileCreator);
        super.internalInit(forceReinit);
    }

    private OidcConfiguration buildConfiguration()
    {
        OidcConfiguration config = new OidcConfiguration();
        config.setClientId(clientId);
        config.setSecret(secret);
        config.setDiscoveryURI(discoveryUri);
        config.setCustomParams(customParams);

        DefaultResourceRetriever resourceRetriever =
                new DefaultResourceRetriever(config.getConnectTimeout(), config.getReadTimeout());
        if (proxyHost != null && !"".equals(proxyHost))
        {
            logger.info("Using proxy {}:{}", proxyHost, proxyPort);
            resourceRetriever.setProxy(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }
        config.setResourceRetriever(resourceRetriever);

        if (scope == null || "".equals(scope))
        {
            scope = "openid profile";
        }
        config.setScope(scope);
        return config;
    }
}
