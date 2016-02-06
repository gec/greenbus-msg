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
package io.greenbus.msg.qpid.driver

import io.greenbus.msg.amqp.driver.{AmqpServiceOperationsImpl, AmqpMessagingDriver, AmqpMessagingTestBase}
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import io.greenbus.msg.amqp.{AmqpSettings, AmqpServiceOperations}
import io.greenbus.msg.qpid.broker.QpidBrokerConnectionFactory
import java.util.concurrent.{Executor, Executors}
import io.greenbus.msg.util.Scheduler

@RunWith(classOf[JUnitRunner])
class QpidDriverTest extends AmqpMessagingTestBase {

  val defaults = AmqpSettings("127.0.0.1", 5672, "test", "qpid", "qpid", 30, 5000, None)

  def runTest(test: (AmqpMessagingDriver, AmqpServiceOperations, Executor) => Unit) {
    val exe = Executors.newScheduledThreadPool(5)
    val factory = new QpidBrokerConnectionFactory(defaults)
    val conn = factory.connect

    val serviceOps = new AmqpServiceOperationsImpl(conn.operations)
    val driver = new AmqpMessagingDriver(conn.operations, 60*1000, Scheduler(exe))
    try {
      test(driver, serviceOps, exe)
    } finally {
      conn.disconnect()
    }
  }
}
