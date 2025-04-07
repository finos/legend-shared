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
import java.util.Iterator;
import java.util.List;
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

  String clientNameParameter = Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER;
  String defaultClient;

  public LegendClientFinder(String defaultClient)
  {
    super();
    this.defaultClient = defaultClient;
  }

  public LegendClientFinder()
  {
    super();
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

    if (CommonHelper.isNotBlank(securityClientNames))
    {
      List<String> names = Arrays.asList(securityClientNames.split(","));
      String clientNameOnRequest = context.getRequestParameter(this.clientNameParameter).orElse(null);
      logger.debug("clientNameOnRequest: {}", clientNameOnRequest);
      String nameFound;
      if (clientNameOnRequest != null)
      {
        result = findUtil(clients, names, clientNameOnRequest);
      } else if (defaultClient != null)
      {
        logger.debug("defaultClient: {}", defaultClient);
        result = findUtil(clients, names, defaultClient);
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

    logger.debug("result: {}", result.stream().map((c) ->
    {
      return c.getName();
    }).collect(Collectors.toList()));
    return result;
  }

  public List<Client<? extends Credentials>> findUtil(Clients clients, List<String> names, String toFind)
  {
    List<Client<? extends Credentials>> result = new ArrayList<>();
    Client client = clients.findClient(toFind).get();
    String nameFound = client.getName();
    boolean found = false;
    Iterator var11 = names.iterator();

    while (var11.hasNext())
    {
      String name = (String) var11.next();
      if (CommonHelper.areEqualsIgnoreCaseAndTrim(name, nameFound))
      {
        result.add(client);
        found = true;
        break;
      }
    }

    if (!found)
    {
      throw new TechnicalException("Client not allowed: " + nameFound);
    }
    return result;
  }

  public String getDefaultClient()
  {
    return defaultClient;
  }
}
