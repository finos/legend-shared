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

import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.extractor.CredentialsExtractor;
import org.pac4j.core.exception.CredentialsException;

import java.util.Optional;

public class GitlabPersonalAccessTokenExtractor implements CredentialsExtractor<GitlabPersonalAccessTokenCredentials>
{
    private final String headerTokenName;

    public GitlabPersonalAccessTokenExtractor(String headerTokenName)
    {
        this.headerTokenName = headerTokenName;
    }

    @Override
    public Optional<GitlabPersonalAccessTokenCredentials> extract(WebContext webContext)
    {
        String personalAccessToken = webContext.getRequestHeader(this.headerTokenName).orElse(null);
        if (personalAccessToken == null)
        {
            throw new CredentialsException("Unable to retrieve token from the header with token name: " + this.headerTokenName);
        }
        return Optional.of(new GitlabPersonalAccessTokenCredentials(personalAccessToken));
    }
}
