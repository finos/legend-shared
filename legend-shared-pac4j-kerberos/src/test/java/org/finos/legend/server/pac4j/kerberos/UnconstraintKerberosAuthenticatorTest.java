// Copyright 2025 Goldman Sachs
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

package org.finos.legend.server.pac4j.kerberos;

import com.sun.security.jgss.GSSUtil;
import org.finos.legend.server.pac4j.exception.http.TemporaryRedirectAction;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.kerberos.credentials.KerberosCredentials;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UnconstraintKerberosAuthenticatorTest
{
    UnconstraintKerberosAuthenticator delegationKerberosAuth;
    AutoCloseable closeableMocks;

    @Before
    public void setUp()
    {
        closeableMocks = MockitoAnnotations.openMocks(this);
        delegationKerberosAuth = spy(new UnconstraintKerberosAuthenticator("service@REALM", "/test/service/keytab", "https://test.service.com"));
    }

    @After
    public void tearDown() throws Exception {
        closeableMocks.close();
    }

   @Test
    public void testValidateKerberosDelegationWithTGT() throws Exception
    {
        GSSManager mockedManager = mock(GSSManager.class);
        GSSCredential mockedAcceptCred = mock(GSSCredential.class);
        GSSContext mockedContext = mock(GSSContext.class);
        GSSName mockedGSSName = mock(GSSName.class);
        WebContext mokcedWebContext = mock(WebContext.class);
        KerberosTicket mockedKerberosTicket = mock(KerberosTicket.class);
        byte[] responseToken = new byte[] {10, 20, 30};
        try (MockedStatic<GSSUtil> gssUtilMockedStatic = mockStatic(GSSUtil.class);
             MockedStatic<GSSManager> gssManagerMockedStatic = mockStatic(GSSManager.class))
        {
            gssManagerMockedStatic.when(GSSManager::getInstance).thenReturn(mockedManager);

            when(mockedManager.createCredential(any(), anyInt(), (Oid) any(), anyInt())).thenReturn(mockedAcceptCred);
            when(mockedManager.createContext(mockedAcceptCred)).thenReturn(mockedContext);

            when(mockedContext.acceptSecContext(any(), eq(0), anyInt())).thenReturn(responseToken);
            when(mockedContext.getCredDelegState()).thenReturn(true);
            when(mockedContext.getSrcName()).thenReturn(mockedGSSName);
            when(mockedGSSName.toString()).thenReturn("mockUser@REALM");
            when(mockedContext.getDelegCred()).thenReturn(mock(GSSCredential.class));
            when(mockedContext.isEstablished()).thenReturn(false).thenReturn(true);

            Subject fakeSubject = new Subject();
            fakeSubject.getPrincipals().add(new KerberosPrincipal("mockUser@REALM"));
            fakeSubject.getPrivateCredentials().add(mockedKerberosTicket);
            gssUtilMockedStatic.when(() -> GSSUtil.createSubject(mockedGSSName, mockedContext.getDelegCred())).thenReturn(fakeSubject);


            byte[] spnegoToken = new byte[] {1, 2, 3};
            KerberosCredentials credentials = new KerberosCredentials(spnegoToken);
            delegationKerberosAuth.validate(credentials, mokcedWebContext);

            assertNotNull(credentials.getUserProfile());
            assertTrue(credentials.getUserProfile() instanceof KerberosProfile);
            assertEquals("mockUser", credentials.getUserProfile().getId());
            assertNotNull(((KerberosProfile) credentials.getUserProfile()).getSubject());
            assertEquals(1,((KerberosProfile) credentials.getUserProfile()).getSubject().getPrivateCredentials().size());
            assertEquals(0,((KerberosProfile) credentials.getUserProfile()).getSubject().getPublicCredentials().size());

            verify(mokcedWebContext).setResponseHeader(eq("WWW-Authenticate"),
                    argThat(h -> h.startsWith("Negotiate " + Base64.getEncoder().encodeToString(responseToken))));
            verify(mockedContext).acceptSecContext(eq(spnegoToken), eq(0), eq(spnegoToken.length));
        }
    }

    @Test
    public void testRedirectionWithoutDelegationCreds() throws Exception
    {
        GSSManager mockedManager = mock(GSSManager.class);
        GSSCredential mockedAcceptCred = mock(GSSCredential.class);
        GSSContext mockedContext = mock(GSSContext.class);
        GSSName mockedGSSName = mock(GSSName.class);
        JEEContext mokcedWebContext = mock(JEEContext.class);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        byte[] responseToken = new byte[] {10, 20, 30};

        try (MockedStatic<GSSUtil> gssUtilMockedStatic = mockStatic(GSSUtil.class);
             MockedStatic<GSSManager> gssManagerMockedStatic = mockStatic(GSSManager.class))
        {
            when(mokcedWebContext.getPath()).thenReturn("/path");
            when(mokcedWebContext.getNativeRequest()).thenReturn(httpServletRequest);
            when(httpServletRequest.getQueryString()).thenReturn("query=value");
            gssManagerMockedStatic.when(GSSManager::getInstance).thenReturn(mockedManager);
            when(mockedContext.acceptSecContext(any(), eq(0), anyInt())).thenReturn(responseToken);
            when(mockedManager.createCredential(any(), anyInt(), (Oid) any(), anyInt())).thenReturn(mockedAcceptCred);
            when(mockedManager.createContext(mockedAcceptCred)).thenReturn(mockedContext);

            when(mockedContext.getCredDelegState()).thenReturn(false);
            when(mockedContext.getSrcName()).thenReturn(mockedGSSName);
            when(mockedGSSName.toString()).thenReturn("mockUser@REALM");

            Subject fakeSubject = new Subject();
            fakeSubject.getPrincipals().add(new KerberosPrincipal("mockUser@REALM"));
            gssUtilMockedStatic.when(() -> GSSUtil.createSubject(mockedGSSName, mockedContext.getDelegCred())).thenReturn(fakeSubject);


            byte[] spnegoToken = new byte[] {1, 2, 3};
            KerberosCredentials credentials = new KerberosCredentials(spnegoToken);
            TemporaryRedirectAction exception = assertThrows(TemporaryRedirectAction.class,
                    () -> delegationKerberosAuth.validate(credentials, mokcedWebContext));

            assertEquals(307, exception.getCode());
            assertEquals("https://test.service.com/path?query=value", exception.getLocation());

            verify(mokcedWebContext).getPath();
            verify(mokcedWebContext).getNativeRequest();
            verify(mockedContext).acceptSecContext(eq(spnegoToken), eq(0), eq(spnegoToken.length));
        }
    }

}