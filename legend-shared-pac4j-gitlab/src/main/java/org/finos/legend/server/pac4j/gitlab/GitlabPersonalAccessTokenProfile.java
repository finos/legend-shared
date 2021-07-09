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

import org.pac4j.core.profile.CommonProfile;

public class GitlabPersonalAccessTokenProfile extends CommonProfile
{
    public GitlabPersonalAccessTokenProfile(String token, String userId, String username, String state)
    {
        super.setId(userId);
        super.addAttribute("username", username);
        super.addAttribute("personalAccessToken", token);
        super.addAttribute("state",state);
    }

    public String getPersonalAccessToken()
    {
        return (String) this.getAttribute("personalAccessToken");
    }

    public String getState()
    {
        return (String) this.getAttribute("state");
    }
}
