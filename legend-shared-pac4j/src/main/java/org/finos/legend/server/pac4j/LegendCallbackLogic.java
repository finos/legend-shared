// Copyright 2026 Goldman Sachs
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

package org.finos.legend.server.pac4j;

import org.pac4j.core.config.Config;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.engine.DefaultCallbackLogic;
import org.pac4j.core.profile.UserProfile;

/**
 * Applies the configured {@code multiProfile} at the callback, ignoring the per-client flag pac4j 6 reads.
 *
 * <p>The clients deliberately keep pac4j's default of {@code false}: setting it on them would make
 * non-browser calls run every default direct client instead of stopping at the first that authenticates.
 */
public class LegendCallbackLogic extends DefaultCallbackLogic
{
    private final boolean multiProfile;

    public LegendCallbackLogic(boolean multiProfile)
    {
        this.multiProfile = multiProfile;
    }

    public boolean isMultiProfile()
    {
        return this.multiProfile;
    }

    /**
     * Renews the session before saving the profile, not after as pac4j does: the SSO session stores move the
     * session to a new token on renewal, and the profile must not be written under the pre-sign-in token.
     */
    @Override
    protected void saveUserProfile(CallContext ctx, Config config, UserProfile profile, boolean saveInSession, boolean clientMultiProfile, boolean renewSession)
    {
        if (profile != null && renewSession)
        {
            renewSession(ctx, config);
        }
        super.saveUserProfile(ctx, config, profile, saveInSession, this.multiProfile, false);
    }
}
