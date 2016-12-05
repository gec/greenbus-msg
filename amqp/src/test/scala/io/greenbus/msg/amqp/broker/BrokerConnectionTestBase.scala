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
package io.greenbus.msg.amqp.broker

import io.greenbus.msg.amqp.AmqpMessage
import org.scalatest.{Matchers, FunSuite}
import io.greenbus.msg.amqp.util.SyncVar


abstract class BrokerConnectionTestBase extends FunSuite with Matchers {

  /**
   * should be implemented by a broker test suite package
   */
  def testConnection(test: AmqpOperations => Unit)

  val defaultTimeout = 5000

  /*test("Disconnect events are fired") {
    testConnection { operations =>
      var fired = false
      conn.setOnDisconnect { expected =>
        fired = true
        expected should equal(true)
      }
      conn.disconnect()
      fired should equal(true)
    }
  }*/

  def syncVar[A](initial: A) = new SyncVar[A](Some(initial))

  test("Subscriptions work") {
    testConnection { operations =>

      val list = syncVar(Seq.empty[String])

      val sub = operations.declareExternallyBoundQueue()
      sub.start {
        message => list.atomic(seq => seq :+ new String(message.msg))
      }

      // bind the queue to the test exchange and send it a message
      operations.declareExchange("test", "topic")
      operations.bindQueue(sub.queue, "test", "hi")
      operations.publish("test", "hi", "hello".getBytes)
      operations.publish("test", "hi", "friend".getBytes)

      list.waitUntil(Seq("hello", "friend")) should equal (true)
    }
  }

  test("Messages arrive in order") {
    testConnection { operations =>

      val list = syncVar(Seq.empty[Int])
      val sub = operations.declareExternallyBoundQueue()
      sub.start {
        message => list.atomic(seq => seq :+ new String(message.msg).toInt)
      }

      // bind the queue to the test exchange and send it a message
      operations.declareExchange("test2", "topic")
      operations.bindQueue(sub.queue, "test2", "hi")

      val range = 0 to 1000

      range.foreach { i => operations.publish("test2", "hi", i.toString.getBytes) }

      list.waitUntil(range.toSeq)
    }
  }

  /*test("Subscriptions throw correct exception on close") {
    testConnection { operations =>

      val sub = operations.createQueueAndListen()

      broker.disconnect()

      intercept[IOException] {
        sub.start(_ => {})
      }
    }
  }*/

  test("Subscriptions are only closed once") {
    testConnection { operations =>
      val sub = operations.declareExternallyBoundQueue()
      sub.close()
    }
  }

  test("Throwing exception out of onMessage block") {
    testConnection { operations =>

      var explode = false

      val list = syncVar(Seq.empty[Int])
      val exceptions = syncVar(Seq.empty[Int])
      val sub = operations.declareExternallyBoundQueue()
      sub.start {
        message =>
          if (explode) {
            exceptions.atomic(seq => seq :+ message.msg.length)
            throw new IllegalArgumentException
          } else {
            list.atomic(seq => seq :+ message.msg.length)
          }
      }

      operations.declareExchange("test", "topic")
      operations.bindQueue(sub.queue, "test", "hi")
      operations.publish("test", "hi", "hello".getBytes)
      list waitUntil List(5)
      explode = true
      operations.publish("test", "hi", "hellohello".getBytes)
      exceptions waitUntil List(10)
      explode = false
      operations.publish("test", "hi", "friend".getBytes)

      list waitUntil List(5, 6)
    }
  }

  test("Routing keys work as expected") {
    testConnection { operations =>

      val list1 = syncVar(Seq.empty[String])
      val sub1 = operations.declareExternallyBoundQueue()
      sub1.start {
        message => list1.atomic(seq => seq :+ new String(message.msg))
      }

      val list2 = syncVar(Seq.empty[String])
      val sub2 = operations.declareExternallyBoundQueue()
      sub2.start {
        message => list2.atomic(seq => seq :+ new String(message.msg))
      }


      // bind the queue to the test exchange and send it a message
      operations.declareExchange("test3", "topic")
      operations.bindQueue(sub1.queue, "test3", "key1")
      operations.bindQueue(sub2.queue, "test3", "key2")

      operations.publish("test3", "badKey", "key1Msg".getBytes)
      operations.publish("test3", "key1", "key1Msg".getBytes)
      operations.publish("test3", "key2", "key2Msg".getBytes)

      list1 waitUntil (List("key1Msg"))
      list2 waitUntil (List("key2Msg"))
    }
  }

  class MockConsumer {
    val messages = syncVar(Seq.empty[List[Byte]])
    def onMessage(msg: AmqpMessage) {
      messages.atomic(seq => seq :+ msg.msg.toList)
    }
  }

  val r = new java.util.Random
  def randomBytes(count: Int) = {
    val arr = new Array[Byte](count)
    r.nextBytes(arr)
    arr
  }

  val testBytes: List[Byte] = randomBytes(100).toList

  test("Queue rotates consumers") {
    testConnection { operations =>
      val mc1 = new MockConsumer
      val mc2 = new MockConsumer

      operations.declareCompetingQueue("testRotate").start(mc1.onMessage)
      operations.declareCompetingQueue("testRotate").start(mc2.onMessage)
      operations.declareExchange("ex1", "topic")
      operations.bindQueue("testRotate", "ex1", "#")

      operations.publish("ex1", "foobar", testBytes.toArray)
      operations.publish("ex1", "foobar", testBytes.toArray)

      mc1.messages waitUntil Seq(testBytes)
      mc2.messages waitUntil Seq(testBytes)
    }
  }

  test("Exchange replicates messages") {
    testConnection { operations =>
      val mc1 = new MockConsumer
      val mc2 = new MockConsumer
      val sub1 = operations.declareExternallyBoundQueue()
      sub1.start(mc1.onMessage)
      val sub2 = operations.declareExternallyBoundQueue()
      sub2.start(mc2.onMessage)
      operations.declareExchange("ex1", "topic")
      operations.bindQueue(sub1.queue, "ex1", "#")
      operations.bindQueue(sub2.queue, "ex1", "#")
      operations.publish("ex1", "foobar", testBytes.toArray)

      mc1.messages waitUntil Seq(testBytes)
      mc2.messages waitUntil Seq(testBytes)
    }
  }

  test("All messages are received when connection is used concurrently") {
    testConnection { operations =>
      operations.declareExchange("ex", "topic")

      val count = syncVar[Int](0)
      val sub = operations.declareExternallyBoundQueue()
      sub.start {
        message => count.atomic(num => num + 1)
      }

      operations.bindQueue(sub.queue, "ex", "#")
      val bytes = (1 to 100).map(_ => randomBytes(1))

      bytes.foreach { arr =>
        new Thread(new Runnable {
          def run() {
            operations.publish("ex", "foo", arr)
          }
        }).start()
      }

      count waitUntil 100
      count.waitWhile(100, 500, false) should equal(false)

    }
  }
}
