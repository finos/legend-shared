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

package org.finos.legend.server.pac4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.YamlConfigurationFactory;
import java.io.IOException;
import java.util.List;
import org.pac4j.core.authorization.authorizer.Authorizer;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.finder.ClientFinder;
import org.pac4j.core.client.finder.DefaultSecurityClientFinder;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class LegendPac4jConfiguration
{
  private String defaults;
  private List<Authorizer> authorizers = ImmutableList.of();
  private List<Client> clients;
  private String defaultClient;
  private String mongoUri;
  private String mongoDb;
  private MongoSessionConfiguration mongoSession = new MongoSessionConfiguration();
  private String callbackPrefix = "";
  private List<String> bypassPaths = ImmutableList.of();
  private List<String> bypassBranches = ImmutableList.of();
  private List<String> trustedPackages = ImmutableList.of();

  public void setDefaults(String defaults)
  {
    this.defaults = defaults;
  }

  public List<String> getBypassPaths()
  {
    return bypassPaths;
  }

  public void setBypassPaths(List<String> bypassPaths)
  {
    this.bypassPaths = bypassPaths;
  }

  private void defaultBypassPaths(List<String> bypassPaths)
  {
    if (this.bypassPaths.isEmpty())
    {
      this.bypassPaths = bypassPaths;
    }
  }

  public List<String> getBypassBranches()
  {
    return bypassBranches;
  }

  public List<String> getTrustedPackages()
  {
    return trustedPackages;
  }

  public void setBypassBranches(List<String> bypassBranches)
  {
    this.bypassBranches = bypassBranches;
  }

  private void defaultBypassBranches(List<String> bypassBranches)
  {
    if (this.bypassBranches.isEmpty())
    {
      this.bypassBranches = bypassBranches;
    }
  }

  public List<Client> getClients()
  {
    return clients;
  }

  public void setClients(List<Client> clients)
  {
    this.clients = clients;
  }

  public ClientFinder getDefaultSecurityClient()
  {
    ClientFinder f;
    if (this.defaultClient != null)
    {
      f = new LegendClientFinder(this.defaultClient);
    } else
    {
      f = new LegendClientFinder();
    }
    return f;
  }

  private void defaultClients(List<Client> clients)
  {
    if (this.clients == null || this.clients.isEmpty())
    {
      this.clients = clients;
    }
  }

  public List<Authorizer> getAuthorizers()
  {
    return authorizers;
  }

  public void setAuthorizers(List<Authorizer> authorizers)
  {
    this.authorizers = authorizers;
  }

  private void defaultAuthorizers(List<Authorizer> authorizers)
  {
    if (this.authorizers == null || this.authorizers.isEmpty())
    {
      this.authorizers = authorizers;
    }
  }

  public MongoSessionConfiguration getMongoSession()
  {
    return mongoSession;
  }

  public void setMongoSession(MongoSessionConfiguration mongoSession)
  {
    this.mongoSession = mongoSession;
  }

  private void defaultMongoSession(MongoSessionConfiguration mongoSession)
  {
    this.mongoSession.defaults(mongoSession);
  }

  public String getMongoUri()
  {
    return mongoUri;
  }

  public void setDefaultClient(String defaultClient)
  {
    this.defaultClient = defaultClient;
  }

  public void setMongoUri(String mongoUri)
  {
    this.mongoUri = mongoUri;
  }

  private void defaultMongoUri(String mongoUri)
  {
    if (Strings.isNullOrEmpty(this.mongoUri))
    {
      this.mongoUri = mongoUri;
    }
  }

  public String getMongoDb()
  {
    return mongoDb;
  }

  public void setMongoDb(String mongoDb)
  {
    this.mongoDb = mongoDb;
  }

  private void defaultMongoDb(String mongoDb)
  {
    if (Strings.isNullOrEmpty(this.mongoDb))
    {
      this.mongoDb = mongoDb;
    }
  }

  public String getCallbackPrefix()
  {
    return callbackPrefix;
  }

  public void setCallbackPrefix(String callbackPrefix)
  {
    this.callbackPrefix = callbackPrefix;
  }

  private void defaultCallbackPrefix(String callbackPrefix)
  {
    if (Strings.isNullOrEmpty(this.callbackPrefix))
    {
      this.callbackPrefix = callbackPrefix;
    }
  }

  public void loadDefaults(ConfigurationSourceProvider configurationSourceProvider,
                           ObjectMapper objectMapper)
      throws IOException, ConfigurationException
  {
    if (!Strings.isNullOrEmpty(defaults))
    {
      YamlConfigurationFactory<LegendPac4jConfiguration> configFactory =
          new YamlConfigurationFactory<>(
              LegendPac4jConfiguration.class, null, objectMapper, "");
      LegendPac4jConfiguration other = configFactory.build(configurationSourceProvider, defaults);
      other.loadDefaults(configurationSourceProvider, objectMapper);
      this.defaultAuthorizers(other.getAuthorizers());
      this.defaultBypassBranches(other.getBypassBranches());
      this.defaultBypassPaths(other.getBypassPaths());
      this.defaultCallbackPrefix(other.getCallbackPrefix());
      this.defaultClients(other.getClients());
      this.defaultMongoDb(other.getMongoDb());
      this.defaultMongoSession(other.getMongoSession());
      this.defaultMongoUri(other.getMongoUri());
    }
  }

  public static class MongoSessionConfiguration
  {
    private static final String DEFAULT_CRYPTO_ALGORITHM = "AES";
    private static final int DEFAULT_MAX_SESSION_LENGTH = 7200;
    private boolean enabled;
    private String collection;
    private String cryptoAlgorithm = DEFAULT_CRYPTO_ALGORITHM;
    private int maxSessionLength = DEFAULT_MAX_SESSION_LENGTH;

    public boolean isEnabled()
    {
      return enabled;
    }

    public void setEnabled(boolean enabled)
    {
      this.enabled = enabled;
    }

    public String getCollection()
    {
      return collection;
    }

    public void setCollection(String collection)
    {
      this.collection = collection;
    }

    public String getCryptoAlgorithm()
    {
      return cryptoAlgorithm;
    }

    public void setCryptoAlgorithm(String cryptoAlgorithm)
    {
      this.cryptoAlgorithm = cryptoAlgorithm;
    }

    private void defaultCryptoAlgorithm(String cryptoAlgorithm)
    {
      if (this.cryptoAlgorithm.equals(DEFAULT_CRYPTO_ALGORITHM))
      {
        this.cryptoAlgorithm = cryptoAlgorithm;
      }
    }

    public int getMaxSessionLength()
    {
      return maxSessionLength;
    }

    public void setMaxSessionLength(int maxSessionLength)
    {
      this.maxSessionLength = maxSessionLength;
    }

    private void defaultMaxSessionLength(int maxSessionLength)
    {
      if (this.maxSessionLength == DEFAULT_MAX_SESSION_LENGTH)
      {
        this.maxSessionLength = maxSessionLength;
      }
    }

    private void defaultEnabled(boolean enabled)
    {
      this.enabled = this.enabled || enabled;
    }

    private void defaultCollection(String collection)
    {
      if (Strings.isNullOrEmpty(this.collection))
      {
        this.collection = collection;
      }
    }

    private void defaults(MongoSessionConfiguration other)
    {
      this.defaultCollection(other.getCollection());
      this.defaultCryptoAlgorithm(other.getCryptoAlgorithm());
      this.defaultEnabled(other.isEnabled());
      this.defaultMaxSessionLength(other.getMaxSessionLength());
    }
  }
}
