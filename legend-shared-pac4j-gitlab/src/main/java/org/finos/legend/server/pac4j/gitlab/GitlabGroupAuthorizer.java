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

package org.finos.legend.server.pac4j.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.pac4j.core.authorization.authorizer.AbstractCheckAuthenticationAuthorizer;
import org.pac4j.core.context.WebContext;
import org.pac4j.oidc.profile.OidcProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class GitlabGroupAuthorizer extends AbstractCheckAuthenticationAuthorizer<OidcProfile>
{
  private static final Logger logger = LoggerFactory.getLogger(GitlabGroupAuthorizer.class);
  @JsonProperty
  private String group;

  @SuppressWarnings("unchecked")
  @Override
  protected boolean isProfileAuthorized(WebContext context, OidcProfile profile)
  {
    String id = profile.getId();
    List<String> groups = (List<String>) profile.getAttribute("groups");
    if (groups != null)
    {
      if (groups.contains(group))
      {
        logger.debug("Allowing user {} - groups includes {}", id, group);
        return true;
      } else
      {
        logger.warn("Disallowing user {} - groups does not include {}", id, group);
        return false;
      }
    }
    logger.warn("Disallowing user {} - no groups in profile", id);
    return false;
  }

  @Override
  public boolean isAuthorized(WebContext context, List<OidcProfile> profiles)
  {
    return isAnyAuthorized(context, profiles);
  }
}
