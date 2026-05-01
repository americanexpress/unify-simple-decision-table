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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
 * @author Deepak Arora
 */
public class TimeExpiryCacheTest {

  private static Logger logger = LoggerFactory.getLogger(TimeExpiryCacheTest.class);

  public static void main(String[] args) throws InterruptedException {
    test();
  }

  public static void test() throws InterruptedException {
    logger.info("Running test");
    TimeExpiryCacheFactory.init("name", 10000L, String.class);
    TimeExpiryCache<String> ec = TimeExpiryCacheFactory.instanceOf("name");
    logger.info("Inserting now");
    ec.put("1", "1", 1000);
    ec.put("2", "2", 2000);
    ec.put("3", "3", 3000);
    ec.put("4", "4", 4000);
    logger.info("# map -> {}", ec.getSize());

    Thread.sleep(1500);
    assertNull(ec.get("1"));
    assertNotNull(ec.get("2"));
    assertNotNull(ec.get("3"));
    assertNotNull(ec.get("4"));
    logger.info("# map -> {}", ec.getSize());

    Thread.sleep(1000);
    assertNull(ec.get("1"));
    assertNull(ec.get("2"));
    assertNotNull(ec.get("3"));
    assertNotNull(ec.get("4"));
    logger.info("# map -> {}", ec.getSize());

    Thread.sleep(1000);
    assertNull(ec.get("1"));
    assertNull(ec.get("2"));
    assertNull(ec.get("3"));
    assertNotNull(ec.get("4"));
    logger.info("# map -> {}", ec.getSize());

    Thread.sleep(3500);
    assertNull(ec.get("1"));
    assertNull(ec.get("2"));
    assertNull(ec.get("3"));
    assertNull(ec.get("4"));
    logger.info("# map -> {}", ec.getSize());

    ec.put("1", "1", 1000);
    Thread.sleep(5000);
    assertNull(ec.get("1"));
    logger.info("# map -> {}", ec.getSize());

    logger.info("Done");
    System.out.println();
    TimeExpiryCacheFactory.close("name");
  }

}
