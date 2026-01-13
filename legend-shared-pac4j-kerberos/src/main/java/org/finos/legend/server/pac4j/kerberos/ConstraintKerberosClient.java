// Copyright 2026 Goldman Sachs
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

package org.finos.legend.server.pac4j.kerberos;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.finos.legend.server.pac4j.SerializableProfile;
import org.pac4j.kerberos.client.direct.DirectKerberosClient;

@SuppressWarnings("unused")
@SerializableProfile
public class ConstraintKerberosClient extends DirectKerberosClient
{
  @JsonProperty
  private String servicePrincipal;

  @JsonProperty
  private String keyTabLocation;

  @Override
  public void clientInit()
  {
    defaultAuthenticator(
        new ConstraintKerberosAuthenticator(servicePrincipal, keyTabLocation));
    super.clientInit();
  }
}
