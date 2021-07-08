// Copyright 2021 Goldman Sachs
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

package org.finos.legend.server.pac4j.gitlab;

import org.pac4j.core.credentials.Credentials;

public class GitlabPersonalAccessTokenCredentials extends Credentials
{
    private String personalAccessToken;

    public GitlabPersonalAccessTokenCredentials(String personalAccessToken)
    {
        this.personalAccessToken = personalAccessToken;
    }

    public String getPersonalAccessToken()
    {
        return this.personalAccessToken;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        GitlabPersonalAccessTokenCredentials that = (GitlabPersonalAccessTokenCredentials) o;
        return this.personalAccessToken.equals(that.personalAccessToken);
    }

    @Override
    public int hashCode()
    {
        return this.personalAccessToken.hashCode();
    }
}
