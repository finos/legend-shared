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

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Assert;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.Span.Kind;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.Sender;

public abstract class ClientSenderTest
{

  protected abstract Sender createSender(URI uri,
                                         CookieAuthenticationProvider cookieAuthenticationProvider);


  protected void testSendSpans() throws IOException, InterruptedException
  {
    try (MockWebServer mockWebServer = new MockWebServer())
    {
      mockWebServer.start();
      mockWebServer.enqueue(new MockResponse());
      try
      {
        CookieAuthenticationProvider tokenProvider =
            new CookieAuthenticationProvider("LegendSSO", () -> "testToken");
        Sender sender = createSender(mockWebServer.url("/").uri(),
            tokenProvider);

        Span span1 = Span.newBuilder()
            .id("abcdef")
            .name("someName")
            .traceId("12345678")
            .putTag("tagKey", "tagValue")
            .kind(Kind.CLIENT)
            .build();

        Span span2 = Span.newBuilder()
            .id("8734568786")
            .name("secondName")
            .traceId("334587326589")
            .putTag("tagKey", "tagValue")
            .kind(Kind.CLIENT)
            .build();

        List<byte[]> encodedSpans = new ArrayList<>();
        encodedSpans.add(span1.toString().getBytes());
        encodedSpans.add(span2.toString().getBytes());
        Call<Void> call = sender.sendSpans(encodedSpans);
        call.execute();
        RecordedRequest request = mockWebServer.takeRequest();
        String cookie = request.getHeader("Cookie");
        List<HttpCookie> cookies = HttpCookie.parse(cookie);
        Assert.assertEquals(1, cookies.size());
        HttpCookie httpCookie = cookies.get(0);
        Assert.assertEquals("LegendSSO", httpCookie.getName());
        Assert.assertEquals("testToken", httpCookie.getValue());
        byte[] sent = request.getBody().readByteArray();
        List<Span> results = SpanBytesDecoder.JSON_V2.decodeList(sent);
        Assert.assertEquals(2, results.size());
        Span testSpan1 = results.get(0);
        Span testSpan2 = results.get(1);
        Assert.assertEquals(span1, testSpan1);
        Assert.assertEquals(span2, testSpan2);

      } finally
      {
        mockWebServer.shutdown();
      }
    }
  }


}
