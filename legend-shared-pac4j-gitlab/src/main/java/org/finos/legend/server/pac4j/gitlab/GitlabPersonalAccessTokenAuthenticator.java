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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.CredentialsException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class GitlabPersonalAccessTokenAuthenticator implements Authenticator<GitlabPersonalAccessTokenCredentials>
{
    private final String apiVersion;
    private final String host;
    private final String scheme;
    private final ObjectReader reader;

    public GitlabPersonalAccessTokenAuthenticator(String scheme, String gitlabHost, String gitlabApiVersion)
    {
        this.scheme = scheme;
        this.host = gitlabHost;
        this.apiVersion = gitlabApiVersion;
        this.reader = JsonMapper.builder().build().readerFor(UserInformation.class);
    }

    @Override
    public void validate(GitlabPersonalAccessTokenCredentials credentials, WebContext webContext)
    {
        UserInformation userInfo = getUserInformation(credentials.getPersonalAccessToken());
        credentials.setUserId(userInfo.username);
        credentials.setUserName(userInfo.name);
    }

    private UserInformation getUserInformation(String personalAccessToken)
    {
        HttpURLConnection connection = null;
        try
        {
            URL url = new URL(this.scheme, this.host, "/api/" + this.apiVersion + "/user");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("PRIVATE-TOKEN", personalAccessToken);
            connection.setInstanceFollowRedirects(false);
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK)
            {
                throw new CredentialsException("Status Code: " + responseCode);
            }
            return this.reader.readValue(connection.getInputStream());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class UserInformation
    {
        @JsonProperty("username")
        private String username;

        @JsonProperty("name")
        private String name;
    }
}
