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
package io.greenbus.msg.qpid.broker

import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import java.io.IOException
import io.greenbus.msg.amqp.util.SyncVar
import io.greenbus.msg.amqp.{AmqpSslSettings, AmqpSettings}
import io.greenbus.msg.amqp.broker.{AmqpOperations, BrokerConnectionTestBase}


@RunWith(classOf[JUnitRunner])
class QpidBrokerConnectionTest extends BrokerConnectionTestBase {


  val defaults = AmqpSettings("127.0.0.1", 5672, "test", "qpid", "qpid", 30, 5000, None)
  val sslDefaults = AmqpSslSettings("etc/trust-store.jks", "password",  None, None)


  def fixture(config: AmqpSettings)(test: QpidConnectionManager => Unit): Unit = {
    val factory = new QpidBrokerConnectionFactory(config)
    val conn = factory.connect
    try {
      test(conn)
    } finally {
      conn.disconnect()
    }
  }

  def testConnection(test: AmqpOperations => Unit) {
    fixture(defaults) { conn =>
      test(conn.operations)
    }
  }

  test("Connection Timeout") {
    val amqpSettings = AmqpSettings("127.0.0.1", 10000, "", "", "", 30, 5000, None)
    intercept[IOException](fixture(amqpSettings) { conn => })
  }

  test("Bad Ssl configuration") {
    val sslConfig = AmqpSslSettings("badFileName", "a", None, None)
    val amqpSettings = defaults.copy(ssl = Some(sslConfig))
    val ex = intercept[IOException](fixture(amqpSettings) { conn => })
    ex.getMessage should include("badFileName")
  }

  test("Bad Ssl password") {

    val sslConfig = AmqpSslSettings("src/test/resources/trust-store.jks", "9090909", None, None)
    val amqpSettings = defaults.copy(ssl = Some(sslConfig))
    val ex = intercept[IOException](fixture(amqpSettings) { conn => })
    ex.getMessage should include("SSL Context")
  }

  test("Valid Ssl configuration against non-ssl server") {

    val sslConfig = AmqpSslSettings("src/test/resources/trust-store.jks", "jjjjjjj", None, None)
    val amqpSettings = defaults.copy(ssl = Some(sslConfig))
    val ex = intercept[IOException](fixture(amqpSettings) { conn => })
    ex.getMessage should include("SSLReceiver")
  }

  test("Test Broker TTL") {

    // short TTL for testing (default is 5 seconds)
    val TTL = 100

    val config = defaults.copy(ttlMilliseconds = TTL)
    fixture(config) { broker =>

      val sub = broker.operations.declareExternallyBoundQueue()
      val queue = sub.queue
      broker.operations.declareExchange("test", "topic")
      broker.operations.bindQueue(queue, "test", "hi")

      // publish some messages we expect to timeout
      broker.operations.publish("test", "hi", "Old message should have expired".getBytes)
      broker.operations.publish("test", "hi", "Other old message should have expired".getBytes)

      // sleep for double the TTL time to make sure qpid has had time to kill the messages
      Thread.sleep(TTL * 2)

      // publish some measurements that we will subscribe to before they expire
      val okMessages = List("Recent message should be delivered", "Other Recent message should be delivered")
      okMessages.foreach { msg =>
        broker.operations.publish("test", "hi", msg.getBytes)
      }

      val list = new SyncVar[Seq[String]](Some(Seq.empty[String]))
      sub.start {
        message => list.atomic(seq => seq :+ new String(message.msg))
      }

      list.waitUntil(okMessages)

      sub.close()
    }
  }
}

