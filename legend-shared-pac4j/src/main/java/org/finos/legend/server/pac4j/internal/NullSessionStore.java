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

import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;

import java.util.Optional;

public class NullSessionStore implements SessionStore<WebContext>
{

  public static final NullSessionStore INSTANCE = new NullSessionStore();

  private NullSessionStore()
  {
  }

  @Override
  public String getOrCreateSessionId(WebContext context)
  {
    return null;
  }

  @Override
  public Optional<Object> get(WebContext context, String key)
  {
    return Optional.empty();
  }

  @Override
  public void set(WebContext context, String key, Object value)
  {

  }

  @Override
  public boolean destroySession(WebContext context)
  {
    return false;
  }

  @Override
  public Optional getTrackableSession(WebContext context)
  {
    return Optional.empty();
  }

  @Override
  public Optional<SessionStore<WebContext>> buildFromTrackableSession(WebContext context,
                                                                      Object trackableSession)
  {
    return Optional.empty();
  }

  @Override
  public boolean renewSession(WebContext context)
  {
    return false;
  }
}
