package org.finos.legend.server.pac4j.session.store;

import org.bson.Document;
import org.finos.legend.server.pac4j.session.config.SessionStoreConfiguration;
import org.finos.legend.server.pac4j.session.utils.SessionToken;
import org.finos.legend.server.pac4j.session.utils.UuidUtils;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.json.Path;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class RedisSessionStore extends SessionStore
{
    private static final String CUSTOM_CONFIG_REDIS_PORT = "port";

    private static long maxSessionLength;

    private UnifiedJedis jedis;

    public RedisSessionStore(SessionStoreConfiguration config)
    {
        validateCustomConfiguration(config.getCustomConfigurations());

        jedis = new JedisPooled(new HostAndPort(config.getDatabaseURI(),
                                Integer.parseInt(config.getCustomConfigurations().get(CUSTOM_CONFIG_REDIS_PORT))));

        maxSessionLength = config.getMaxSessionLength();
    }

    private void validateCustomConfiguration(Map<String, String> customConfigurations)
    {

        if (!customConfigurations.containsKey(CUSTOM_CONFIG_REDIS_PORT))
        {
            throw new RuntimeException("Redis session store requires 'port' custom attribute to be configured if enabled");
        }
    }

    public void createIndex(long maxSessionLength, TimeUnit timeUnit)
    {
        // Redis doesn't require an index to manage expiration
    }

    public void createSession(SessionToken token)
    {
        String key = getSessionIdFromToken(token);

        Document value = new Document(SESSION_PROPERTY_ID, key).append(SessionStore.SESSION_PROPERTY_CREATED, new Date());

        jedis.jsonSet(key, Path.ROOT_PATH, value); //TODO check on transactions
        jedis.expire(key, maxSessionLength);
    }

    private String createSession(String sessionId, String attributeKey, Object attributeValue)
    {
        Document doc = new Document(SESSION_PROPERTY_ID, sessionId)
                .append(SessionStore.SESSION_PROPERTY_CREATED, new Date())
                .append(attributeKey, attributeValue);

        String result = jedis.jsonSet(sessionId, Path.ROOT_PATH, doc); //TODO check on transactions
        jedis.expire(sessionId, maxSessionLength);

        return result;
    }

    public Object deleteSession(SessionToken token)
    {
        return jedis.jsonDel(getSessionIdFromToken(token));
    }

    public Object getDatabase()
    {
        return jedis;
    }

    private String getSessionIdFromToken(SessionToken token)
    {
        return UuidUtils.toHexString(token.getSessionId());
    }

    public Document getSession(SessionToken token)
    {
        Object jsonStringResult = jedis.jsonGet(getSessionIdFromToken(token));
        if (jsonStringResult == null)
        {
            return null;
        }

        return new Document((Map<String, Object>) jsonStringResult);
    }

    public Object updateSession(SessionToken token, String key, Object value)
    {
        String sessionId = getSessionIdFromToken(token);

        if (!jedis.exists(sessionId))
        {
            return createSession(sessionId, key, value);
        }

        return jedis.jsonSet(sessionId, new Path(key), value);
    }

}