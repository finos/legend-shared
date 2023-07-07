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

package org.finos.legend.server.pac4j.sessionutil;

import java.nio.ByteBuffer;
import java.util.UUID;

public class UuidUtils
{

  public static UUID newUuid()
  {
    return UUID.randomUUID();
  }

  public static String toHexString(UUID uuid)
  {
    return Long.toHexString(uuid.getMostSignificantBits())
        + '-'
        + Long.toHexString(uuid.getLeastSignificantBits());
  }

  public static byte[] toByteArray(UUID uuid)
  {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return bb.array();
  }

  public static UUID fromHexString(String hex)
  {
    String[] keyTokens = hex.split("-", 2);
    long msb = Long.parseUnsignedLong(keyTokens[0], 16);
    long lsb = Long.parseUnsignedLong(keyTokens[1], 16);
    return new UUID(msb, lsb);
  }

  public static UUID fromByteArray(byte[] bytes)
  {
    ByteBuffer bb = ByteBuffer.wrap(bytes);
    long msb = bb.getLong();
    long lsb = bb.getLong();
    return new UUID(msb, lsb);
  }
}
