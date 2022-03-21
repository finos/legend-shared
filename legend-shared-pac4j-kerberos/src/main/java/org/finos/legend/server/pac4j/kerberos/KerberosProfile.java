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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;

public class KerberosProfile extends org.pac4j.kerberos.profile.KerberosProfile
{

    private static final Logger logger = LoggerFactory.getLogger(KerberosProfile.class);
    private static final long serialVersionUID = -8691865551996919736L;

    private Subject subject;


    public KerberosProfile(Subject subject, final GSSContext gssContext)
    {
        super(gssContext);
        this.subject = subject;
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

    public void readExternal(ObjectInput in) throws ClassNotFoundException, IOException
    {
        super.readExternal(in);
        KerberosPrincipal principal = new KerberosPrincipal(in.readUTF());
        KerberosTicket tgt = (KerberosTicket)in.readObject();
        subject = new Subject(true, Collections.singleton(principal), Collections.emptySet(), Collections.singleton(tgt));
    }

    public void writeExternal(ObjectOutput out) throws IOException
    {
        super.writeExternal(out);
        out.writeUTF(subject.getPrincipals().iterator().next().getName());
        out.writeObject(subject.getPrivateCredentials(KerberosTicket.class).iterator().next());
    }

    @Override
    public boolean isExpired()
    {
        if (subject != null && subject.getPrivateCredentials(KerberosTicket.class) != null)
        {
            KerberosTicket kerberosTicket = subject.getPrivateCredentials(KerberosTicket.class).iterator().next();
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
        return false;
    }
}
