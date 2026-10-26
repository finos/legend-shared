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
import com.hazelcast.nio.serialization.HazelcastSerializationException;
import org.finos.legend.server.pac4j.internal.HttpSessionStore;
import org.finos.legend.server.pac4j.sessionutil.SessionToken;
import org.finos.legend.server.pac4j.sessionutil.UuidUtils;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.InvalidClassException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class HazelcastSessionStore extends HttpSessionStore
{
    private static final Logger logger = LoggerFactory.getLogger(HazelcastSessionStore.class);

    private final IMap<UUID, Map<String, Object>> hazelcastMap;
    private String sessionTokenName;
    private String sameSitePolicy = SessionToken.DEFAULT_SAME_SITE_POLICY;

    public HazelcastSessionStore(String hazelcastConfigFilePath,
                                 Map<Class<? extends WebContext>, SessionStore> underlyingStores, String sessionTokenName)
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

    private SessionToken resolveSsoKey(WebContext context)
    {
        // containsKey, not get: it does not deserialize the value, which fails for an older pac4j's session.
        return SessionToken.resolveFromContext(this.sessionTokenName, context,
                candidate -> hazelcastMap.containsKey(candidate.getSessionId()));
    }

    private SessionToken getOrCreateSsoKey(WebContext context)
    {
        SessionToken token = resolveSsoKey(context);
        if (token == null)
        {
            token = createSsoKey(context, new HashMap<>());
        }
        return token;
    }

    private SessionToken createSsoKey(WebContext context, Map<String, Object> hazelcastSessionData)
    {
        SessionToken token = SessionToken.generate();
        token.issueInContext(this.sessionTokenName, context, -1, this.sameSitePolicy);
        hazelcastMap.put(token.getSessionId(), hazelcastSessionData);
        return token;
    }

    /**
     * Called at sign-in, before the profile is written. Moves the session to a new token, unless this request
     * minted it, so a token obtained or planted before sign-in does not carry the profile. An HttpSession,
     * which this store never creates, is renewed only where there is one.
     */
    @Override
    public boolean renewSession(WebContext context)
    {
        if (!SessionToken.isDestroyedInContext(this.sessionTokenName, context))
        {
            SessionToken token = resolveSsoKey(context);
            if (token != null && !token.isIssuedInContext(this.sessionTokenName, context))
            {
                rotate(context, token);
            }
        }
        return renewExistingHttpSession(context);
    }

    private void rotate(WebContext context, SessionToken token)
    {
        Map<String, Object> hazelcastSessionData;
        try
        {
            hazelcastSessionData = hazelcastMap.get(token.getSessionId());
        }
        catch (RuntimeException e)
        {
            if (!isUnreadableFormat(e))
            {
                throw e;
            }
            hazelcastSessionData = null;
        }
        createSsoKey(context, hazelcastSessionData == null ? new HashMap<>() : new HashMap<>(hazelcastSessionData));
        // delete(), not remove(): see destroySession.
        hazelcastMap.delete(token.getSessionId());
    }

    @Override
    public Optional<String> getSessionId(WebContext context, boolean createSession)
    {
        if (SessionToken.isDestroyedInContext(this.sessionTokenName, context))
        {
            return super.getSessionId(context, false);
        }
        if (createSession)
        {
            getOrCreateSsoKey(context);
            return super.getSessionId(context, true);
        }
        // pac4j 6 passes createSession=false on paths that must stay side-effect free (matchers,
        // bypassed paths, logout); minting a token there leaves a cookie and a map entry behind.
        // A session reached by its SSO cookie alone still exists, so that ProfileManager.removeProfiles
        // clears it. The answer is the HttpSession id where there is one; otherwise the SSO session id,
        // so answering creates nothing.
        Optional<String> httpSessionId = super.getSessionId(context, false);
        if (httpSessionId.isPresent())
        {
            return httpSessionId;
        }
        SessionToken token = resolveSsoKey(context);
        return token != null && hazelcastMap.containsKey(token.getSessionId())
                ? Optional.of(UuidUtils.toHexString(token.getSessionId()))
                : Optional.empty();
    }

    @Override
    public Optional<Object> get(WebContext context, String key)
    {
        // Must not mint a token: pac4j 6 ProfileManager.retrieveAll calls get() directly on every
        // request, including anonymous ones, without asking for a session id first.
        SessionToken token = resolveSsoKey(context);
        if (token == null)
        {
            return Optional.empty();
        }
        UUID sessionId = token.getSessionId();

        Map<String, Object> hazelcastSessionData;
        try
        {
            hazelcastSessionData = hazelcastMap.get(sessionId);
        }
        catch (RuntimeException e)
        {
            if (!isUnreadableFormat(e))
            {
                // Transient cluster failure (timeout, member left): the stored session is fine,
                // and evicting it would log the user out.
                throw e;
            }
            // Hazelcast deserializes on read, so a session written by pac4j 4/5 fails here: pac4j 6
            // changed the Externalizable layout of BasicUserProfile without changing its
            // serialVersionUID. The value is one blob, so it is replaced wholesale.
            // set(), not put(): put() returns the previous value, deserializing it and failing again.
            logger.warn("Discarding unreadable session data for session {}", sessionId, e);
            hazelcastMap.set(sessionId, new HashMap<>());
            return Optional.empty();
        }

        if (hazelcastSessionData != null)
        {
            Object data = hazelcastSessionData.get(key);
            if (data != null)
            {
                return Optional.of(data);
            }
            return Optional.empty();
        }

        // An evicted session carries on under its id. A token this store never issued is adopted the same
        // way, which is safe because sign-in rotates it (see renewSession).
        hazelcastMap.putIfAbsent(sessionId, new HashMap<>());
        return Optional.empty();
    }

    private static boolean isUnreadableFormat(RuntimeException e)
    {
        for (Throwable t = e; t != null; t = t.getCause())
        {
            if (t instanceof HazelcastSerializationException
                    || t instanceof ClassCastException
                    || t instanceof InvalidClassException)
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public void set(WebContext context, String key, Object value)
    {
        if (SessionToken.isDestroyedInContext(this.sessionTokenName, context))
        {
            return;
        }
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
        // Not getOrCreateSsoKey: logout without a session must not mint a token and a map entry.
        SessionToken token = resolveSsoKey(context);
        if (token != null)
        {
            token.destroyInContext(this.sessionTokenName, context, this.sameSitePolicy);
            // delete(), not remove(): remove() returns, and so deserializes, the previous value, which
            // throws for a session written by an older pac4j and would break logout.
            hazelcastMap.delete(token.getSessionId());
        }
        return super.destroySession(context);
    }

    /**
     * Strict, Lax or None (case-insensitive); anything else throws. Null or empty means
     * {@link SessionToken#DEFAULT_SAME_SITE_POLICY}.
     */
    public void setSameSitePolicy(String sameSitePolicy)
    {
        this.sameSitePolicy = SessionToken.sameSitePolicy(sameSitePolicy);
    }

    public String getSameSitePolicy()
    {
        return this.sameSitePolicy;
    }
}
