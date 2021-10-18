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

package org.finos.legend.server.shared.bundles;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.opentracing.log.Fields;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import org.junit.After;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class OpenTracingBundleTest
{
  private static final CyclicBarrier REQ_BARR = new CyclicBarrier(2);

  private static final MockTracer MOCK_TRACER = new MockTracer()
  {
    @Override
    protected void onSpanFinished(MockSpan mockSpan)
    {
      // sync with caller when is root span
      // from client perspective, jersey will complete request while internally still executing some post-request filters and interceptors
      // and these need to complete for the span to be finished
      if (mockSpan.tags().get("serverHost") != null)
      {
        try
        {
          REQ_BARR.await(2, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    }
  };

  @ClassRule
  public static final DropwizardAppRule<TestConfig> RULE =
      new DropwizardAppRule<>(TestApp.class, ResourceHelpers.resourceFilePath("testConfig.json"));

  @ClassRule
  public static final DropwizardAppRule<TestConfig> RULE_THAT_LOG_READ_WRITE_ERRORS =
          new DropwizardAppRule<>(TestAppThatLogReadWriteErrors.class, ResourceHelpers.resourceFilePath("testConfig.json"));

  @After
  public void tearDown()
  {
    REQ_BARR.reset();
    MOCK_TRACER.reset();
  }

  @Test
  public void testNoTracesOnSkippedPath() throws Exception
  {
    String methodPath = "skipped";
    try(Response response = execPostCall(RULE, methodPath, 200))
    {
      assertEquals(response.readEntity(String.class), "Hello World!");

      Assert.assertNull(response.getHeaderString("traceid"));
      Assert.assertNull(response.getHeaderString("spanid"));

      List<MockSpan> finishedSpans = MOCK_TRACER.finishedSpans();
      Assert.assertEquals(0, finishedSpans.size());
    }
  }

  @Test
  public void testTracesOnHappyPath() throws Exception
  {
    String methodPath = "happyPath";
    try(Response response = execPostCall(RULE_THAT_LOG_READ_WRITE_ERRORS, methodPath, 200))
    {
      assertEquals(response.readEntity(String.class), "Hello World!");

      Assert.assertNotNull(response.getHeaderString("traceid"));
      Assert.assertNotNull(response.getHeaderString("spanid"));

      // is a list of spans in the order they were close/finish
      // deserialization, serialization, and root
      List<MockSpan> finishedSpans = MOCK_TRACER.finishedSpans();
      Assert.assertEquals(3, finishedSpans.size());

      MockSpan deserializationSpan = finishedSpans.get(0);
      Assert.assertEquals("deserialize", deserializationSpan.operationName());
      Assert.assertEquals(2, deserializationSpan.tags().size());
      Assert.assertNotNull(deserializationSpan.tags().get("entity.type"));
      Assert.assertEquals(MediaType.TEXT_PLAIN, deserializationSpan.tags().get("media.type"));
      Assert.assertEquals(0, deserializationSpan.logEntries().size());

      MockSpan serializationSpan = finishedSpans.get(1);
      Assert.assertEquals("serialize", serializationSpan.operationName());
      Assert.assertEquals(2, serializationSpan.tags().size());
      Assert.assertNotNull(serializationSpan.tags().get("entity.type"));
      Assert.assertEquals(MediaType.TEXT_PLAIN, serializationSpan.tags().get("media.type"));
      Assert.assertEquals(0, serializationSpan.logEntries().size());

      MockSpan rootSpan = finishedSpans.get(2);
      Assert.assertEquals("/test/" + methodPath, rootSpan.operationName());
      Assert.assertEquals(5, rootSpan.tags().size());
      Assert.assertNotNull(rootSpan.tags().get("serverHost"));
      Assert.assertEquals("server", rootSpan.tags().get("span.kind"));
      Assert.assertEquals("/api/test/happyPath", rootSpan.tags().get("http.url"));
      Assert.assertEquals(200, rootSpan.tags().get("http.status_code"));
      Assert.assertEquals("POST", rootSpan.tags().get("http.method"));
      Assert.assertEquals(0, rootSpan.logEntries().size());
    }
  }

  @Test
  public void testTracesWhenFailingSerializingWithDefaultInterceptors() throws Exception
  {
    String methodPath = "failSerializing";
    try(Response response = execPostCall(RULE, methodPath, 500))
    {
      Assert.assertNotNull(response.getHeaderString("traceid"));
      Assert.assertNotNull(response.getHeaderString("spanid"));

      // is a list of spans in the order they were close/finish
      // deserialization, serialization (response), serialization (error msg), and root
      List<MockSpan> finishedSpans = MOCK_TRACER.finishedSpans();
      Assert.assertEquals(4, finishedSpans.size());

      MockSpan deserializationSpan = finishedSpans.get(0);
      Assert.assertEquals("deserialize", deserializationSpan.operationName());
      Assert.assertEquals(2, deserializationSpan.tags().size());
      Assert.assertNotNull(deserializationSpan.tags().get("entity.type"));
      Assert.assertEquals(MediaType.TEXT_PLAIN, deserializationSpan.tags().get("media.type"));
      Assert.assertEquals(0, deserializationSpan.logEntries().size());

      MockSpan serializationSpan = finishedSpans.get(1);
      Assert.assertEquals("serialize", serializationSpan.operationName());
      Assert.assertEquals(3, serializationSpan.tags().size());
      Assert.assertNotNull(serializationSpan.tags().get("entity.type"));
      Assert.assertEquals(MediaType.TEXT_PLAIN, serializationSpan.tags().get("media.type"));
      Assert.assertEquals(true, serializationSpan.tags().get(Tags.ERROR.getKey()));
      Assert.assertEquals("Default interceptor does not log error on span", 0, serializationSpan.logEntries().size());

      MockSpan serializationErrorSpan = finishedSpans.get(2);
      Assert.assertEquals("serialize", serializationErrorSpan.operationName());
      Assert.assertEquals(2, serializationErrorSpan.tags().size());
      Assert.assertEquals("io.dropwizard.jersey.errors.ErrorMessage", serializationErrorSpan.tags().get("entity.type"));

      MockSpan rootSpan = finishedSpans.get(3);
      Assert.assertEquals("/test/" + methodPath, rootSpan.operationName());
      Assert.assertEquals(5, rootSpan.tags().size());
      Assert.assertNotNull(rootSpan.tags().get("serverHost"));
      Assert.assertEquals("server", rootSpan.tags().get("span.kind"));
      Assert.assertEquals("/api/test/" + methodPath, rootSpan.tags().get("http.url"));
      Assert.assertEquals(500, rootSpan.tags().get("http.status_code"));
      Assert.assertEquals("POST", rootSpan.tags().get("http.method"));
      Assert.assertNull("Default does not flag root span as error", rootSpan.tags().get(Tags.ERROR.getKey()));
      Assert.assertEquals("Default does not log on root span on error", 0, rootSpan.logEntries().size());
    }
  }

  @Test
  public void testTracesWhenFailingSerializing() throws Exception
  {
    String methodPath = "failSerializing";
    try(Response response = execPostCall(RULE_THAT_LOG_READ_WRITE_ERRORS, methodPath, 500))
    {
      Assert.assertNotNull(response.getHeaderString("traceid"));
      Assert.assertNotNull(response.getHeaderString("spanid"));

      // is a list of spans in the order they were close/finish
      // deserialization, serialization (response), serialization (error msg), and root
      List<MockSpan> finishedSpans = MOCK_TRACER.finishedSpans();
      Assert.assertEquals(4, finishedSpans.size());

      MockSpan deserializationSpan = finishedSpans.get(0);
      Assert.assertEquals("deserialize", deserializationSpan.operationName());
      Assert.assertEquals(2, deserializationSpan.tags().size());
      Assert.assertNotNull(deserializationSpan.tags().get("entity.type"));
      Assert.assertEquals(MediaType.TEXT_PLAIN, deserializationSpan.tags().get("media.type"));
      Assert.assertEquals(0, deserializationSpan.logEntries().size());

      MockSpan serializationSpan = finishedSpans.get(1);
      Assert.assertEquals("serialize", serializationSpan.operationName());
      Assert.assertEquals(3, serializationSpan.tags().size());
      Assert.assertNotNull(serializationSpan.tags().get("entity.type"));
      Assert.assertEquals(MediaType.TEXT_PLAIN, serializationSpan.tags().get("media.type"));
      Assert.assertEquals(true, serializationSpan.tags().get(Tags.ERROR.getKey()));
      Assert.assertEquals(1, serializationSpan.logEntries().size());
      Map<String, ?> serializationSpanErrorLogFields = serializationSpan.logEntries().get(0).fields();
      Assert.assertEquals("error", serializationSpanErrorLogFields.get(Fields.EVENT));
      Assert.assertTrue(serializationSpanErrorLogFields.get(Fields.ERROR_OBJECT) instanceof Exception);

      MockSpan serializationErrorSpan = finishedSpans.get(2);
      Assert.assertEquals("serialize", serializationErrorSpan.operationName());
      Assert.assertEquals(2, serializationErrorSpan.tags().size());
      Assert.assertEquals("io.dropwizard.jersey.errors.ErrorMessage", serializationErrorSpan.tags().get("entity.type"));

      MockSpan rootSpan = finishedSpans.get(3);
      Assert.assertEquals("/test/" + methodPath, rootSpan.operationName());
      Assert.assertEquals(6, rootSpan.tags().size());
      Assert.assertNotNull(rootSpan.tags().get("serverHost"));
      Assert.assertEquals("server", rootSpan.tags().get("span.kind"));
      Assert.assertEquals("/api/test/" + methodPath, rootSpan.tags().get("http.url"));
      Assert.assertEquals(500, rootSpan.tags().get("http.status_code"));
      Assert.assertEquals("POST", rootSpan.tags().get("http.method"));
      Assert.assertEquals(true, rootSpan.tags().get(Tags.ERROR.getKey()));
      Assert.assertEquals(1, rootSpan.logEntries().size());
      Map<String, ?> rootSpanErrorLogFields = rootSpan.logEntries().get(0).fields();
      Assert.assertEquals("error", rootSpanErrorLogFields.get(Fields.EVENT));
      Assert.assertEquals("Error on serialize", rootSpanErrorLogFields.get(Fields.MESSAGE));
    }
  }

  @Test
  public void testTracesWhenFailingDeserializing() throws Exception
  {
    String methodPath = "failDeserializing";
    try(Response response = execPostCall(RULE_THAT_LOG_READ_WRITE_ERRORS, methodPath, 415))
    {
      Assert.assertNotNull(response.getHeaderString("traceid"));
      Assert.assertNotNull(response.getHeaderString("spanid"));

      // is a list of spans in the order they were close/finish
      // deserialization, serialization (error msg), and root
      List<MockSpan> finishedSpans = MOCK_TRACER.finishedSpans();
      Assert.assertEquals(3, finishedSpans.size());

      MockSpan deserializationSpan = finishedSpans.get(0);
      Assert.assertEquals("deserialize", deserializationSpan.operationName());
      Assert.assertEquals(3, deserializationSpan.tags().size());
      Assert.assertNotNull(deserializationSpan.tags().get("entity.type"));
      Assert.assertEquals(MediaType.TEXT_PLAIN, deserializationSpan.tags().get("media.type"));
      Assert.assertEquals(true, deserializationSpan.tags().get(Tags.ERROR.getKey()));
      Assert.assertEquals(1, deserializationSpan.logEntries().size());
      Map<String, ?> deserializationSpanErrorLogFields = deserializationSpan.logEntries().get(0).fields();
      Assert.assertEquals("error", deserializationSpanErrorLogFields.get(Fields.EVENT));
      Assert.assertTrue(deserializationSpanErrorLogFields.get(Fields.ERROR_OBJECT) instanceof Exception);

      MockSpan serializationErrorSpan = finishedSpans.get(1);
      Assert.assertEquals("serialize", serializationErrorSpan.operationName());
      Assert.assertEquals(2, serializationErrorSpan.tags().size());
      Assert.assertEquals("io.dropwizard.jersey.errors.ErrorMessage", serializationErrorSpan.tags().get("entity.type"));

      MockSpan rootSpan = finishedSpans.get(2);
      Assert.assertEquals("/test/" + methodPath, rootSpan.operationName());
      Assert.assertEquals(6, rootSpan.tags().size());
      Assert.assertNotNull(rootSpan.tags().get("serverHost"));
      Assert.assertEquals("server", rootSpan.tags().get("span.kind"));
      Assert.assertEquals("/api/test/" + methodPath, rootSpan.tags().get("http.url"));
      Assert.assertEquals(415, rootSpan.tags().get("http.status_code"));
      Assert.assertEquals("POST", rootSpan.tags().get("http.method"));
      Assert.assertEquals(true, rootSpan.tags().get(Tags.ERROR.getKey()));
      Assert.assertEquals(1, rootSpan.logEntries().size());
      Map<String, ?> rootSpanErrorLogFields = rootSpan.logEntries().get(0).fields();
      Assert.assertEquals("error", rootSpanErrorLogFields.get(Fields.EVENT));
      Assert.assertEquals("Error on deserialize", rootSpanErrorLogFields.get(Fields.MESSAGE));
    }
  }

  private Response execPostCall(DropwizardAppRule<TestConfig> appRule, String methodPath, int status) throws Exception
  {
    Client client = appRule.client();
    Response response =
        client
            .target(String.format("http://localhost:%d/api/test/%s", appRule.getLocalPort(), methodPath))
            .request(MediaType.TEXT_PLAIN_TYPE)
            .post(Entity.text("Hello World!"));

    if (!methodPath.equals("skipped"))
    {
      // sync with server, only proceeding when root span is finished
      REQ_BARR.await(2, TimeUnit.SECONDS);
    }

    assertEquals(status, response.getStatus());

    return response;
  }

  public static class TestApp extends Application<TestConfig>
  {
    @Override
    public void initialize(Bootstrap<TestConfig> bootstrap)
    {
      GlobalTracer.registerIfAbsent(MOCK_TRACER);
      super.initialize(bootstrap);
      bootstrap.addBundle(getOpenTracingBundle());
    }

    public OpenTracingBundle getOpenTracingBundle()
    {
      return new OpenTracingBundle(Collections.emptyList(), Collections.singletonList("/api/test/skipped"));
    }

    @Override
    public void run(TestConfig testConfig, Environment environment)
    {
      environment.jersey().setUrlPattern("/api/*");
      environment.jersey().register(new Resource());
    }
  }

  public static class TestAppThatLogReadWriteErrors extends TestApp
  {
    @Override
    public OpenTracingBundle getOpenTracingBundle()
    {
      return new OpenTracingBundle(
              Collections.emptyList(),
              Collections.singletonList(new OpenTracingBundle.LogErrorsInterceptorSpanDecorator()),
              Collections.singletonList("/api/test/skipped")
      );
    }
  }

  public static class TestConfig extends Configuration
  {
  }

  @Path("/test")
  public static class Resource
  {
    @Path("/skipped")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String skipped(String payload)
    {
      return payload;
    }

    @Path("/happyPath")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String happyPath(String payload)
    {
      return payload;
    }

    @Path("/failSerializing")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response failSerializing(String payload)
    {
      StreamingOutput so = os ->
      {
        throw new RuntimeException();
      };
      return Response.ok().entity(so).build();
    }

    @Path("/failDeserializing")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    // there is no text_plain to map deserializer
    public Map<String, String> failDeserializing(Map<String, String> payload)
    {
      return payload;
    }
  }
}
