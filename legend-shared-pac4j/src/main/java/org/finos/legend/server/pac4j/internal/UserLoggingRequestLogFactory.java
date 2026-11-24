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

package org.finos.legend.server.pac4j.internal;

import ch.qos.logback.access.jetty.RequestLogImpl;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.AbstractServerFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;
import org.eclipse.jetty.server.RequestLog;

/**
 * {@link LogbackAccessRequestLogFactory} building a {@link UserLoggingRequestLog}, with the same appenders.
 */
public class UserLoggingRequestLogFactory extends LogbackAccessRequestLogFactory
{
    /**
     * Replaces the server's request log factory with this one if it is Dropwizard's default logback-access one.
     * Another type, such as {@code classic}, which writes through Jetty's request log and already logs the user,
     * or a subclass, is left alone.
     */
    public static void useForDefaultRequestLog(Configuration configuration)
    {
        if (configuration.getServerFactory() instanceof AbstractServerFactory)
        {
            AbstractServerFactory serverFactory = (AbstractServerFactory) configuration.getServerFactory();
            RequestLogFactory<?> requestLogFactory = serverFactory.getRequestLogFactory();
            if ((requestLogFactory != null) && (requestLogFactory.getClass() == LogbackAccessRequestLogFactory.class))
            {
                UserLoggingRequestLogFactory userLogging = new UserLoggingRequestLogFactory();
                userLogging.setAppenders(((LogbackAccessRequestLogFactory) requestLogFactory).getAppenders());
                serverFactory.setRequestLogFactory(userLogging);
            }
        }
    }

    @Override
    public RequestLog build(String name)
    {
        RequestLogImpl configured = (RequestLogImpl) super.build(name);
        UserLoggingRequestLog requestLog = new UserLoggingRequestLog();
        configured.iteratorForAppenders().forEachRemaining(requestLog::addAppender);
        return requestLog;
    }
}
