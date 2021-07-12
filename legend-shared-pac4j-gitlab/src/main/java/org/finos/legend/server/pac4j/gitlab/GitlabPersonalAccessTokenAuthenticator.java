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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.CredentialsException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GitlabPersonalAccessTokenAuthenticator implements Authenticator<GitlabPersonalAccessTokenCredentials>
{

    private String apiVersion;
    private String host;
    private String scheme;
    private static final ObjectMapper mapper = new ObjectMapper();

    public GitlabPersonalAccessTokenAuthenticator(String scheme, String gitlabHost, String gitlabApiVersion)
    {
        this.scheme = scheme;
        this.host = gitlabHost;
        this.apiVersion = gitlabApiVersion;
    }

    @Override
    public void validate(GitlabPersonalAccessTokenCredentials credentials, WebContext webContext)
    {
        HttpURLConnection connection = null;
        try
        {
            URL url = new URL(this.scheme, this.host, "/api/" + this.apiVersion + "/user");
            HttpURLConnection.setFollowRedirects(false);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("PRIVATE-TOKEN", credentials.getPersonalAccessToken());
            if (connection.getResponseCode() != 200)
            {
                HttpURLConnection tempConnection = connection;
                ByteSource byteSource = new ByteSource()
                {
                    @Override
                    public InputStream openStream()
                    {
                        return tempConnection.getErrorStream();
                    }
                };
                throw new CredentialsException("Status Code: " + tempConnection.getResponseCode() + " and Error Message: " + byteSource.asCharSource(Charsets.UTF_8).read());
            }
            UserInformation userInfo = mapper.readValue(connection.getInputStream(), UserInformation.class);
            credentials.setUserId(userInfo.getId());
            credentials.setUserName(userInfo.getUsername());
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
    private class UserInformation
    {
        @JsonProperty("username")
        private String username;

        @JsonProperty("id")
        private String id;

        private String getUsername()
        {
            return this.username;
        }

        private String getId()
        {
            return this.id;
        }
    }
}
