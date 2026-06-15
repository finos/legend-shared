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
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.IMap;
import org.finos.legend.server.pac4j.internal.HttpSessionStore;
import org.finos.legend.server.pac4j.sessionutil.SessionToken;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;

import java.io.FileNotFoundException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class HazelcastSessionStore extends HttpSessionStore
{

    private final IMap<UUID, Map<String, Object>> hazelcastMap;
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
            if (mapConfigs.size() == 1)
            {
                MapConfig hazelcastMapConfig = optionalMapConfig.get();
                this.hazelcastMap = hazelcastInstance.getMap(hazelcastMapConfig.getName());

            }
            else
            {
                throw new IllegalStateException(
                        "The Hazelcast config needs to include exactly one Map Configuration");
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
        token.saveInContext(this.sessionTokenName, context, -1);
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
        SessionToken token = getOrCreateSsoKey(context);
        UUID sessionId = token.getSessionId();

        Map<String, Object> hazelcastSessionData = hazelcastMap.get(sessionId);

        if (hazelcastSessionData != null)
        {
            Object data = hazelcastSessionData.get(key);
            if (data != null)
            {
                return Optional.of(data);
            }
            return Optional.empty();
        }

        hazelcastMap.putIfAbsent(sessionId, new HashMap<>());
        return Optional.empty();
    }

    @Override
    public void set(WebContext context, String key, Object value)
    {
        SessionToken token = getOrCreateSsoKey(context);
        UUID sessionId = token.getSessionId();

        hazelcastMap.executeOnKey(sessionId, new SessionDataUpdater(key, value));
    }

    private static class SessionDataUpdater
            implements EntryProcessor<UUID, Map<String, Object>, Void>, Serializable
    {
        private static final long serialVersionUID = 1L;

        private final String key;
        private final Object value;

        SessionDataUpdater(String key, Object value)
        {
            this.key = key;
            this.value = value;
        }

        @Override
        public Void process(Map.Entry<UUID, Map<String, Object>> entry)
        {
            Map<String, Object> data = entry.getValue();
            if (data == null)
            {
                data = new HashMap<>();
            }
            data.put(key, value);
            entry.setValue(data);
            return null;
        }
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
