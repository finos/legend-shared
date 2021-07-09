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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.CredentialsException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class GitlabPersonalAccessTokenAuthenticator implements Authenticator<GitlabPersonalAccessTokenCredentials>
{

    private String apiVersion;
    private String host;
    private String scheme;

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
            URL url = new URL(this.scheme, this.host, "/api/" + this.apiVersion + "/user?private_token=" + credentials.getPersonalAccessToken());
            HttpURLConnection.setFollowRedirects(false);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type","application/json");
            if (connection.getResponseCode() != 200)
            {
                throw new CredentialsException("Status Code: " + connection.getResponseCode() + " and Error Message: " + connection.getResponseMessage());
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(connection.getInputStream(), Map.class);
            credentials.setState((String) jsonMap.get("state"));
            credentials.setUserName((String) jsonMap.get("username"));
            credentials.setUserId(jsonMap.get("id").toString());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e.getMessage());
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }
}
