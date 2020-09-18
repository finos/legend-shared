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

import brave.Tracing;
import brave.opentracing.BraveTracer;
import brave.sampler.Sampler;
import io.opentracing.Tracer;
import org.finos.legend.opentracing.reporter.AsyncReporter;
import zipkin2.Span;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.Sender;

public class OpenTracing
{
  private OpenTracing()
  {
  }

  /**
   * Create an OpenTracing Tracer reporting to a Zipkin server.
   *
   * @param sender      Zipkin Sender
   * @param serviceName Service name to report
   * @return The Tracer
   */
  public static Tracer create(Sender sender, String serviceName)
  {
    return create(sender, serviceName, null);
  }

  /**
   * Create an OpenTracing Tracer reporting to a Zipkin server with sampling.
   *
   * @param sender       Zipkin Sender
   * @param serviceName  Service name to report
   * @param samplingRate sampling rate for tracing
   * @return The Tracer
   */
  public static Tracer create(Sender sender, String serviceName, Float samplingRate)
  {
    return create(sender, serviceName, samplingRate, null);
  }

  /**
   * Create an OpenTracing Tracer reporting to a Zipkin server with sampling.
   *
   * @param sender       Zipkin Sender
   * @param serviceName  Service name to report
   * @param samplingRate sampling rate for tracing
   * @param metrics      ReporterMetrics for collecting tracing metrics
   * @return The Tracer
   */
  public static Tracer create(Sender sender, String serviceName, Float samplingRate,
                              ReporterMetrics metrics)
  {

    if (metrics == null)
    {
      metrics = ReporterMetrics.NOOP_METRICS;
    }

    Reporter<Span> spanReporter = AsyncReporter.builder(sender).metrics(metrics).build();

    Tracing.Builder builder = Tracing.newBuilder()
        .localServiceName(serviceName)
        .spanReporter(spanReporter);
    if (samplingRate != null)
    {
      builder.sampler(Sampler.create(samplingRate));
    }

    return BraveTracer.create(builder.build());
  }
}
