package org.finos.legend.server.pac4j;

import org.pac4j.core.client.DirectClient;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.profile.CommonProfile;

public class SecondTestClient extends DirectClient<Credentials> {

  @Override
  protected void clientInit()
  {

  }

}
