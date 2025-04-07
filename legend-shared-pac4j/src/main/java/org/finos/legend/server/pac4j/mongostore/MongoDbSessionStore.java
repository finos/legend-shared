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
import org.pac4j.core.util.JavaSerializationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class MongoDbSessionStore extends HttpSessionStore
{
    private static final Logger logger = LoggerFactory.getLogger(MongoDbSessionStore.class);
    private static final String CREATED_FIELD = "created";
    private static final String ID_FIELD = "_id";
    private final MongoCollection<Document> userSessions;
    private final SessionCrypt sessionCrypt;
    private final int maxSessionLength;
    private final JavaSerializationHelper serializationHelper;
    private final SubjectExecutor subjectExecutor;

    private String sessionTokenName;

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
            Map<Class<? extends WebContext>, SessionStore<? extends WebContext>> underlyingStores, List<String> extraTrustedPackages, String sessionTokenName)
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
            Map<Class<? extends WebContext>, SessionStore<? extends WebContext>> underlyingStores,
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
        SessionToken token;
        token = SessionToken.generate();
        token.saveInContext(this.sessionTokenName, context, maxSessionLength);
        SessionToken finalToken = token;
        this.subjectExecutor.execute((PrivilegedAction<Void>) () ->
        {
            userSessions.insertOne(getSearchSpec(finalToken).append(CREATED_FIELD, new Date()));
            return null;
        });
        return token;
    }

    private Document getSearchSpec(SessionToken token)
    {
        return new Document(ID_FIELD, UuidUtils.toHexString(token.getSessionId()));
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
            final SessionToken token = getOrCreateSsoKey(context);
            Document doc = this.subjectExecutor.execute(() -> userSessions.find(getSearchSpec(token)).first());
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
                        //Once we have it, store it in the regular session store for later access
                        super.set(context, key, res);
                    } catch (GeneralSecurityException e)
                    {
                        logger.warn("Unable to deserialize session data for user", e);
                    }
                }
            }
            else
            {
                token.removeFromContext(this.sessionTokenName, context); //force the token to expire because it doesn't match any credential in session store.
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
        if (value instanceof Serializable)
        {
            final SessionToken token = getOrCreateSsoKey(context);
            Serializable serializable = (Serializable) value;
            byte[] serialized = new JavaSerializationHelper().serializeToBytes(serializable);
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
        final SessionToken token = getOrCreateSsoKey(context);
        token.saveInContext(this.sessionTokenName, context, 0);
        this.subjectExecutor.execute(() -> userSessions.deleteMany(getSearchSpec(token)));
        return super.destroySession(context);
    }

    public JavaSerializationHelper getSerializationHelper()
    {
        return serializationHelper;
    }
}
