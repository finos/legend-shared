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

import java.util.List;
import java.util.Optional;
import org.pac4j.core.client.BaseClient;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;

/**
 * Saves a profile in the session only when the storage decision, asked over the clients in play, also
 * says so; pac4j 6 alone saves on the client's flag, so a profile could be saved over one client list and
 * never read back over another. The flag can still veto. Until {@link #setCurrentClients} is called,
 * saving is left to the caller.
 *
 * <p>Saving a profile in the session is a direct client's sign-in, so the session is renewed first, once
 * per request, as pac4j's callback renews it for an indirect client: a session obtained or planted before
 * sign-in must not carry the profile. As there, the clients are told the old session id, so one that
 * tracks the session for single logout (OidcClient) follows it to the new one.
 */
class StorageDecisionProfileManager extends ProfileManager
{
    private final LegendUserProfileStorageDecision storageDecision;
    private List<Client> currentClients;
    private boolean renewed;

    StorageDecisionProfileManager(WebContext context, SessionStore sessionStore, LegendUserProfileStorageDecision storageDecision)
    {
        super(context, sessionStore);
        this.storageDecision = storageDecision;
    }

    void setCurrentClients(List<Client> currentClients)
    {
        this.currentClients = currentClients;
    }

    @Override
    public void save(boolean saveInSession, UserProfile profile, boolean multiProfile)
    {
        DirectClient client = currentClients == null ? null : currentClients.stream()
                .filter(c -> c instanceof DirectClient && c.getName().equals(profile.getClientName()))
                .map(c -> (DirectClient) c)
                .findFirst()
                .orElse(null);
        boolean decided = client == null || storageDecision.mustSaveProfileInSession(context, currentClients, client, profile);
        boolean inSession = saveInSession && decided;
        if (inSession && !renewed)
        {
            renewSession();
            renewed = true;
        }
        super.save(inSession, profile, multiProfile);
    }

    /**
     * As DefaultCallbackLogic.renewSession, except that no session is created just to have an old id:
     * without one, nothing can have been recorded against it.
     */
    private void renewSession()
    {
        Optional<String> oldSessionId = sessionStore.getSessionId(context, false);
        if (sessionStore.renewSession(context) && oldSessionId.isPresent() && config != null && config.getClients() != null)
        {
            CallContext ctx = new CallContext(context, sessionStore);
            for (Client client : config.getClients().getClients())
            {
                ((BaseClient) client).notifySessionRenewal(ctx, oldSessionId.get());
            }
        }
    }
}
