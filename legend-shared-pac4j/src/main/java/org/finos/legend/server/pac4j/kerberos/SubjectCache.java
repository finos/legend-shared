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

import javax.security.auth.Subject;

class SubjectCache
{
  private final long creationTime = System.currentTimeMillis();
  private final Subject subject;
  private static final long validityPeriod = 1800000L;

  SubjectCache(Subject subject)
  {
    this.subject = subject;
  }

  Subject getSubject()
  {
    return this.subject;
  }

  boolean isValid()
  {
    return this.subject != null && System.currentTimeMillis() - this.creationTime <= validityPeriod;
  }
}