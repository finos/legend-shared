// Forked from:
//      https://github.com/opentracing-contrib/java-jaxrs/blob/master/opentracing-jaxrs2/src/main/java/io/opentracing/contrib/jaxrs2/serialization/InterceptorSpanDecorator.java
//
// This includes enhancements requested on issue:
//      https://github.com/opentracing-contrib/java-jaxrs/issues/147
//
// PR with contribution for solving issue:
//      https://github.com/opentracing-contrib/java-jaxrs/pull/148
//
// Once issue is solved, we should update artifact version and delete this fork

package org.finos.legend.opentracing.jaxrs2;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

import javax.ws.rs.ext.InterceptorContext;
import javax.ws.rs.ext.ReaderInterceptorContext;
import javax.ws.rs.ext.WriterInterceptorContext;

public interface InterceptorSpanDecorator
{
    void decorateRead(InterceptorContext context, Span span);

    void decorateWrite(InterceptorContext context, Span span);

    void decorateReadException(Exception e, ReaderInterceptorContext context, Span span);

    void decorateWriteException(Exception e, WriterInterceptorContext context, Span span);

    InterceptorSpanDecorator STANDARD_TAGS = new InterceptorSpanDecorator()
    {
        @Override
        public void decorateRead(InterceptorContext context, Span span)
        {
            span.setTag("media.type", context.getMediaType().toString());
            span.setTag("entity.type", context.getType().getName());
        }

        @Override
        public void decorateWrite(InterceptorContext context, Span span)
        {
            decorateRead(context, span);
        }

        @Override
        public void decorateReadException(Exception e, ReaderInterceptorContext context, Span span)
        {
            Tags.ERROR.set(span, true);
        }

        @Override
        public void decorateWriteException(Exception e, WriterInterceptorContext context, Span span)
        {
            Tags.ERROR.set(span, true);
        }
    };
}
