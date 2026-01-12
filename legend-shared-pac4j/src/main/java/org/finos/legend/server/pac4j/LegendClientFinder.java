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

package org.finos.legend.server.pac4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.client.finder.DefaultSecurityClientFinder;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.util.Pac4jConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class LegendClientFinder extends DefaultSecurityClientFinder
{
   private static final Logger logger = LoggerFactory.getLogger(DefaultSecurityClientFinder.class);
   public static final String CLIENT_TO_EXCLUDE = "ClientToExclude";

  String clientNameParameter = Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER;
  List<String> defaultClients;

  public LegendClientFinder(List<String> defaultClients)
  {
    super();
    this.defaultClients = defaultClients;
  }

  @Override
  public List<Client<? extends Credentials>> find(Clients clients, WebContext context, String clientNames)
  {
    List<Client<? extends Credentials>> result = new ArrayList<>();
    String securityClientNames = clientNames;
    logger.debug("Provided clientNames: {}", clientNames);
    if (clientNames == null)
    {
      securityClientNames = clients.getDefaultSecurityClients();
      logger.debug("Default security clients: {}", securityClientNames);
      if (securityClientNames == null && clients.findAllClients().size() == 1)
      {
        securityClientNames = ((Client) clients.getClients().get(0)).getName();
        logger.debug("Only client: {}", securityClientNames);
      }
    }
    Optional<String> kerbClientToExclude = context.getRequestAttribute(CLIENT_TO_EXCLUDE);

    if (CommonHelper.isNotBlank(securityClientNames))
    {
      List<String> names = Arrays.asList(securityClientNames.split(","));
      String clientNameOnRequest = context.getRequestParameter(this.clientNameParameter).orElse(null);
      logger.debug("clientNameOnRequest: {}", clientNameOnRequest);
      String nameFound;
      if (clientNameOnRequest != null)
      {
        result = findUtil(clients, names, Collections.singletonList(clientNameOnRequest), null);
      } else if (!defaultClients.isEmpty())
      {
        logger.debug("defaultClients: {}", defaultClients);
        logger.debug("Exclusion for Kerberos client, removing '{}' from default list", kerbClientToExclude);
        result = findUtil(clients, names, defaultClients, kerbClientToExclude.orElse(null));
      } else
      {
        Iterator var13 = names.iterator();

        while (var13.hasNext())
        {
          nameFound = (String) var13.next();
          Client client = clients.findClient(nameFound).get();
          result.add(client);
        }
      }
    }

    logger.debug("result: {}", result.stream().map(Client::getName).collect(Collectors.toList()));
    return result;
  }

  public List<Client<? extends Credentials>> findUtil(Clients clients, List<String> names, List<String> toFind, String clientToExclude)
  {
    return toFind.stream()
            .filter(requested -> !CommonHelper.areEqualsIgnoreCaseAndTrim(clientToExclude, requested))
            .map(requested -> names.stream()
                    .filter(allowed -> CommonHelper.areEqualsIgnoreCaseAndTrim(allowed, requested))
                    .findFirst()
                    .flatMap(clients::findClient)
                    .map(client -> (Client<? extends Credentials>) client)
                    .orElseThrow(() -> new TechnicalException("Client not found: " + requested)))
            .collect(Collectors.toList());
  }

  public List<String> getDefaultClients()
  {
    return defaultClients;
  }
}
