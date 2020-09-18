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

package org.finos.legend.opentracing;

import java.util.Collections;
import java.util.List;

public class CookieAuthenticationProvider implements AuthenticationProvider
{

  private final String cookieName;
  private final CookieValueSupplier cookieValueSupplier;

  public CookieAuthenticationProvider(String cookieName, String cookieValue)
  {
    this(cookieName, () -> cookieValue);
  }

  public CookieAuthenticationProvider(String cookieName, CookieValueSupplier cookieValueSupplier)
  {
    this.cookieName = cookieName;
    this.cookieValueSupplier = cookieValueSupplier;
  }

  @Override
  public List<HeaderEntry> getAuthenticationHeaders()
  {
    return Collections.singletonList(
        new HeaderEntry("Cookie", cookieName + "=" + cookieValueSupplier.value()));
  }

  @FunctionalInterface
  public interface CookieValueSupplier
  {
    String value();
  }
}
