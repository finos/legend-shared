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

package org.finos.legend.server.pac4j.kerberos;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

public class KerberosProfile extends org.pac4j.kerberos.profile.KerberosProfile
{

    private static final Logger logger = LoggerFactory.getLogger(KerberosProfile.class);
    private static final long serialVersionUID = -8691865551996919736L;
    private Subject serviceSubject;
    private Subject subject;


    public KerberosProfile(Subject subject, final GSSContext gssContext)
    {
        super(gssContext);
        this.subject = subject;
    }

    public KerberosProfile(Subject subject, Subject serviceSubject, final GSSContext gssContext)
    {
        super(gssContext);
        this.subject = subject;
        this.serviceSubject = serviceSubject;
    }

    public KerberosProfile(LocalCredentials creds)
    {
        setId(creds.getUserId());
        this.subject = creds.getSubject();
    }

    public KerberosProfile()
    {
    }

    public Subject getSubject()
    {
        return subject;
    }

    public Subject getServiceSubject()
    {
        return serviceSubject;
    }

    @Override
    public void readExternal(ObjectInput in) throws ClassNotFoundException, IOException
    {
        super.readExternal(in);
        KerberosPrincipal principal = new KerberosPrincipal(in.readUTF());
       try
       {
           KerberosTicket tgt = (KerberosTicket)in.readObject();
           subject = new Subject(true, Collections.singleton(principal), Collections.emptySet(), Collections.singleton(tgt));
       }
       catch (IOException ioException)
       {
           subject = new Subject(true, Collections.singleton(principal), Collections.emptySet(), Collections.emptySet());
       }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        super.writeExternal(out);
        out.writeUTF(subject.getPrincipals().iterator().next().getName());
        if (!getKerberosTicketsFromSubject().isEmpty())
        {
            out.writeObject(getKerberosTicketsFromSubject().iterator().next());
        }
    }

    @Override
    public boolean isExpired()
    {
        Iterator<KerberosTicket> tickets = getKerberosTicketsFromSubject().iterator();
        if (tickets.hasNext())
        {
            KerberosTicket kerberosTicket = tickets.next();
            boolean expired = kerberosTicket == null || !kerberosTicket.isCurrent();
            if (kerberosTicket != null)
            {
                logger.debug("profile {}: starts {}, ends {} expired? {}", subject.getPrincipals().iterator().next().getName(), kerberosTicket.getStartTime(), kerberosTicket.getEndTime(), expired);
            }
            else
            {
                logger.debug("profile {} has no kerberos ticket", subject.getPrincipals().iterator().next().getName());
            }
            return expired;
        }
        else if (Objects.nonNull(subject))
        {
            Set<GSSCredential> publicCredentials = subject.getPublicCredentials(GSSCredential.class);
            return publicCredentials.isEmpty() || publicCredentials.stream()
                   .anyMatch(gssCredential ->
                       {
                           try
                           {
                               return gssCredential.getRemainingLifetime() == 0;
                           }
                           catch (GSSException e)
                           {
                               return true;
                           }
                       });
        }
        return super.isExpired();
    }

    private Set<KerberosTicket> getKerberosTicketsFromSubject()
    {
        if (Objects.nonNull(subject) && !subject.getPrivateCredentials().isEmpty())
        {
            return subject.getPrivateCredentials(KerberosTicket.class);
        }
        return Collections.emptySet();
    }
}
