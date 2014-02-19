/*
 * Copyright 2010-2012 henryzhao81@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.orientechnologies.orient.core.db.record;

import com.orientechnologies.orient.core.config.OStorageConfiguration;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 2/14/14
 */
public class OCurrentStorageVersions {
  public final int storageVersion;

  public OCurrentStorageVersions(OStorageConfiguration configuration) {
    this.storageVersion = configuration.storageVersion;
  }

  /**
   * @return Whether class of is detected by cluster id or it is taken from documents serialized content.
   * @since 1.7
   */
  public boolean classesAreDetectedByClusterId() {
    return storageVersion >= 10;
  }

  public boolean legacyStringSerializer() {
    return storageVersion <= 8;
  }
}