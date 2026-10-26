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

package org.finos.legend.server.pac4j;

import org.pac4j.core.client.DirectClient;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.profile.creator.AuthenticatorProfileCreator;

import java.util.Optional;

@SerializableProfile
public class TestClient extends DirectClient
{
    public TestClient()
    {
    }

    public TestClient(String name)
    {
        this.setName(name);
    }

    /**
     * Never finds credentials, so pac4j answers 401 rather than failing to initialise the client.
     */
    public static TestClient withoutCredentials(String name)
    {
        TestClient client = new TestClient(name);
        client.setCredentialsExtractor(ctx -> Optional.empty());
        client.setAuthenticator(Authenticator.ALWAYS_VALIDATE);
        client.setProfileCreator(AuthenticatorProfileCreator.INSTANCE);
        return client;
    }

    @Override
  protected void internalInit(boolean forceReinit)
  {

  }
}
