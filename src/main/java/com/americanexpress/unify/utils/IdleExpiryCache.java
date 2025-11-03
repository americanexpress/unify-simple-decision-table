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

import com.americanexpress.unify.base.ErrorTuple;
import com.americanexpress.unify.base.UnifyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/*
 * @author Deepak Arora
 */
public class IdleExpiryCache<V> {

  private static final Logger logger = LoggerFactory.getLogger(IdleExpiryCache.class);

  private Map<String, Entry<V>> map = new ConcurrentHashMap<>();
  private Map<Long, Entry<V>> tsMap = new TreeMap<>();
  private final ReentrantLock lock = new ReentrantLock();
  private long idleTimeoutInMs = 0;
  private Class<V> clazz = null;

  private UnifyTimer timer = null;

  public IdleExpiryCache(String name, long idleTimeoutInMs, Class<V> clazz) {
    this.idleTimeoutInMs = idleTimeoutInMs;
    this.clazz = clazz;
    timer = new UnifyTimer(name);
    timer.start(new Task(), 30000L, 30000L);
  }

  public V get(String key) {
    V value = null;

    Entry<V> entry = map.get(key);
    if (entry != null) {
      value = entry.value;

      // remove the entry from tsMap and put it again
      try {
        lock.lock();
        tsMap.remove(entry.ts);
        entry.ts = System.nanoTime();
        tsMap.put(entry.ts, entry);
      }
      finally {
        lock.unlock();
      }
    }

    return value;
  }

  public void put(String key, V value) {
    if (clazz.isInstance(value) == false) {
      throw new UnifyException(new ErrorTuple("error", "Invalid object being inserted"));
    }

    Entry<V> entry = new Entry<>();
    entry.key = key;
    entry.value = value;
    entry.ts = System.nanoTime();
    map.put(key, entry);

    try {
      lock.lock();
      tsMap.put(entry.ts, entry);
    }
    finally {
      lock.unlock();
    }
  }

  public int getSize() {
    return map.size();
  }

  void close() {
    map.clear();
    map = null;
    try {
      lock.lock();
      tsMap.clear();
      tsMap = null;
    }
    finally {
      lock.unlock();
    }
    timer.close();
    timer = null;
  }

  void clear() {
    try {
      lock.lock();
      tsMap.clear();
    }
    finally {
      lock.unlock();
    }
    map.clear();
  }

  private class Task extends UnifyTimerTask {

    public Task() {
      // nothing to do
    }

    public void executeTask() {
      long now = System.nanoTime();

      try {
        lock.lock();

        // iterate through the tree map in ascending order i.e. from lowest to highest
        Set<Long> keySet = tsMap.keySet();
        Iterator<Long> iter = keySet.iterator();
        while (iter.hasNext()) {
          Long ts = iter.next();
          Entry<V> e = tsMap.get(ts);
          long interval = now - e.ts;
          if (interval >= (IdleExpiryCache.this.idleTimeoutInMs * 1000000)) {
            iter.remove();
            map.remove(e.key);
            logger.info("Removed key -> {}", e.key);
          }
          else {
            break;
          }
        }
      }
      catch (Exception e) {
        logger.error(e.getMessage());
      }
      finally {
        lock.unlock();
      }
    }

  }

  private static class Entry<V> {

    private String key;
    private V value;
    private long ts;

  }

}
