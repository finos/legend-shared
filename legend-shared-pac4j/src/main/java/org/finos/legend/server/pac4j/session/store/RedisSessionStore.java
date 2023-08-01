package org.finos.legend.server.pac4j.session.store;

import org.apache.commons.lang.StringUtils;
import org.bson.Document;
import org.finos.legend.server.pac4j.session.config.SessionStoreConfiguration;
import org.finos.legend.server.pac4j.session.config.SessionStoreConfiguration.RedisConfiguration;
import org.finos.legend.server.pac4j.session.utils.SessionToken;
import org.finos.legend.server.pac4j.session.utils.UuidUtils;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.json.Path;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class RedisSessionStore implements SessionStore
{
    private static long maxSessionLength;

    private JedisPooled jedis;

    public RedisSessionStore(RedisConfiguration config, long maxSessionLength)
    {
        validateConfiguration(config);

        jedis = new JedisPooled(new HostAndPort(config.getHostname(), Integer.parseInt(config.getPort())));

        this.maxSessionLength = maxSessionLength;
    }

    public void createIndex(long maxSessionLength, TimeUnit timeUnit)
    {
        // Redis doesn't require an index to manage expiration
    }

    public void createSession(SessionToken token)
    {
        String key = getSessionIdFromToken(token);

        Document value = new Document(SESSION_PROPERTY_ID, key).append(SessionStore.SESSION_PROPERTY_CREATED, new Date());

        Transaction transaction = new Transaction(jedis.getPool().getResource());

        transaction.jsonSet(key, Path.ROOT_PATH, value);
        transaction.expire(key, maxSessionLength);

        transaction.exec();
    }

    private Object createSession(String sessionId, String attributeKey, Object attributeValue)
    {
        Document doc = new Document(SESSION_PROPERTY_ID, sessionId)
                .append(SessionStore.SESSION_PROPERTY_CREATED, new Date())
                .append(attributeKey, attributeValue);

        Transaction transaction = new Transaction(jedis.getPool().getResource());

        transaction.jsonSet(sessionId, Path.ROOT_PATH, doc);
        transaction.expire(sessionId, maxSessionLength);

        return transaction.exec();
    }

    public Object deleteSession(SessionToken token)
    {
        return jedis.jsonDel(getSessionIdFromToken(token));
    }

    public Object getDatabaseClient()
    {
        return jedis;
    }

    public Object getSession(SessionToken token)
    {
        return jedis.jsonGet(getSessionIdFromToken(token));
    }

    public String getSessionAttribute(Object document, String attributeKey)
    {
        return String.valueOf(((Map<String, Object>) document).get(attributeKey));
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

    private String getSessionIdFromToken(SessionToken token)
    {
        return UuidUtils.toHexString(token.getSessionId());
    }

    private void validateConfiguration(RedisConfiguration redisConfiguration)
    {
        if (StringUtils.isEmpty(redisConfiguration.getHostname()))
        {
            throw new RuntimeException("Redis session store requires 'hostname' custom attribute to be configured");
        }

        if (StringUtils.isEmpty(redisConfiguration.getPort()))
        {
            throw new RuntimeException("Redis session store requires 'port' custom attribute to be configured");
        }
    }

}