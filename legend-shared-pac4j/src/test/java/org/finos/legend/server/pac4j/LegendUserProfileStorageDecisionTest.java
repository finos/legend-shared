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

import org.junit.Test;
import org.pac4j.core.client.Client;
import org.pac4j.core.context.JEEContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class LegendUserProfileStorageDecisionTest
{

    @Test
    public void shouldReturnTrueOnlyIfAllTheClientsAreAnnotatedWithSerializableProfile()
    {
      LegendUserProfileStorageDecision<JEEContext> decision = new LegendUserProfileStorageDecision<>();
      List<Client> clientList = new ArrayList<>();
      clientList.add(new TestClient());
      assertTrue(decision.mustSaveProfileInSession(null,clientList,null,null));
    }

    @Test
    public void shouldReturnFalseIfAnyOfTheClientsAreNotAnnotatedWithSerializableProfile()
    {
        LegendUserProfileStorageDecision<JEEContext> decision = new LegendUserProfileStorageDecision<>();
        List<Client> clientList = new ArrayList<>();
        clientList.add(new TestClient());
        clientList.add(new SecondTestClient());
        assertFalse(decision.mustSaveProfileInSession(null,clientList,null,null));
    }

    @Test
    public void shouldReturnTrueOnlyIfAllTheClientsAreAnnotatedWithSerializableProfileForMustLoadProfileFromSession()
    {
        LegendUserProfileStorageDecision<JEEContext> decision = new LegendUserProfileStorageDecision<>();
        List<Client> clientList = new ArrayList<>();
        clientList.add(new TestClient());
        assertTrue(decision.mustLoadProfilesFromSession(null,clientList));
    }

    @Test
    public void shouldReturnFalseIfAnyOfTheClientsAreNotAnnotatedWithSerializableProfileForMustLoadProfileFromSession()
    {
        LegendUserProfileStorageDecision<JEEContext> decision = new LegendUserProfileStorageDecision<>();
        List<Client> clientList = new ArrayList<>();
        clientList.add(new TestClient());
        clientList.add(new SecondTestClient());
        assertFalse(decision.mustLoadProfilesFromSession(null,clientList));
    }

}