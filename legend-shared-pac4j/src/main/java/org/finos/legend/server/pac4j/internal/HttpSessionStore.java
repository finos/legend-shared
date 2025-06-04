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

package org.finos.legend.server.pac4j.internal;

import java.util.Map;
import java.util.Optional;

import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;

public class HttpSessionStore implements SessionStore<WebContext>
{

  private final Map<Class<? extends WebContext>, SessionStore<? extends WebContext>>
      underlyingStores;

  public HttpSessionStore(
      Map<Class<? extends WebContext>, SessionStore<? extends WebContext>> underlyingStores)
  {
    this.underlyingStores = underlyingStores;
  }

  private SessionStore getUnderlyingSessionStore(WebContext context)
  {
    SessionStore<? extends WebContext> sessionStore = this.underlyingStores.get(context.getClass());
    if (sessionStore == null)
    {
      return NullSessionStore.INSTANCE;
    }
    return sessionStore;
  }

  @Override
  public String getOrCreateSessionId(WebContext context)
  {
    return getUnderlyingSessionStore(context).getOrCreateSessionId(context);
  }

  @Override
  public Optional<Object> get(WebContext context, String key)
  {
    return getUnderlyingSessionStore(context).get(context, key);
  }

  @Override
  public void set(WebContext context, String key, Object value)
  {
    getUnderlyingSessionStore(context).set(context, key, value);
  }

  @Override
  public boolean destroySession(WebContext context)
  {
    return getUnderlyingSessionStore(context).destroySession(context);
  }

  @Override
  public Optional getTrackableSession(WebContext context)
  {
    return getUnderlyingSessionStore(context).getTrackableSession(context);
  }

  @Override
  public Optional<SessionStore<WebContext>> buildFromTrackableSession(
      WebContext context, Object trackableSession)
  {
    return getUnderlyingSessionStore(context).buildFromTrackableSession(context, trackableSession);
  }

  @Override
  public boolean renewSession(WebContext context)
  {
    return getUnderlyingSessionStore(context).renewSession(context);
  }
}
