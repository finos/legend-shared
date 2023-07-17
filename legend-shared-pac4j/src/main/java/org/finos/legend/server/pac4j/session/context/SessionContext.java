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

package org.finos.legend.server.pac4j.session.context;

import org.bson.Document;
import org.finos.legend.server.pac4j.LegendPac4jBundle;
import org.finos.legend.server.pac4j.internal.HttpSessionStore;
import org.finos.legend.server.pac4j.kerberos.SubjectExecutor;
import org.finos.legend.server.pac4j.session.store.SessionStore;
import org.finos.legend.server.pac4j.session.utils.SessionCrypt;
import org.finos.legend.server.pac4j.session.utils.SessionToken;
import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileHelper;
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
import java.util.concurrent.TimeUnit;

public class SessionContext extends HttpSessionStore //TODO rename
{
  private static final Logger logger = LoggerFactory.getLogger(SessionContext.class);

  private final SessionStore userSessions;
  private final SessionCrypt sessionCrypt;
  private final int maxSessionLength;
  private final JavaSerializationHelper serializationHelper;
  private final SubjectExecutor subjectExecutor;

  /**
   * Create session store.
   *
   * @param algorithm        Crypto Algorithm for serialized data
   * @param maxSessionLength Expire data after
   * @param userSessions     Session Store
   * @param underlyingStores Fallback stores
   */
  public SessionContext(
          String algorithm, int maxSessionLength, SessionStore userSessions,
          Map<Class<? extends WebContext>, org.pac4j.core.context.session.SessionStore> underlyingStores, List<String> extraTrustedPackages)
  {
    this(algorithm, maxSessionLength, userSessions, underlyingStores, new SubjectExecutor(null),extraTrustedPackages);
  }

  /**
   * Create session store.
   *
   * @param algorithm        Crypto Algorithm for serialized data
   * @param maxSessionLength Expire data after
   * @param userSessions     Session store
   * @param underlyingStores Fallback stores
   * @param subjectExecutor  Execute DB actions using a Subject
   */
  public SessionContext(
          String algorithm, int maxSessionLength, SessionStore userSessions,
          Map<Class<? extends WebContext>, org.pac4j.core.context.session.SessionStore> underlyingStores,
          SubjectExecutor subjectExecutor, List<String> extraTrustedPackages)
  {
    super(underlyingStores);
    this.subjectExecutor = subjectExecutor;
    sessionCrypt = new SessionCrypt(algorithm);
    this.maxSessionLength = maxSessionLength;
    this.serializationHelper = LegendPac4jBundle.getSerializationHelper(extraTrustedPackages);
    this.subjectExecutor.execute((PrivilegedAction<Void>) () ->
    {
      userSessions.createIndex(maxSessionLength, TimeUnit.SECONDS);
      return null;
    });
    this.userSessions = userSessions;
  }

  private SessionToken getOrCreateSsoKey(WebContext context)
  {
    SessionToken token = SessionToken.fromContext(context);
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
    token.saveInContext(context, maxSessionLength);
    SessionToken finalToken = token;
    this.subjectExecutor.execute((PrivilegedAction<Void>) () ->
    {
      userSessions.createSession(finalToken);
      return null;
    });
    return token;
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
      final SessionToken token = getOrCreateSsoKey(context);
      Document doc = this.subjectExecutor.execute(() -> userSessions.getSession(token));
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
    else if (SessionToken.fromContext(context) == null)
    {
      //if res is not null ,this means we still have an active Session but an expired SSO cookie we need to recreate one and add it to the context request/response.
      createSsoKey(context);
      if (res instanceof LinkedHashMap)
      {
        ProfileHelper.flatIntoAProfileList((LinkedHashMap<String, CommonProfile>)res);
        set(context, Pac4jConstants.USER_PROFILES, ProfileHelper.flatIntoAProfileList((LinkedHashMap<String, CommonProfile>)res));
      }
    }
    return res;
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
        this.subjectExecutor.executeWithException(() -> userSessions.updateSession(
                                                        token, key, sessionCrypt.toCryptedString(serialized, token)));
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
    token.saveInContext(context, 0);
    this.subjectExecutor.execute(() -> userSessions.deleteSession(token));
    return super.destroySession(context);
  }

  public JavaSerializationHelper getSerializationHelper()
  {
    return serializationHelper;
  }
}
