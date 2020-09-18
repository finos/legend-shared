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

package org.finos.legend.opentracing;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.codec.Encoding;
import zipkin2.reporter.Sender;

public class JerseyClientSender extends Sender
{

  private static final int messageMaxBytes = 5 * 1024 * 1024;
  private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(JerseyClientSender.class);
  private final Encoding encoding = Encoding.JSON;
  private final Client client = ClientBuilder.newClient();
  private final URI uri;
  private final AuthenticationProvider authenticationProvider;

  public JerseyClientSender(URI uri,
                            AuthenticationProvider authenticationProvider)
  {
    this.uri = uri;
    this.authenticationProvider = authenticationProvider;
  }

  @Override
  public Encoding encoding()
  {
    return encoding;
  }

  @Override
  public int messageMaxBytes()
  {
    return messageMaxBytes;
  }

  @Override
  public int messageSizeInBytes(List<byte[]> encodedSpans)
  {
    return encoding.listSizeInBytes(encodedSpans);
  }

  @Override
  public Call<Void> sendSpans(List<byte[]> encodedSpans)
  {
    Invocation.Builder request = client.target(uri).request();
    authenticationProvider.getAuthenticationHeaders().forEach(e -> request.header(e.name, e.value));
    Entity<String> json = Entity
        .json('[' + encodedSpans.stream().map(String::new).collect(
            Collectors.joining(",")) + ']');
    return new Executor(json, request);
  }

  private static class Executor extends Call<Void>
  {

    private final Entity<String> entity;
    private final Invocation.Builder request;
    private Future<Response> response;

    private Executor(Entity<String> entity, Builder request)
    {
      this.entity = entity;
      this.request = request;
    }

    @Override
    public Void execute()
    {
      try
      {
        Response response = request.post(entity);
        if (!response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL))
        {
          LOGGER.warn("Error sending tracing spans: " + response.getEntity());
        }
      } catch (Exception e)
      {
        LOGGER.warn("Error sending tracing spans", e);
      }

      return null;
    }

    @Override
    public void enqueue(Callback<Void> callback)
    {
      response = request.async().post(entity, new InvocationCallback<Response>()
      {
        @Override
        public void completed(Response response)
        {
          callback.onSuccess(null);
        }

        @Override
        public void failed(Throwable throwable)
        {
          callback.onError(throwable);
        }
      });
    }

    @Override
    public void cancel()
    {
      response.cancel(true);
    }

    @Override
    public boolean isCanceled()
    {
      return response.isCancelled();
    }

    @Override
    public Call<Void> clone()
    {
      return new Executor(entity, request);
    }
  }

}
