/**
 * Copyright 2011-2016 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.greenbus.msg.util

import scala.concurrent.duration.Duration
import java.util.concurrent.{TimeUnit, ScheduledExecutorService}

trait Cancelable {
  def cancel()
}

trait Scheduler {
  def schedule(fromNow: Duration)(func: => Unit): Cancelable
  def repeat(fromNow: Duration, period: Duration)(func: => Unit): Cancelable
}


object Scheduler {

  def apply(exe: ScheduledExecutorService): Scheduler = new SchedulerImpl(exe)

  implicit def convert(exe: ScheduledExecutorService): Scheduler = apply(exe)

  private class SchedulerImpl(exe: ScheduledExecutorService) extends Scheduler {

    def schedule(fromNow: Duration)(func: => Unit): Cancelable = {
      val runnable = new Runnable {
        def run() { func }
      }
      val schedFut = exe.schedule(runnable, fromNow.toMillis, TimeUnit.MILLISECONDS)

      new Cancelable {
        def cancel() { schedFut.cancel(true) }
      }
    }

    def repeat(fromNow: Duration, period: Duration)(func: => Unit): Cancelable = {
      val runnable = new Runnable {
        def run() { func }
      }
      val schedFut = exe.scheduleWithFixedDelay(runnable, fromNow.toMillis, period.toMillis, TimeUnit.MILLISECONDS)

      new Cancelable {
        def cancel() { schedFut.cancel(true) }
      }
    }
  }
}