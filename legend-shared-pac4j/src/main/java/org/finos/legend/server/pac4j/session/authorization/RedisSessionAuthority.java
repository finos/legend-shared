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

package org.finos.legend.server.pac4j.session.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.StringUtils;
import org.bson.Document;
import org.finos.legend.server.pac4j.session.config.SessionStoreConfiguration;
import org.finos.legend.server.pac4j.session.config.SessionStoreConfiguration.RedisConfiguration;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.CommonProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;

import java.util.Map;

@SuppressWarnings("unused")
public class RedisSessionAuthority extends SessionAuthority
{
    public static final String NAME = "RedisSessionAuthority";
    private static final Logger logger = LoggerFactory.getLogger(RedisSessionAuthority.class);

    @JsonProperty
    private String port;

    private UnifiedJedis jedis;

    @Override
    protected boolean isProfileAuthorized(WebContext webContext, CommonProfile u)
    {
        String id = u.getId();

        Document doc = new Document((Map<String, Object>) jedis.jsonGet(id));
        if (doc != null)
        {
            logger.debug("Allowing user {} - found in Profile Authorization Store", id);
            return true;
        }
        else
        {
            logger.warn("Disallowing user {} - not found in Profile Authorization Store", id);
            return false;
        }
    }

    @Override
    public void configureDatabase(Object database, SessionStoreConfiguration config)
    {
        if (config == null)
        {
            throw new RuntimeException("Session store configuration is required for session authority");
        }

        RedisConfiguration redisConfiguration = config.getRedisConfiguration();

        if (redisConfiguration == null || StringUtils.isEmpty(redisConfiguration.getHostname()) || StringUtils.isEmpty(port))
        {
            throw new RuntimeException("Redis hostname and port name must be specified");
        }

        jedis = new JedisPooled(new HostAndPort(redisConfiguration.getHostname(), Integer.parseInt(port)));
    }

}