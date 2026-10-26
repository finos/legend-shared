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

package org.finos.legend.server.pac4j.mongostore;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.finos.legend.server.pac4j.LegendPac4jBundle;
import org.finos.legend.server.pac4j.internal.HttpSessionStore;
import org.finos.legend.server.pac4j.kerberos.SubjectExecutor;
import org.finos.legend.server.pac4j.sessionutil.SessionToken;
import org.finos.legend.server.pac4j.sessionutil.UuidUtils;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.util.serializer.JavaSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MongoDbSessionStore extends HttpSessionStore
{
    private static final Logger logger = LoggerFactory.getLogger(MongoDbSessionStore.class);
    private static final String CREATED_FIELD = "created";
    private static final String ID_FIELD = "_id";
    /** Prefix of the request attributes caching, per session id, whether the collection holds that session. */
    private static final String STORED_ATTRIBUTE_PREFIX = MongoDbSessionStore.class.getName() + ".stored.";
    private final MongoCollection<Document> userSessions;
    private final SessionCrypt sessionCrypt;
    private final int maxSessionLength;
    private final JavaSerializer serializationHelper;
    private final SubjectExecutor subjectExecutor;

    private String sessionTokenName;
    private String sameSitePolicy = SessionToken.DEFAULT_SAME_SITE_POLICY;

    /**
     * Create MongoDb session store.
     *
     * @param algorithm        Crypto Algorithm for serialized data
     * @param maxSessionLength Expire data after
     * @param userSessions     Mongo Collection
     * @param underlyingStores Fallback stores
     */
    public MongoDbSessionStore(
            String algorithm, int maxSessionLength, MongoCollection<Document> userSessions,
            Map<Class<? extends WebContext>, SessionStore> underlyingStores, List<String> extraTrustedPackages, String sessionTokenName)
    {
        this(algorithm, maxSessionLength, userSessions, underlyingStores, new SubjectExecutor(null),extraTrustedPackages, sessionTokenName);
    }

    /**
     * Create MongoDb session store.
     *
     * @param algorithm        Crypto Algorithm for serialized data
     * @param maxSessionLength Expire data after
     * @param userSessions     Mongo Collection
     * @param underlyingStores Fallback stores
     * @param subjectExecutor  Execute DB actions using a Subject
     */
    public MongoDbSessionStore(
            String algorithm, int maxSessionLength, MongoCollection<Document> userSessions,
            Map<Class<? extends WebContext>, SessionStore> underlyingStores,
            SubjectExecutor subjectExecutor, List<String> extraTrustedPackages, String sessionTokenName)
    {
        super(underlyingStores);
        this.subjectExecutor = subjectExecutor;
        sessionCrypt = new SessionCrypt(algorithm);
        this.maxSessionLength = maxSessionLength;
        this.serializationHelper = LegendPac4jBundle.getSerializationHelper(extraTrustedPackages);
        this.subjectExecutor.execute((PrivilegedAction<Void>) () ->
        {
            userSessions.createIndex(
                    new Document(CREATED_FIELD, 1),
                    new IndexOptions().name("ttl").expireAfter((long) maxSessionLength, TimeUnit.SECONDS));
            return null;
        });
        this.userSessions = userSessions;
        this.sessionTokenName = sessionTokenName;
    }

    private SessionToken resolveSsoKey(WebContext context)
    {
        return SessionToken.resolveFromContextByIds(this.sessionTokenName, context, candidates ->
        {
            Set<UUID> stored = storedSessionIds(candidates);
            candidates.forEach(candidate -> rememberStored(context, candidate, stored.contains(candidate.getSessionId())));
            return stored;
        });
    }

    private Set<UUID> storedSessionIds(List<SessionToken> candidates)
    {
        List<String> ids = candidates.stream()
                .map(candidate -> UuidUtils.toHexString(candidate.getSessionId()))
                .collect(Collectors.toList());
        return this.subjectExecutor.execute(() ->
        {
            Set<UUID> stored = new HashSet<>();
            for (Document doc : userSessions.find(Filters.in(ID_FIELD, ids)).projection(new Document(ID_FIELD, 1)))
            {
                stored.add(UuidUtils.fromHexString(doc.getString(ID_FIELD)));
            }
            return stored;
        });
    }

    private boolean hasStoredSession(WebContext context, SessionToken token)
    {
        Optional<Object> known = context.getRequestAttribute(STORED_ATTRIBUTE_PREFIX + UuidUtils.toHexString(token.getSessionId()));
        if (known.isPresent())
        {
            return (Boolean) known.get();
        }
        boolean stored = this.subjectExecutor.execute(
                () -> userSessions.find(getSearchSpec(token)).projection(new Document(ID_FIELD, 1)).first()) != null;
        rememberStored(context, token, stored);
        return stored;
    }

    private static void rememberStored(WebContext context, SessionToken token, boolean stored)
    {
        context.setRequestAttribute(STORED_ATTRIBUTE_PREFIX + UuidUtils.toHexString(token.getSessionId()), stored);
    }

    private SessionToken getOrCreateSsoKey(WebContext context)
    {
        SessionToken token = resolveSsoKey(context);
        if (token == null)
        {
            token = createSsoKey(context);
        }
        return token;
    }

    private SessionToken createSsoKey(WebContext context)
    {
        return createSsoKey(context, SessionToken.generate(), new Document());
    }

    private SessionToken createSsoKey(WebContext context, SessionToken token, Document data)
    {
        token.issueInContext(this.sessionTokenName, context, maxSessionLength, this.sameSitePolicy);
        this.subjectExecutor.execute((PrivilegedAction<Void>) () ->
        {
            Document session = getSearchSpec(token).append(CREATED_FIELD, new Date());
            session.putAll(data);
            userSessions.insertOne(session);
            return null;
        });
        rememberStored(context, token, true);
        return token;
    }

    /**
     * Called at sign-in, before the profile is written. Moves the session to a new token, unless this request
     * minted it, so a token obtained or planted before sign-in does not carry the profile. The old row is
     * emptied only if this request could decrypt it: otherwise the cookie pairs someone else's session id
     * with a wrong key, and emptying it would log them out.
     *
     * <p>The HttpSession is renewed too: get() reads it first, so a JSESSIONID planted before sign-in would
     * carry the profile even under a new token.
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
        Document doc = this.subjectExecutor.execute(() -> userSessions.find(getSearchSpec(token)).first());
        if (doc == null)
        {
            createSsoKey(context);
            return;
        }
        Map<String, byte[]> data = new LinkedHashMap<>();
        boolean undecryptable = false;
        for (Map.Entry<String, Object> field : doc.entrySet())
        {
            if (!ID_FIELD.equals(field.getKey()) && field.getValue() instanceof String)
            {
                try
                {
                    data.put(field.getKey(), sessionCrypt.fromCryptedString((String) field.getValue(), token));
                } catch (GeneralSecurityException e)
                {
                    undecryptable = true;
                    break;
                }
            }
        }
        // A row with nothing to decrypt, e.g. one created by getSessionId(ctx, true), proves nothing: any key
        // would pass.
        boolean owned = !undecryptable && !data.isEmpty();
        SessionToken rotated = SessionToken.generate();
        Document moved = new Document();
        if (owned)
        {
            try
            {
                for (Map.Entry<String, byte[]> field : data.entrySet())
                {
                    moved.append(field.getKey(), sessionCrypt.toCryptedString(field.getValue(), rotated));
                }
            } catch (GeneralSecurityException e)
            {
                logger.warn("Unable to move session data to the rotated session token", e);
                moved = new Document();
            }
        }
        createSsoKey(context, rotated, moved);
        if (owned)
        {
            // Emptied, not deleted: a request still carrying the old token, sent before this response
            // arrived, would otherwise find no row and expire the cookie by name, deleting the new one.
            // The TTL index removes the row.
            Document unset = new Document();
            data.keySet().forEach(field -> unset.append(field, ""));
            this.subjectExecutor.execute(() -> userSessions.updateOne(getSearchSpec(token), new Document("$unset", unset)));
        }
    }

    private Document getSearchSpec(SessionToken token)
    {
        return new Document(ID_FIELD, UuidUtils.toHexString(token.getSessionId()));
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
        // bypassed paths, logout); minting a token there leaves a cookie and a Mongo row behind.
        // A session reached by its SSO cookie alone still exists, so that ProfileManager.removeProfiles
        // clears it. The answer is the HttpSession id where there is one; otherwise the SSO session id,
        // because creating an HttpSession to answer would leave one, and a JSESSIONID cookie, behind on
        // every node the user's logout or CSRF check reaches.
        Optional<String> httpSessionId = super.getSessionId(context, false);
        if (httpSessionId.isPresent())
        {
            return httpSessionId;
        }
        SessionToken token = resolveSsoKey(context);
        return token != null && hasStoredSession(context, token)
                ? Optional.of(UuidUtils.toHexString(token.getSessionId()))
                : Optional.empty();
    }

    @Override
    public Optional<Object> get(WebContext context, String key)
    {
        Object res = super.get(context, key).orElse(null);
        if (res == null)
        {
            // Must not mint a token: pac4j 6 ProfileManager.retrieveAll calls get() directly on
            // every request, including anonymous ones, without asking for a session id first.
            final SessionToken token = resolveSsoKey(context);
            if (token == null)
            {
                return Optional.empty();
            }
            Document doc = this.subjectExecutor.execute(() -> userSessions.find(getSearchSpec(token)).first());
            rememberStored(context, token, doc != null);
            if (doc != null)
            {
                String serialized = doc.getString(key);
                if (serialized != null)
                {
                    try
                    {
                        res =
                                serializationHelper.deserializeFromBytes(
                                        sessionCrypt.fromCryptedString(serialized, token));
                    } catch (GeneralSecurityException e)
                    {
                        // A wrong key, not unreadable data: the cookie pairs this session id with a key
                        // that did not encrypt it. Evicting here would let anyone who knows a session id
                        // log its owner out. A wrong key that happens to unpad yields bytes without the
                        // serialization header, which JavaSerializer turns into null, not an exception.
                        logger.warn("Unable to decrypt session data for key {}", key, e);
                    } catch (RuntimeException e)
                    {
                        // RuntimeException is deliberate: pac4j 6 dropped a field from the
                        // Externalizable BasicUserProfile without changing its serialVersionUID, so a
                        // pac4j 4/5 blob is mis-parsed and JavaSerializer throws ClassCastException.
                        // Unset the key, or the failure repeats on every request until the TTL expires.
                        logger.warn("Discarding unreadable session data for key {}", key, e);
                        final SessionToken evictFor = token;
                        this.subjectExecutor.execute((PrivilegedAction<Void>) () ->
                        {
                            userSessions.updateOne(getSearchSpec(evictFor),
                                    new Document("$unset", new Document(key, "")));
                            return null;
                        });
                        res = null;
                    }

                    if (res != null)
                    {
                        // Outside the try: a failure here (e.g. an invalidated HTTP session) says
                        // nothing about the stored bytes and must not evict them.
                        super.set(context, key, res);
                    }
                }
            }
            else
            {
                token.removeFromContext(this.sessionTokenName, context, this.sameSitePolicy); //force the token to expire because it doesn't match any credential in session store.
            }
        }
        else if (!SessionToken.isDestroyedInContext(this.sessionTokenName, context) && resolveSsoKey(context) == null)
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
        if (SessionToken.isDestroyedInContext(this.sessionTokenName, context))
        {
            return;
        }
        if (value instanceof Serializable)
        {
            final SessionToken token = getOrCreateSsoKey(context);
            Serializable serializable = (Serializable) value;
            byte[] serialized = serializationHelper.serializeToBytes(serializable);
            try
            {
                this.subjectExecutor.executeWithException(() -> userSessions.updateOne(
                        getSearchSpec(token),
                        new Document("$set", new Document(key, sessionCrypt.toCryptedString(serialized, token)))));
            } catch (PrivilegedActionException e)
            {
                logger.warn("Unable to serialize session data for user", e);
            }
        }
        super.set(context, key, value);
    }

    @Override
    public boolean destroySession(WebContext context)
    {
        // Not getOrCreateSsoKey: logout without a session must not mint a token and a Mongo row.
        final SessionToken token = resolveSsoKey(context);
        if (token != null)
        {
            token.destroyInContext(this.sessionTokenName, context, this.sameSitePolicy);
            this.subjectExecutor.execute(() -> userSessions.deleteMany(getSearchSpec(token)));
            rememberStored(context, token, false);
        }
        return super.destroySession(context);
    }

    public JavaSerializer getSerializationHelper()
    {
        return serializationHelper;
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
