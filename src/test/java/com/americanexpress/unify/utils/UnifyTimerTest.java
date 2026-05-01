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

/*
 * @author Deepak Arora
 */
public class UnifyTimerTest {

  public static void main(String[] args) throws InterruptedException {
    UnifyTimerFactory.create("test");
    MyTimer t = new MyTimer();
    //    t.scheduleRecurringTask();
    t.scheduleOneOffTask();
    Thread.sleep(5000);
    t.stop();
  }

}

class MyTimer {

  private UnifyTimer timer = null;

  public MyTimer() {
    timer = UnifyTimerFactory.instanceOf("test");
  }

  public void scheduleOneOffTask() {
    timer.start(new MyTimerTask(), 1000);
  }

  public void scheduleRecurringTask() {
    timer.start(new MyTimerTask(), 1000, 1000);
    timer.start(new MyTimerTask1(), 1000, 1000);
  }

  public void stop() {
    UnifyTimerFactory.close("test");
  }

}

class MyTimerTask extends UnifyTimerTask {

  public void executeTask() {
    System.out.println("Task has run");
  }

}

class MyTimerTask1 extends UnifyTimerTask {

  public void executeTask() {
    System.out.println("Task1 has run");
  }

}
