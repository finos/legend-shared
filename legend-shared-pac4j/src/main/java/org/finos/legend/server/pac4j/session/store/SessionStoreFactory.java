package org.finos.legend.server.pac4j.session.store;

import org.finos.legend.server.pac4j.session.config.SessionStoreConfiguration;

public enum SessionStoreFactory
{
    INSTANCE;

    public SessionStore getInstance(SessionStoreConfiguration config)
    {
        if (config.getMongodbConfiguration() != null)
        {
            return new MongoDbSessionStore(config.getMongodbConfiguration());
        }
        else if (config.getRedisConfiguration() != null)
        {
            return new RedisSessionStore(config.getRedisConfiguration(), config.getMaxSessionLength());
        }
        else
        {
            throw new RuntimeException("Either mongodb or redis must be configured if session store is enabled");
        }
    }

}