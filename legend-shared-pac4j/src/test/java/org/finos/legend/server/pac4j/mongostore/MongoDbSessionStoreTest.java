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

package org.finos.legend.server.pac4j.mongostore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.servlet.http.Cookie;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.session.J2ESessionStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class MongoDbSessionStoreTest
{

  private static MongoServer server;
  private static MongoClient client;
  private static MongoDatabase db;
  private MongoDbSessionStore store;

  @BeforeClass
  public static void setup()
  {
    server = new MongoServer(new MemoryBackend());
    InetSocketAddress serverAddress = server.bind();

    client = new MongoClient(new ServerAddress(serverAddress));
    db = client.getDatabase("test");
  }

  @AfterClass
  public static void teardown()
  {
    server.shutdown();
    client.close();
  }

  @Before
  public void before()
  {
    List<String> testTrustedPackages = new ArrayList<>();
    testTrustedPackages.add("test.trusted.package");
    store = new MongoDbSessionStore("AES", 100, db.getCollection("sessionData"), ImmutableMap.of(J2EContext.class, new J2ESessionStore()), testTrustedPackages);
  }

  @Test
  public void testSetCreatesCookie()
  {
    MockHttpServletResponse response = new MockHttpServletResponse();
    J2EContext requestContext = new J2EContext(new MockHttpServletRequest(), response);
    store.set(requestContext, "testKey", "testValue");
    Cookie[] cookies = response.getCookies();
    assertEquals(1, cookies.length);
    Cookie cookie = cookies[0];
    assertEquals("LegendSSO", cookie.getName());
    String val = cookie.getValue();
    Pattern acceptable = Pattern.compile("[0-9a-f]{15,16}-[0-9a-f]{15,16}/[0-9a-f]{15,16}-[0-9a-f]{15,16}");
    assertTrue("testing " + val, acceptable.matcher(val).matches());
  }


  @Test
  public void testMultipleSetsOnlyCreateOneCookie()
  {
    MockHttpServletResponse response = new MockHttpServletResponse();
    J2EContext requestContext = new J2EContext(new MockHttpServletRequest(), response);
    store.set(requestContext, "testKey", "testValue");
    store.set(requestContext, "testKey2", "testValue");
    store.set(requestContext, "testKey3", "testValue");
    store.set(requestContext, "testKey4", "testValue");
    Cookie[] cookies = response.getCookies();
    assertEquals(1, cookies.length);
  }

  @Test
  public void testTrustedPackageAdded()
  {
    assertTrue(this.store.getSerializationHelper().getTrustedPackages().contains("test.trusted.package"));
  }

  @Test
  public void testSetThenGetFromSession()
  {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    J2EContext requestContext = new J2EContext(request, response);
    store.set(requestContext, "testKey", "testValue");

    assertEquals("testValue", store.get(requestContext, "testKey"));

    // Copy the session to the new request
    MockHttpServletRequest newRequest = new MockHttpServletRequest();
    newRequest.setSession(request.getSession());
    MockHttpServletResponse newResponse = new MockHttpServletResponse();
    requestContext = new J2EContext(newRequest, newResponse);

    assertEquals("testValue", store.get(requestContext, "testKey"));
  }

  @Test
  public void testSetThenGetFromMongo()
  {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    J2EContext requestContext = new J2EContext(request, response);
    store.set(requestContext, "testKey", "testValue");

    assertEquals("testValue", store.get(requestContext, "testKey"));

    // Copy the cookie to the new request
    MockHttpServletRequest newRequest = new MockHttpServletRequest();
    newRequest.setCookies(response.getCookies());
    MockHttpServletResponse newResponse = new MockHttpServletResponse();
    requestContext = new J2EContext(newRequest, newResponse);

    assertEquals("testValue", store.get(requestContext, "testKey"));

  }

}