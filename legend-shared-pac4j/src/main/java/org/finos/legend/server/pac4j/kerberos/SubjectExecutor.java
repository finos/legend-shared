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
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Objects;
import java.util.function.Supplier;

public class SubjectExecutor
{
  private final Supplier<Subject> subjectSupplier;
  private SubjectCache subjectCache = new SubjectCache(null);

  public SubjectExecutor(Supplier<Subject> subjectSupplier)
  {
    this.subjectSupplier = subjectSupplier;
  }

  private Subject getSubject()
  {
    if (Objects.isNull(this.subjectSupplier))
    {
      return null;
    }
    if (!this.subjectCache.isValid())
    {
      this.subjectCache = new SubjectCache(this.subjectSupplier.get());
    }
    return this.subjectCache.getSubject();
  }

  /**
   * Execute a privileged action using the supplied Subject, if it exists.
   *
   * @param action The action to execute
   */
  public <T> T execute(final PrivilegedAction<T> action)
  {
    Subject subject = getSubject();
    if (!Objects.isNull(subject))
    {
      return Subject.doAs(subject, action);
    }
    return action.run();
  }

  /**
   * Execute a privileged action which throws an Exception using the supplied Subject, if it exists.
   *
   * @param action The exception-throwing action to execute
   */
  public <T> T executeWithException(final PrivilegedExceptionAction<T> action) throws PrivilegedActionException
  {
    Subject subject = getSubject();
    if (!Objects.isNull(subject))
    {
      return Subject.doAs(subject, action);
    }
    try
    {
      return action.run();
    } catch (Exception e)
    {
      throw new PrivilegedActionException(e);
    }
  }
}
