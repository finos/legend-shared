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

package org.finos.legend.server.pac4j.gitlab.ssl;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class TrustManagerComposite implements X509TrustManager
{

  private final List<X509TrustManager> compositeTrustmanager;

  public TrustManagerComposite(String keystoreFile)
  {
    List<X509TrustManager> trustManagers = new ArrayList<>();
    try (InputStream truststoreInput = new FileInputStream(keystoreFile))
    {
      trustManagers.add(getCustomTrustmanager(truststoreInput));
      trustManagers.add(getDefaultTrustmanager());
    } catch (Exception e)
    {
      throw new RuntimeException(e);
    }
    compositeTrustmanager = trustManagers;
  }

  private static X509TrustManager getCustomTrustmanager(InputStream trustStream) throws Exception
  {
    return createTrustManager(trustStream);
  }

  private static X509TrustManager getDefaultTrustmanager() throws Exception
  {
    return createTrustManager(null);
  }

  private static X509TrustManager createTrustManager(InputStream trustStream) throws Exception
  {
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());

    trustStore.load(trustStream, null);

    TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
    trustFactory.init(trustStore);

    TrustManager[] trustManagers = trustFactory.getTrustManagers();
    for (TrustManager trustManager : trustManagers)
    {
      if (trustManager instanceof X509TrustManager)
      {
        return (X509TrustManager) trustManager;
      }
    }
    return null;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain,
                                 String authType) throws CertificateException
  {
    for (X509TrustManager trustManager : compositeTrustmanager)
    {
      try
      {
        trustManager.checkClientTrusted(chain, authType);
        return;
      } catch (CertificateException e)
      {
        // maybe the next trust manager will trust it, don't break the loop
      }
    }
    throw new CertificateException("None of the TrustManagers trust this certificate chain");
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain,
                                 String authType) throws CertificateException
  {
    for (X509TrustManager trustManager : compositeTrustmanager)
    {
      try
      {
        trustManager.checkServerTrusted(chain, authType);
        return;
      } catch (CertificateException e)
      {
        // maybe the next trust manager will trust it, don't break the loop
      }
    }
    throw new CertificateException("None of the TrustManagers trust this certificate chain");
  }

  @Override
  public X509Certificate[] getAcceptedIssuers()
  {
    List<X509Certificate> certs = new ArrayList<>();
    for (X509TrustManager trustManager : compositeTrustmanager)
    {
      certs.addAll(Arrays.asList(trustManager.getAcceptedIssuers()));
    }
    return certs.toArray(new X509Certificate[0]);
  }
}