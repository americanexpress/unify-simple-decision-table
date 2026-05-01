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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Deepak Arora
 */
public class TimeExpiryCache<V> {

  private static Logger logger = LoggerFactory.getLogger(TimeExpiryCache.class);

  private Map<String, Entry<V>> map = new ConcurrentHashMap<>();

  // the use of an array is a performance optimization. Almost all entries will
  // have unique expiry timestamp and only some may have the same expiry timestamp.
  // Hence instead of using a ArrayList which would waste default space, we use
  // an array. Most of the entries will have an array of size 1
  private Map<Long, Entry<V>[]> expiryTsMap = new TreeMap<>();

  private ReentrantLock lock = new ReentrantLock();
  private UnifyTimer timer = null;
  private long expiryTimeoutInMs = 0;

  TimeExpiryCache(String name, long expiryTimeoutInMs) {
    this.expiryTimeoutInMs = expiryTimeoutInMs;
    timer = new UnifyTimer(name);
    timer.start(new Task(), 60000, 60000);
  }

  public V get(String key) {
    Entry<V> entry = map.get(key);
    if (entry == null) {
      return null;
    }

    long now = System.currentTimeMillis();
    if (now > entry.expiryTs) {
      // remove entry from both maps and return null
      try {
        map.remove(entry.key);
        lock.lock();
        removeFromExpiryTsMap(entry);
      }
      finally {
        lock.unlock();
      }
      return null;
    }

    return entry.value;
  }

  private void removeFromExpiryTsMap(Entry<V> entry) {
    Entry<V>[] entryArr = expiryTsMap.get(entry.expiryTs);

    if (entryArr.length == 1) {
      expiryTsMap.remove(entry.expiryTs);
      return;
    }

    Entry<V>[] newEntryArr = new Entry[entryArr.length - 1];
    int dest = 0;
    for (int source = 0; source < entryArr.length; source++) {
      Entry<V> e = entryArr[source];
      if (e.key.equals(entry.key) == false) {
        newEntryArr[dest] = entryArr[source];
        dest++;
      }
    }
    expiryTsMap.put(entry.expiryTs, newEntryArr);
  }

  private void putInExpiryTsMap(Entry<V> entry) {
    Entry<V>[] entryArr = expiryTsMap.get(entry.expiryTs);
    Entry<V>[] newEntryArr = null;
    if (entryArr != null) {
      newEntryArr = new Entry[entryArr.length + 1];
      System.arraycopy(entryArr, 0, newEntryArr, 0, entryArr.length);
      newEntryArr[entryArr.length] = entry;
    }
    else {
      newEntryArr = new Entry[1];
      newEntryArr[0] = entry;
    }
    expiryTsMap.put(entry.expiryTs, newEntryArr);
  }

  public void put(String key, V value, long expiryTimeoutInMs) {
    Entry<V> entry = new Entry<>();
    entry.key = key;
    entry.value = value;
    entry.expiryTs = System.currentTimeMillis() + expiryTimeoutInMs;

    try {
      map.put(key, entry);
      lock.lock();
      putInExpiryTsMap(entry);
    }
    finally {
      lock.unlock();
    }
  }

  public void put(String key, V value) {
    put(key, value, this.expiryTimeoutInMs);
  }

  public int getSize() {
    return map.size();
  }

  void close() {
    map.clear();
    map = null;
    try {
      lock.lock();
      expiryTsMap.clear();
      expiryTsMap = null;
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
      expiryTsMap.clear();
    }
    finally {
      lock.unlock();
    }
    map.clear();
  }

  private class Task extends UnifyTimerTask {

    public void executeTask() {
      long now = System.currentTimeMillis();

      try {
        lock.lock();

        // iterate through the tree map in ascending order i.e. from lowest to highest
        Set<Long> keySet = expiryTsMap.keySet();
        Iterator<Long> iter = keySet.iterator();
        while (iter.hasNext()) {
          Long ts = iter.next();
          if (now > ts) {
            Entry<V>[] entryArr = expiryTsMap.get(ts);
            for (int i = 0; i < entryArr.length; i++) {
              Entry<V> e = entryArr[i];
              map.remove(e.key);
            }
            iter.remove();
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

  private class Entry<V> {

    public String key;
    public V value;
    public long expiryTs;

  }

}
