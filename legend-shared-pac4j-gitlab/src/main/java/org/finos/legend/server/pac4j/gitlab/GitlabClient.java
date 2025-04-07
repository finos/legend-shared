// Copyright 2020 Goldman Sachs
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

package org.finos.legend.server.pac4j.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import org.finos.legend.server.pac4j.SerializableProfile;
import org.finos.legend.server.pac4j.gitlab.ssl.TrustManagerComposite;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.http.url.DefaultUrlResolver;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.credentials.OidcCredentials;
import org.pac4j.oidc.credentials.authenticator.OidcAuthenticator;
import org.pac4j.oidc.profile.OidcProfile;
import org.pac4j.oidc.profile.creator.OidcProfileCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.util.Optional;

@SuppressWarnings("unused")
@SerializableProfile
public class GitlabClient extends OidcClient<OidcConfiguration>
{
    private static final Logger logger = LoggerFactory.getLogger(GitlabClient.class);
    public static final String GITLAB_CLIENT_NAME = "gitlab";

    @JsonProperty
    protected String name = GITLAB_CLIENT_NAME;
    @JsonProperty
    protected String clientId;
    @JsonProperty
    protected String secret;
    @JsonProperty
    protected String discoveryUri;
    @JsonProperty
    protected String scope;

    @JsonProperty
    protected String proxyHost;
    @JsonProperty
    protected int proxyPort;

    @JsonProperty
    protected String sslKeystore;

    @Override
    public void setName(String name)
    {
        this.name = name;
    }

    @Override
    public String getName()
    {
        return this.name;
    }

    @Override
    protected void clientInit()
    {

        if (StringUtils.isNotBlank(sslKeystore))
        {
            TrustManager[] myTMs = new TrustManager[]{
                    new TrustManagerComposite(sslKeystore)
            };

            try
            {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, myTMs, null);
                SSLContext.setDefault(ctx);
            }
            catch (GeneralSecurityException e)
            {
                throw new RuntimeException("Cannot initialize Trust store", e);
            }
        }

        OidcConfiguration config = new OidcConfiguration();
        config.setClientId(clientId);
        config.setSecret(secret);
        config.setDiscoveryURI(discoveryUri);

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
        setConfiguration(config);
        setAuthenticator(
                new OidcAuthenticator(config, this)
                {
                    @Override
                    public void validate(OidcCredentials credentials, WebContext context)
                    {
                        if (proxyHost != null && !"".equals(proxyHost))
                        {
                            System.setProperty("https.proxyHost", proxyHost);
                            System.setProperty("https.proxyPort", String.valueOf(proxyPort));
                            super.validate(credentials, context);
                            System.setProperty("https.proxyHost", "");
                            System.setProperty("https.proxyPort", "");
                        }
                        else
                        {
                            super.validate(credentials, context);
                        }
                    }
                });
        setProfileCreator(
                new OidcProfileCreator<OidcProfile>(config,this)
                {
                    @Override
                    public Optional<UserProfile> create(OidcCredentials credentials, WebContext context)
                    {
                        OidcProfile profile = (OidcProfile)super.create(credentials, context).get();
                        profile.setId(profile.getNickname());
                        return Optional.of(profile);
                    }
                });
        setUrlResolver(new DefaultUrlResolver(true));
        super.clientInit();
    }

    public String getClientId()
    {
        return clientId;
    }

    public void setClientId(String clientId)
    {
        this.clientId = clientId;
    }

    public String getSecret()
    {
        return secret;
    }

    public void setSecret(String secret)
    {
        this.secret = secret;
    }

    public String getDiscoveryUri()
    {
        return discoveryUri;
    }

    public void setDiscoveryUri(String discoveryUri)
    {
        this.discoveryUri = discoveryUri;
    }

    public String getScope()
    {
        return scope;
    }

    public void setScope(String scope)
    {
        this.scope = scope;
    }

    public String getProxyHost()
    {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost)
    {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort()
    {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort)
    {
        this.proxyPort = proxyPort;
    }

    public String getSslKeystore()
    {
        return sslKeystore;
    }

    public void setSslKeystore(String sslKeystore)
    {
        this.sslKeystore = sslKeystore;
    }
}
