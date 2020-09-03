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

package org.finos.legend.server.pac4j.mongostore;

import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

class SessionCrypt
{
  private final String cryptAlgorithm;

  SessionCrypt(String cryptAlgorithm)
  {
    this.cryptAlgorithm = cryptAlgorithm;
  }

  String toCryptedString(byte[] in, SessionToken token) throws GeneralSecurityException
  {
    byte[] keyBytes = UuidUtils.toByteArray(token.getSessionKey());
    SecretKeySpec secretKey = new SecretKeySpec(keyBytes, cryptAlgorithm);
    Cipher cipher = Cipher.getInstance(cryptAlgorithm);
    cipher.init(Cipher.ENCRYPT_MODE, secretKey);
    byte[] crypted = cipher.doFinal(in);
    return Base64.getEncoder().encodeToString(crypted);
  }

  byte[] fromCryptedString(String in, SessionToken token) throws GeneralSecurityException
  {
    byte[] keyBytes = UuidUtils.toByteArray(token.getSessionKey());
    SecretKeySpec secretKey = new SecretKeySpec(keyBytes, cryptAlgorithm);
    Cipher cipher = Cipher.getInstance(cryptAlgorithm);
    cipher.init(Cipher.DECRYPT_MODE, secretKey);
    return cipher.doFinal(Base64.getDecoder().decode(in));
  }
}