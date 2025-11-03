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

import com.americanexpress.unify.base.BaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimerTask;

/*
 * @author Deepak Arora
 */
public abstract class UnifyTimerTask extends TimerTask {

  private static Logger logger = LoggerFactory.getLogger(UnifyTimerTask.class);

  @Override
  public final void run() {
    try {
      executeTask();
    }
    catch (Exception e) {
      // nothing to do. We cannot have an exception being thrown as that will terminate the timer thread
      logger.info("Unexpected exception encountered while executing task, task class name -> {}, error message -> {}", this.getClass().getName(), e.getMessage());
      logger.info("Stack trace -> {}", BaseUtils.getStackTrace(e));
    }
  }

  public abstract void executeTask();

}
