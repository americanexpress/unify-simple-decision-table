/*
 * Copyright 2025 American Express Travel Related Services Company, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.americanexpress.unify.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TimeExpiryCacheFactory {

  private static Map<String, TimeExpiryCache> cacheMap = new HashMap<>();

  public static <T> void init(String cacheName, long expiryTimeoutInMs, T t) {
    cacheMap.put(cacheName, new TimeExpiryCache<T>(cacheName, expiryTimeoutInMs));
  }

  public static <T> TimeExpiryCache<T> instanceOf(String cacheName) {
    return cacheMap.get(cacheName);
  }

  public static void close() {
    Set<String> keys = cacheMap.keySet();
    for (String key : keys) {
      TimeExpiryCache cache = cacheMap.get(key);
      cache.close();
    }
    cacheMap.clear();
  }

  public static void close(String cacheName) {
    cacheMap.get(cacheName).close();
    cacheMap.remove(cacheName);
  }

  public static void clear(String cacheName) {
    cacheMap.get(cacheName).clear();
  }

}
