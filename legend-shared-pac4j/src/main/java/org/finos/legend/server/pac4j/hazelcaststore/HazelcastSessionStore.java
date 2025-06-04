// Copyright 2023 Goldman Sachs
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

package org.finos.legend.server.pac4j.hazelcaststore;

import com.hazelcast.config.FileSystemYamlConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.finos.legend.server.pac4j.internal.HttpSessionStore;
import org.finos.legend.server.pac4j.sessionutil.SessionToken;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.util.Pac4jConstants;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class HazelcastSessionStore extends HttpSessionStore
{

    private final Map<UUID, Map<String, Object>> hazelcastMap;
    private final int maxSessionLength;
    private String sessionTokenName;

    public HazelcastSessionStore(String hazelcastConfigFilePath,
                                 Map<Class<? extends WebContext>, SessionStore<? extends WebContext>> underlyingStores, String sessionTokenName)
    {
        super(underlyingStores);
        this.sessionTokenName = sessionTokenName;
        try
        {
            FileSystemYamlConfig fileConfig = new FileSystemYamlConfig(hazelcastConfigFilePath);
            HazelcastInstance hazelcastInstance = Hazelcast.getOrCreateHazelcastInstance(fileConfig);

            Collection<MapConfig> mapConfigs = fileConfig.getMapConfigs().values();
            Optional<MapConfig> optionalMapConfig = mapConfigs.stream().findFirst();
            if (mapConfigs.size() == 1 && optionalMapConfig.get().getTimeToLiveSeconds() != 0)
            {
                MapConfig hazelcastMapConfig = optionalMapConfig.get();
                this.hazelcastMap = hazelcastInstance.getMap(hazelcastMapConfig.getName());
                this.maxSessionLength = hazelcastMapConfig.getTimeToLiveSeconds();
            }
            else
            {
                throw new IllegalStateException(
                        "The Hazelcast config needs to include exactly one Map Configuration with a TTL seconds value");
            }
        }
        catch (FileNotFoundException e)
        {
            throw new UncheckedIOException(
                    "Failed to find Hazelcast config file in specified path: " + hazelcastConfigFilePath, e);
        }
    }

    private SessionToken getOrCreateSsoKey(WebContext context)
    {
        SessionToken token = SessionToken.fromContext(this.sessionTokenName, context);
        if (token == null)
        {
            token = createSsoKey(context);
        }
        return token;
    }

    private SessionToken createSsoKey(WebContext context)
    {
        SessionToken token = SessionToken.generate();
        token.saveInContext(this.sessionTokenName,context, maxSessionLength);
        Map<String, Object> hazelcastSessionData = new HashMap<>();
        hazelcastMap.put(token.getSessionId(), hazelcastSessionData);
        return token;
    }

    @Override
    public String getOrCreateSessionId(WebContext context)
    {
        getOrCreateSsoKey(context);
        return super.getOrCreateSessionId(context);
    }

    @Override
    public Optional<Object> get(WebContext context, String key)
    {
        Object res = super.get(context, key).orElse(null);
        if (res == null)
        {
            SessionToken token = getOrCreateSsoKey(context);
            Map<String, Object> hazelcastSessionData = hazelcastMap.get(token.getSessionId());
            if (hazelcastSessionData != null)
            {
                Object data = hazelcastSessionData.get(key);
                if (data != null)
                {
                    res = data;
                    //Once we have it, store it in the regular session store for later access
                    super.set(context, key, res);
                }
            }
        }
        else if (SessionToken.fromContext(this.sessionTokenName, context) == null)
        {
            // if res is not null, this means we still have an active Session but an expired SSO cookie
            // we need to recreate one and add it to the context request/response
            createSsoKey(context);
            if (res instanceof LinkedHashMap)
            {
                set(context, Pac4jConstants.USER_PROFILES, res);
            }
        }
        return Optional.ofNullable(res);
    }

    @Override
    public void set(WebContext context, String key, Object value)
    {
        SessionToken token = getOrCreateSsoKey(context);
        Map<String, Object> hazelcastSessionData = hazelcastMap.get(token.getSessionId());
        if (hazelcastSessionData != null)
        {
            hazelcastSessionData.put(key, value);

            // need to write the object back into hazelcast
            hazelcastMap.put(token.getSessionId(), hazelcastSessionData);
        }

        super.set(context, key, value);
    }

    @Override
    public boolean destroySession(WebContext context)
    {
        SessionToken token = getOrCreateSsoKey(context);
        token.saveInContext(this.sessionTokenName, context, 0);
        hazelcastMap.remove(token.getSessionId());
        return super.destroySession(context);
    }
}
