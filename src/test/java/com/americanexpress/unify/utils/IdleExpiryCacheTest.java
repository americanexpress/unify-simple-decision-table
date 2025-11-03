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

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/*
 * @author Deepak Arora
 */
class IdleExpiryCacheTest {

  private static Logger logger = LoggerFactory.getLogger(IdleExpiryCacheTest.class);

  @Test
  void test() throws InterruptedException {
    IdleExpiryCache<String> ec = new IdleExpiryCache<>("cache_1", 1000, String.class);
    logger.info("Inserting now");
    ec.put("1", "1");
    ec.put("2", "2");
    ec.put("3", "3");
    ec.put("4", "4");

    Thread.sleep(5000);
    logger.info("Getting values now");
    assertNotNull(ec.get("1"));
    assertNotNull(ec.get("2"));
    assertNotNull(ec.get("3"));
    assertNotNull(ec.get("4"));
  }

}
