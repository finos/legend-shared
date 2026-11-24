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

import ch.qos.logback.access.common.spi.IAccessEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.old.LogbackClassicRequestLogFactory;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.security.Principal;
import java.util.Iterator;

public class UserLoggingRequestLogTest
{
    @Test
    public void logsTheUserFromTheJettyAuthenticationState()
    {
        Principal alice = () -> "alice";
        Request.AuthenticationState authenticated = new Request.AuthenticationState()
        {
            @Override
            public Principal getUserPrincipal()
            {
                return alice;
            }
        };
        Assert.assertEquals("alice", logAndCapture(authenticated).getRemoteUser());
    }

    @Test
    public void logsNoUserWithoutAnAuthenticationState()
    {
        Assert.assertNull(logAndCapture(null).getRemoteUser());
    }

    @Test
    public void replacesTheDefaultRequestLogFactoryKeepingItsAppenders()
    {
        Configuration configuration = new Configuration();
        DefaultServerFactory serverFactory = (DefaultServerFactory) configuration.getServerFactory();
        LogbackAccessRequestLogFactory original = (LogbackAccessRequestLogFactory) serverFactory.getRequestLogFactory();

        UserLoggingRequestLogFactory.useForDefaultRequestLog(configuration);

        Assert.assertTrue(serverFactory.getRequestLogFactory() instanceof UserLoggingRequestLogFactory);
        Assert.assertSame(original.getAppenders(), ((UserLoggingRequestLogFactory) serverFactory.getRequestLogFactory()).getAppenders());
    }

    @Test
    public void leavesOtherRequestLogTypesAlone()
    {
        Configuration configuration = new Configuration();
        DefaultServerFactory serverFactory = (DefaultServerFactory) configuration.getServerFactory();
        LogbackClassicRequestLogFactory classic = new LogbackClassicRequestLogFactory();
        serverFactory.setRequestLogFactory(classic);

        UserLoggingRequestLogFactory.useForDefaultRequestLog(configuration);

        Assert.assertSame(classic, serverFactory.getRequestLogFactory());
    }

    @Test
    public void buildsAUserLoggingRequestLogWithTheConfiguredAppenders()
    {
        // The default appenders: one console appender.
        RequestLog requestLog = new UserLoggingRequestLogFactory().build("test");
        Assert.assertTrue(requestLog instanceof UserLoggingRequestLog);
        Iterator<Appender<IAccessEvent>> appenders = ((UserLoggingRequestLog) requestLog).iteratorForAppenders();
        Assert.assertTrue(appenders.hasNext());
        appenders.next();
        Assert.assertFalse(appenders.hasNext());
    }

    private static IAccessEvent logAndCapture(Request.AuthenticationState authenticationState)
    {
        Request request = Mockito.mock(Request.class);
        Mockito.when(request.getHeaders()).thenReturn(HttpFields.EMPTY);
        Mockito.when(request.getAttribute(Request.AuthenticationState.class.getName())).thenReturn(authenticationState);
        Response response = Mockito.mock(Response.class);
        Mockito.when(response.getHeaders()).thenReturn(HttpFields.build());

        UserLoggingRequestLog requestLog = new UserLoggingRequestLog();
        ListAppender<IAccessEvent> appender = new ListAppender<>();
        appender.start();
        requestLog.addAppender(appender);

        requestLog.log(request, response);

        Assert.assertEquals(1, appender.list.size());
        return appender.list.get(0);
    }
}
