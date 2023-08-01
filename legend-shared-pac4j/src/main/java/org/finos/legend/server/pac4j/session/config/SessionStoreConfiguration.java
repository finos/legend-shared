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

package org.finos.legend.server.pac4j.session.config;

public class SessionStoreConfiguration
{
    private static final String DEFAULT_CRYPTO_ALGORITHM = "AES";
    private static final int DEFAULT_MAX_SESSION_LENGTH = 7200;

    private boolean enabled;

    private MongodbConfiguration mongodbConfiguration;
    private RedisConfiguration redisConfiguration;

    private String cryptoAlgorithm = DEFAULT_CRYPTO_ALGORITHM;
    private int maxSessionLength = DEFAULT_MAX_SESSION_LENGTH;

    public static class MongodbConfiguration
    {
        private String databaseURI;
        private String databaseName;
        private String collection;

        public MongodbConfiguration()
        {
        }

        public String getDatabaseURI()
        {
            return databaseURI;
        }

        public String getDatabaseName()
        {
            return databaseName;
        }

        public String getCollection()
        {
            return collection;
        }

        public void setDatabaseName(String databaseName)
        {
            this.databaseName = databaseName;
        }

        public void setDatabaseURI(String databaseURI)
        {
            this.databaseURI = databaseURI;
        }

        public void setCollection(String collection)
        {
            this.collection = collection;
        }
    }

    public static class RedisConfiguration
    {
        private String hostname;
        private String port;

        public RedisConfiguration()
        {
        }

        public String getHostname()
        {
            return hostname;
        }

        public String getPort()
        {
            return port;
        }

        public void setHostname(String hostname)
        {
            this.hostname = hostname;
        }

        public void setPort(String port)
        {
            this.port = port;
        }
    }

    public void defaults(SessionStoreConfiguration other)
    {
        this.defaultCryptoAlgorithm(other.getCryptoAlgorithm());
        this.defaultEnabled(other.isEnabled());
        this.defaultMaxSessionLength(other.getMaxSessionLength());
    }

    public void defaultCryptoAlgorithm(String cryptoAlgorithm)
    {
        if (this.cryptoAlgorithm.equals(DEFAULT_CRYPTO_ALGORITHM))
        {
            this.cryptoAlgorithm = cryptoAlgorithm;
        }
    }

    public void defaultEnabled(boolean enabled)
    {
        this.enabled = this.enabled || enabled;
    }

    public void defaultMaxSessionLength(int maxSessionLength)
    {
        if (this.maxSessionLength == DEFAULT_MAX_SESSION_LENGTH)
        {
            this.maxSessionLength = maxSessionLength;
        }
    }

    public String getCryptoAlgorithm()
    {
        return cryptoAlgorithm;
    }

    public int getMaxSessionLength()
    {
        return maxSessionLength;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setCryptoAlgorithm(String cryptoAlgorithm)
    {
        this.cryptoAlgorithm = cryptoAlgorithm;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public void setMaxSessionLength(int maxSessionLength)
    {
        this.maxSessionLength = maxSessionLength;
    }

    public MongodbConfiguration getMongodbConfiguration()
    {
        return mongodbConfiguration;
    }

    public void setMongodbConfiguration(MongodbConfiguration mongodbConfiguration)
    {
        this.mongodbConfiguration = mongodbConfiguration;
    }

    public RedisConfiguration getRedisConfiguration()
    {
        return redisConfiguration;
    }

    public void setRedisConfiguration(RedisConfiguration redisConfiguration)
    {
        this.redisConfiguration = redisConfiguration;
    }

}