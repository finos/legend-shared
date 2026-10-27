// Copyright 2026 Goldman Sachs
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

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.dropwizard.jetty.HttpConnectorFactory;

import java.io.IOException;

/**
 * Restores Dropwizard 1.3's default of {@code useForwardedHeaders: true} on HTTP connectors (Dropwizard 2 and later
 * default to false). Legend servers moved straight from Dropwizard 1.3, and their deployments sit behind
 * TLS-terminating proxies without setting the key, so with the new default every URL built from the request (pac4j's
 * saved request, application redirects) comes out as plain http or the proxy's internal host.
 *
 * <p>The connector is created with the old default and then configured as usual, so a configuration that sets
 * {@code useForwardedHeaders} (or a {@code -Ddw.} override) still decides. Covers every {@link HttpConnectorFactory}
 * subtype ({@code http}, {@code https}, {@code h2}, {@code h2c}), application and admin connectors alike, as
 * Dropwizard 1.3 did.
 *
 * <p>{@link LegendPac4jBundle} registers it on the bootstrap's object mapper, which Dropwizard copies into the
 * configuration factory, so every server that adds the bundle gets it. A server without the bundle can register it
 * itself in {@code initialize}.
 */
public class ForwardedHeadersDefaultModule extends SimpleModule
{
    public ForwardedHeadersDefaultModule()
    {
        super(ForwardedHeadersDefaultModule.class.getName());
        setDeserializerModifier(new BeanDeserializerModifier()
        {
            @Override
            public BeanDeserializerBuilder updateBuilder(DeserializationConfig config, BeanDescription beanDesc, BeanDeserializerBuilder builder)
            {
                if (HttpConnectorFactory.class.isAssignableFrom(beanDesc.getBeanClass()) && !(builder.getValueInstantiator() instanceof ForwardedHeadersOn))
                {
                    builder.setValueInstantiator(new ForwardedHeadersOn(builder.getValueInstantiator()));
                }
                return builder;
            }
        });
    }

    private static class ForwardedHeadersOn extends ValueInstantiator.Delegating
    {
        private ForwardedHeadersOn(ValueInstantiator delegate)
        {
            super(delegate);
        }

        @Override
        public ValueInstantiator createContextual(DeserializationContext context, BeanDescription beanDesc) throws JsonMappingException
        {
            // Delegating would drop this wrapper if the delegate returned a new instantiator
            ValueInstantiator contextual = delegate().createContextual(context, beanDesc);
            return (contextual == delegate()) ? this : new ForwardedHeadersOn(contextual);
        }

        @Override
        public Object createUsingDefault(DeserializationContext context) throws IOException
        {
            Object connector = super.createUsingDefault(context);
            if (connector instanceof HttpConnectorFactory)
            {
                ((HttpConnectorFactory) connector).setUseForwardedHeaders(true);
            }
            return connector;
        }
    }
}
