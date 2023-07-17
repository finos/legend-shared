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

package org.finos.legend.server.pac4j.session.authorization;

import org.finos.legend.server.pac4j.session.consumer.SessionConsumer;
import org.pac4j.core.authorization.authorizer.AbstractCheckAuthenticationAuthorizer;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.CommonProfile;

import java.util.List;

@SuppressWarnings("unused")
public abstract class SessionAuthority extends AbstractCheckAuthenticationAuthorizer<CommonProfile>
        implements SessionConsumer
{

    @Override
    public boolean isAuthorized(WebContext context, List<CommonProfile> profiles)
    {
        return isAnyAuthorized(context, profiles);
    }

}