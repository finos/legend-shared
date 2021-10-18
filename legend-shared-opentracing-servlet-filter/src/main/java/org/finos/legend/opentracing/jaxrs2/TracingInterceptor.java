// Forked from
//      https://github.com/opentracing-contrib/java-jaxrs/blob/master/opentracing-jaxrs2/src/main/java/io/opentracing/contrib/jaxrs2/serialization/TracingInterceptor.java
//
// This includes enhancements requested on issue:
//      https://github.com/opentracing-contrib/java-jaxrs/issues/147
//
// PR with contribution for solving issue:
//      https://github.com/opentracing-contrib/java-jaxrs/pull/148
//
// Once issue is solved, we should update artifact version and delete this fork

package org.finos.legend.opentracing.jaxrs2;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.jaxrs2.internal.SpanWrapper;
import io.opentracing.noop.NoopSpan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.InterceptorContext;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.ReaderInterceptorContext;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

public abstract class TracingInterceptor implements WriterInterceptor, ReaderInterceptor
{
    private final Tracer tracer;
    private final Collection<InterceptorSpanDecorator> spanDecorators;

    public TracingInterceptor(Tracer tracer,
                              List<InterceptorSpanDecorator> spanDecorators)
    {
        Objects.requireNonNull(tracer);
        Objects.requireNonNull(spanDecorators);
        this.tracer = tracer;
        this.spanDecorators = new ArrayList<>(spanDecorators);
    }

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context)
            throws IOException, WebApplicationException
    {
        Span span = buildSpan(context, "deserialize");
        try (Scope scope = tracer.activateSpan(span))
        {
            decorateRead(context, span);
            try
            {
                return context.proceed();
            } catch (Exception e)
            {
                decorateReadException(e, context, span);
                throw e;
            }
        } finally
        {
            span.finish();
        }
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context)
            throws IOException, WebApplicationException
    {
        Span span = buildSpan(context, "serialize");
        try (Scope scope = tracer.activateSpan(span))
        {
            decorateWrite(context, span);
            try
            {
                context.proceed();
            } catch (Exception e)
            {
                decorateWriteException(e, context, span);
                throw e;
            }
        } finally
        {
            span.finish();
        }
    }

    private Span buildSpan(InterceptorContext context, String operationName)
    {
        final SpanWrapper spanWrapper = findSpan(context);
        if (spanWrapper == null)
        {
            return NoopSpan.INSTANCE;
        }
        final Tracer.SpanBuilder spanBuilder = tracer.buildSpan(operationName);
        if (spanWrapper.isFinished())
        {
            spanBuilder.addReference(References.FOLLOWS_FROM, spanWrapper.get().context());
        } else
        {
            spanBuilder.asChildOf(spanWrapper.get());
        }
        return spanBuilder.start();
    }

    protected abstract SpanWrapper findSpan(InterceptorContext context);

    private void decorateRead(InterceptorContext context, Span span)
    {
        for (InterceptorSpanDecorator decorator : spanDecorators)
        {
            decorator.decorateRead(context, span);
        }
    }

    private void decorateWrite(InterceptorContext context, Span span)
    {
        for (InterceptorSpanDecorator decorator : spanDecorators)
        {
            decorator.decorateWrite(context, span);
        }
    }

    private void decorateReadException(Exception e, ReaderInterceptorContext context, Span span)
    {
        for (InterceptorSpanDecorator decorator : spanDecorators)
        {
            decorator.decorateReadException(e, context, span);
        }
    }

    private void decorateWriteException(Exception e, WriterInterceptorContext context, Span span)
    {
        for (InterceptorSpanDecorator decorator : spanDecorators)
        {
            decorator.decorateWriteException(e, context, span);
        }
    }
}
