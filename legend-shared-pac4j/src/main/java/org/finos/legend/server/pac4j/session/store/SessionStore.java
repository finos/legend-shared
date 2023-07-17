package org.finos.legend.server.pac4j.session.store;

import org.apache.commons.lang.StringUtils;
import org.bson.Document;
import org.finos.legend.server.pac4j.session.config.SessionStoreConfiguration;
import org.finos.legend.server.pac4j.session.utils.SessionToken;

import java.util.concurrent.TimeUnit;

public abstract class SessionStore
{
    public static final String SESSION_PROPERTY_ID = "_id";
    protected static final String SESSION_PROPERTY_CREATED = "created";

    public static SessionStore createInstance(SessionStoreConfiguration config)
    {
        validateSessionStoreConfiguration(config);

        String type = config.getType();

        if (SessionStoreTypes.mongoDb.name().equals(type))
        {
            return new MongoDbSessionStore(config);
        }
        else if (SessionStoreTypes.redis.name().equals(type))
        {
            return new RedisSessionStore(config);
        }
        return null;
    }

    private static void validateSessionStoreConfiguration(SessionStoreConfiguration config)
    {
        if (StringUtils.isEmpty(config.getType()))
        {
            throw new RuntimeException("Session store requires 'type' attribute to be configured if enabled");
        }

        if (StringUtils.isEmpty(config.getDatabaseURI()))
        {
            throw new RuntimeException("Session store requires 'databaseURI' attribute to be configured if enabled");
        }
    }

    public abstract void createIndex(long maxSessionLength, TimeUnit seconds);

    public abstract void createSession(SessionToken token);

    public abstract Object deleteSession(SessionToken token);

    public abstract Object getDatabase();

    public abstract Document getSession(SessionToken token);

    public abstract Object updateSession(SessionToken token, String key, Object value);

    enum SessionStoreTypes
    {
        mongoDb, redis
    }

}