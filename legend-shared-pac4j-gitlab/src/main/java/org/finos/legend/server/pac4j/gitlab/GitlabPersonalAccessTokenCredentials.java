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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.pac4j.core.credentials.Credentials;

import java.util.Map;

public class GitlabPersonalAccessTokenCredentials extends Credentials
{
    private String personalAccessToken;
    private String userId;
    private String state;
    private String userName;

    protected GitlabPersonalAccessTokenCredentials(String personalAccessToken)
    {
        this.personalAccessToken = personalAccessToken;
    }

    public String getPersonalAccessToken()
    {
        return this.personalAccessToken;
    }

    public String getUserId()
    {
        return this.userId;
    }

    private void setUserId(String userId)
    {
        this.userId = userId;
    }

    public String getState()
    {
        return this.state;
    }

    private void setState(String state)
    {
        this.state = state;
    }

    public String getUserName()
    {
        return this.userName;
    }

    private void setUserName(String userName)
    {
        this.userName = userName;
    }

    protected void setCredentialWithResponse(Map<String, Object> jsonMap)
    {
        setState((String) jsonMap.get("state"));
        setUserName((String) jsonMap.get("username"));
        setUserId(jsonMap.get("id").toString());
    }


    @Override
    public boolean equals(Object o)
    {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode()
    {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
