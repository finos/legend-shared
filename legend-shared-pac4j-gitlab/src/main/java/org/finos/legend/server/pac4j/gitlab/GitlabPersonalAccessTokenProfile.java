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

import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.core.profile.CommonProfile;

public class GitlabPersonalAccessTokenProfile extends CommonProfile
{
    private static final String PERSONAL_ACCESS_TOKEN = "personalAccessToken";
    private static final String GITLAB_HOST = "gitlabHost";

    public GitlabPersonalAccessTokenProfile()
    {
        // no-arg constructor for Externalizable
    }

    public GitlabPersonalAccessTokenProfile(String token, String userId, String username, String gitlabHost)
    {
        setId(userId);
        addAttribute(Pac4jConstants.USERNAME, username);
        addAttribute(PERSONAL_ACCESS_TOKEN, token);
        addAttribute(GITLAB_HOST, gitlabHost);
    }

    public String getPersonalAccessToken()
    {
        return (String) getAttribute(PERSONAL_ACCESS_TOKEN);
    }

    public String getGitlabHost()
    {
        return (String) getAttribute(GITLAB_HOST);
    }
}
