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

import ch.qos.logback.access.common.spi.AccessEvent;
import ch.qos.logback.access.common.spi.IAccessEvent;
import ch.qos.logback.access.jetty.JettyServerAdapter;
import ch.qos.logback.access.jetty.RequestWrapper;
import ch.qos.logback.access.jetty.ResponseWrapper;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.FilterReply;
import io.dropwizard.request.logging.LogbackAccessRequestLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.security.Principal;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dropwizard's logback-access request log, except that it logs the authenticated user ({@code %u}).
 *
 * <p>logback-access's Jetty 12 {@link RequestWrapper} answers {@code getRemoteUser()} with null, so Dropwizard 5's
 * {@link LogbackAccessRequestLog} writes {@code -} for every user. This one reads the user from the Jetty request's
 * authentication state, which {@link UsernameFilter} sets, as Jetty's own {@code CustomRequestLog} does. The rest
 * of {@link #log} is Dropwizard's, which keeps it private.
 */
public class UserLoggingRequestLog extends LogbackAccessRequestLog
{
    @Override
    public void log(Request jettyRequest, Response jettyResponse)
    {
        HttpServletRequest httpServletRequest = new RequestWrapper(jettyRequest)
        {
            @Override
            public String getRemoteUser()
            {
                // Null without a state on Jetty 12.0; Jetty 12.1 answers AuthenticationState.NONE instead.
                Request.AuthenticationState state = Request.getAuthenticationState(jettyRequest);
                Principal principal = (state == null) ? null : state.getUserPrincipal();
                return (principal == null) ? null : principal.getName();
            }

            @Override
            public Map<String, String> buildRequestHeaderMap()
            {
                return buildHeaderMap(jettyRequest.getHeaders());
            }
        };
        HttpServletResponse httpServletResponse = new ResponseWrapper(jettyResponse);
        JettyServerAdapter adapter = new JettyServerAdapter(jettyRequest, jettyResponse)
        {
            @Override
            public Map<String, String> buildResponseHeaderMap()
            {
                return buildHeaderMap(jettyResponse.getHeaders());
            }
        };
        IAccessEvent accessEvent = new AccessEvent(this, httpServletRequest, httpServletResponse, adapter);
        if (getFilterChainDecision(accessEvent) == FilterReply.DENY)
        {
            return;
        }
        Iterator<Appender<IAccessEvent>> appenders = iteratorForAppenders();
        while (appenders.hasNext())
        {
            appenders.next().doAppend(accessEvent);
        }
    }

    private static Map<String, String> buildHeaderMap(HttpFields headers)
    {
        Map<String, String> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (HttpField field : headers)
        {
            String existing = headerMap.get(field.getName());
            headerMap.put(field.getName(), (existing == null) ? field.getValue() : (existing + "," + field.getValue()));
        }
        return headerMap;
    }
}
