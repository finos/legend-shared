// Forked from
//      https://github.com/opentracing-contrib/java-jaxrs/blob/master/opentracing-jaxrs2/src/main/java/io/opentracing/contrib/jaxrs2/server/ServerTracingInterceptor.java
//
// This includes enhancements requested on issue:
//      https://github.com/opentracing-contrib/java-jaxrs/issues/147
//
// PR with contribution for solving issue:
//      https://github.com/opentracing-contrib/java-jaxrs/pull/148
//
// Once issue is solved, we should update artifact version and delete this fork

package org.finos.legend.opentracing.jaxrs2;

import io.opentracing.Tracer;
import io.opentracing.contrib.jaxrs2.internal.CastUtils;
import io.opentracing.contrib.jaxrs2.internal.SpanWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.InterceptorContext;
import java.util.List;

import static io.opentracing.contrib.jaxrs2.internal.SpanWrapper.PROPERTY_NAME;

public class ServerTracingInterceptor extends TracingInterceptor
{
    /**
     * Apache CFX does not seem to publish the PROPERTY_NAME into the Interceptor context.
     * Use the current HttpServletRequest to lookup the current span wrapper.
     */
    @Context
    private HttpServletRequest servletReq;

    public ServerTracingInterceptor(Tracer tracer, List<InterceptorSpanDecorator> spanDecorators)
    {
        super(tracer, spanDecorators);
    }

    @Override
    protected SpanWrapper findSpan(InterceptorContext context)
    {
        SpanWrapper spanWrapper = CastUtils.cast(context.getProperty(PROPERTY_NAME), SpanWrapper.class);
        if (spanWrapper == null && servletReq != null)
        {
            spanWrapper = CastUtils
                    .cast(servletReq.getAttribute(PROPERTY_NAME), SpanWrapper.class);
        }
        return spanWrapper;
    }
}