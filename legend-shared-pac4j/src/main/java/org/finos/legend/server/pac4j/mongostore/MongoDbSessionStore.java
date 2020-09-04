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
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.finos.legend.server.pac4j.internal.HttpSessionStore;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.util.JavaSerializationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoDbSessionStore extends HttpSessionStore
{
  private static final Logger logger = LoggerFactory.getLogger(MongoDbSessionStore.class);
  private static final String CREATED_FIELD = "created";
  private static final String ID_FIELD = "_id";
  private final MongoCollection<Document> userSessions;
  private final SessionCrypt sessionCrypt;
  private final int maxSessionLength;
  private final JavaSerializationHelper serializationHelper = new JavaSerializationHelper();

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
      Map<Class<? extends WebContext>, SessionStore<? extends WebContext>> underlyingStores)
  {
    super(underlyingStores);
    serializationHelper.addTrustedPackage("org.finos.legend.server.pac4j.kerberos.");
    sessionCrypt = new SessionCrypt(algorithm);
    this.maxSessionLength = maxSessionLength;
    userSessions.createIndex(
        new Document(CREATED_FIELD, 1),
        new IndexOptions().name("ttl").expireAfter((long) maxSessionLength, TimeUnit.SECONDS));
    this.userSessions = userSessions;
  }

  private SessionToken getOrCreateSsoKey(WebContext context)
  {
    SessionToken token = SessionToken.fromContext(context);
    if (token == null)
    {
      token = SessionToken.generate();
      token.saveInContext(context, maxSessionLength);
      userSessions.insertOne(getSearchSpec(token).append(CREATED_FIELD, new Date()));
    }
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
  public Object get(WebContext context, String key)
  {
    Object res = super.get(context, key);
    if (res == null)
    {
      SessionToken token = getOrCreateSsoKey(context);
      Document doc = userSessions.find(getSearchSpec(token)).first();
      if (doc != null)
      {
        String serialized = doc.getString(key);
        if (serialized != null)
        {
          try
          {
            res =
                serializationHelper.unserializeFromBytes(
                    sessionCrypt.fromCryptedString(serialized, token));
            //Once we have it, store it in the regular session store for later access
            super.set(context, key, res);
          } catch (GeneralSecurityException e)
          {
            logger.warn("Unable to deserialize session data for user", e);
          }
        }
      }
    }
    return res;
  }

  @Override
  public void set(WebContext context, String key, Object value)
  {
    if (value instanceof Serializable)
    {
      SessionToken token = getOrCreateSsoKey(context);
      Serializable serializable = (Serializable) value;
      byte[] serialized = new JavaSerializationHelper().serializeToBytes(serializable);
      try
      {
        userSessions.updateOne(
            getSearchSpec(token),
            new Document(
                "$set", new Document(key, sessionCrypt.toCryptedString(serialized, token))));
      } catch (GeneralSecurityException e)
      {
        logger.warn("Unable to serialize session data for user", e);
      }
    }
    super.set(context, key, value);
  }

  @Override
  public boolean destroySession(WebContext context)
  {
    SessionToken token = getOrCreateSsoKey(context);
    token.saveInContext(context, 0);
    userSessions.deleteMany(getSearchSpec(token));
    return super.destroySession(context);
  }
}
