// Copyright 2023 Goldman Sachs
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

package org.finos.legend.shared.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Marker interface for preview and experimental APIs. Breaking changes may be
 * introduced to elements marked as {@link ExperimentalApi}. Users should assume that anything annotated as experimental will change or break in subsequent releases.
 */
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
public @interface ExperimentalApi
{
}