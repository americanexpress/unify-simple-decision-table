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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/*
 * @author Deepak Arora
 */
public class IdleExpiryCache<V> {

  private static final Logger logger = LoggerFactory.getLogger(IdleExpiryCache.class);

  private Map<String, Entry<V>> entryMap = new ConcurrentHashMap<>();
  private Map<Long, Map<String, Entry<V>>> tsMap = new TreeMap<>();
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

    try {
      lock.lock();

      if (entryMap == null) {
        throw new UnifyException(new ErrorTuple("error", "cache is closed"));
      }

      Entry<V> entry = entryMap.get(key);
      if (entry != null) {
        value = entry.value;

        // remove the entry from map in tsMap and remove entry from tsMap if map is empty
        Map<String, Entry<V>> map = tsMap.get(entry.ts);
        map.remove(entry.key);
        if (map.isEmpty() == true) {
          tsMap.remove(entry.ts);
        }

        // assign a new ts
        entry.ts = System.nanoTime();

        // insert a new element in tsMap or update the existing one and put in tsMap
        map = tsMap.get(entry.ts);
        if (map == null) {
          map = new HashMap<>();
        }
        map.put(entry.key, entry);
        tsMap.put(entry.ts, map);
      }
    }
    finally {
      lock.unlock();
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

    try {
      lock.lock();

      if (entryMap == null) {
        throw new UnifyException(new ErrorTuple("error", "cache is closed"));
      }

      // create the new entry
      Entry<V> oldEntry = entryMap.put(key, entry);

      // remove the old entry from tsMap
      if (oldEntry != null) {
        Map<String, Entry<V>> map = tsMap.get(oldEntry.ts);
        map.remove(oldEntry.key);
        if (map.isEmpty() == true) {
          tsMap.remove(oldEntry.ts);
        }
      }

      // next we insert the entry into tsMap
      Map<String, Entry<V>> map = tsMap.get(entry.ts);
      if (map == null) {
        map = new HashMap<>();
      }
      map.put(entry.key, entry);
      tsMap.put(entry.ts, map);
    }
    finally {
      lock.unlock();
    }
  }

  public void remove(String key) {
    try {
      lock.lock();

      if (entryMap == null) {
        throw new UnifyException(new ErrorTuple("error", "cache is closed"));
      }

      // get the entry
      Entry<V> entry = entryMap.get(key);
      if (entry != null) {
        entryMap.remove(key);
        Map<String, Entry<V>> map = tsMap.get(entry.ts);
        map.remove(entry.key);
        if (map.isEmpty() == true) {
          tsMap.remove(entry.ts);
        }
      }
    }
    finally {
      lock.unlock();
    }
  }

  public int getSize() {
    try {
      lock.lock();
      if (entryMap == null) {
        throw new UnifyException(new ErrorTuple("error", "cache is closed"));
      }
      else {
        return entryMap.size();
      }
    }
    finally {
      lock.unlock();
    }
  }

  void close() {
    try {
      lock.lock();

      if (entryMap == null) {
        return;
      }

      entryMap.clear();
      entryMap = null;

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
      if (entryMap == null) {
        throw new UnifyException(new ErrorTuple("error", "cache is closed"));
      }
      entryMap.clear();
      tsMap.clear();
    }
    finally {
      lock.unlock();
    }
  }

  private class Task extends UnifyTimerTask {

    public Task() {
      // nothing to do
    }

    public void executeTask() {
      long now = System.nanoTime();

      try {
        lock.lock();

        if (entryMap == null) {
          return;
        }

        // iterate through the tree map in ascending order i.e. from lowest to highest
        Set<Long> keySet = tsMap.keySet();
        Iterator<Long> iter = keySet.iterator();
        while (iter.hasNext()) {
          Long ts = iter.next();
          long interval = now - ts;
          if (interval >= (IdleExpiryCache.this.idleTimeoutInMs * 1000000)) {
            Map<String, Entry<V>> map = tsMap.get(ts);
            Set<String> mapKeySet = map.keySet();
            for (String key : mapKeySet) {
              Entry<V> e = map.get(key);
              entryMap.remove(e.key);
              logger.info("Removed key -> {}", e.key);
            }
            iter.remove();
          }
          else {
            break;
          }
        }
      }
      catch (Exception e) {
        logger.error("IdleExpiryCache execute task failed", e);
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
