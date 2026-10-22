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

import org.junit.Test;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.serializer.JavaSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The fixtures were written by pac4j 4.5.8 and 5.7.1; regenerating them with the current pac4j
 * would make these tests meaningless.
 */
public class CrossVersionProfileSerializationTest
{
    private static final String PAC4J_5_BLOB = "/profiles/commonprofile-pac4j-5.7.1.bin";
    private static final String PAC4J_4_BLOB = "/profiles/commonprofile-pac4j-4.5.8.bin";

    public static byte[] readResource(String path)
    {
        try (InputStream in = CrossVersionProfileSerializationTest.class.getResourceAsStream(path))
        {
            assertNotNull("missing test fixture " + path, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int read = in.read(buffer); read != -1; read = in.read(buffer))
            {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * pac4j 6 dropped the {@code permissions} slot from the {@code Externalizable}
     * {@code BasicUserProfile} without changing its {@code serialVersionUID}, so the old
     * {@code HashSet} lands in the {@code clientName} slot, and {@code JavaSerializer} catches only
     * {@code IOException} and {@code ClassNotFoundException}. If this stops throwing, re-examine
     * the session stores' {@code RuntimeException} catch rather than deleting it.
     */
    @Test
    public void pac4jSerializerLetsRuntimeExceptionEscapeOnLegacyBlob()
    {
        for (String fixture : new String[]{PAC4J_5_BLOB, PAC4J_4_BLOB})
        {
            try
            {
                Object result = new JavaSerializer().deserializeFromBytes(readResource(fixture));
                fail("expected a RuntimeException to escape for " + fixture + ", got " + result);
            }
            catch (ClassCastException e)
            {
                assertTrue("unexpected cast failure for " + fixture + ": " + e.getMessage(),
                        e.getMessage().contains("java.util.HashSet")
                                && e.getMessage().contains("java.lang.String"));
            }
        }
    }

    @Test
    public void pac4j4AndPac4j5BlobsFailTheSameWay()
    {
        String four = cause(PAC4J_4_BLOB);
        String five = cause(PAC4J_5_BLOB);
        assertEquals("4.5.8 and 5.7.1 are expected to share one wire format", four, five);
    }

    @Test
    public void currentVersionRoundTripStillWorks()
    {
        CommonProfile profile = new CommonProfile();
        profile.setId("alice");
        profile.addRole("admin");
        profile.setClientName("KerberosClient");

        JavaSerializer serializer = new JavaSerializer();
        Object result = serializer.deserializeFromBytes(serializer.serializeToBytes(profile));

        assertTrue(result instanceof CommonProfile);
        assertEquals("alice", ((CommonProfile) result).getId());
        assertEquals("KerberosClient", ((CommonProfile) result).getClientName());
    }

    private static String cause(String fixture)
    {
        try
        {
            return "no throw: " + new JavaSerializer().deserializeFromBytes(readResource(fixture));
        }
        catch (RuntimeException e)
        {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
