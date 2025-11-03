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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * @author Deepak Arora
 */
public class IdleExpiryCacheFactory {

  private static final Map<String, IdleExpiryCache<?>> cacheMap = new ConcurrentHashMap<>();

  public static void createCache(String cacheName, long idleTimeoutInMs, Class<?> clazz) {
    cacheMap.put(cacheName, new IdleExpiryCache<>(cacheName, idleTimeoutInMs, clazz));
  }

  @SuppressWarnings("unchecked")
  public static <T> IdleExpiryCache<T> instanceOf(String cacheName) {
    return (IdleExpiryCache<T>)cacheMap.get(cacheName);
  }

  public static void close() {
    cacheMap.values().forEach(IdleExpiryCache::close);
    cacheMap.clear();
  }

  public static void close(String cacheName) {
    cacheMap.get(cacheName).close();
    cacheMap.remove(cacheName);
  }

  public static void clear(String cacheName) {
    cacheMap.get(cacheName).clear();
  }

  private IdleExpiryCacheFactory() {
    // nothing to do
  }

}
