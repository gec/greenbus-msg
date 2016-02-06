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
package io.greenbus.msg.amqp.driver

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import io.greenbus.msg.amqp.AmqpServiceOperations
import io.greenbus.msg.service.ServiceHandler
import scala.collection.mutable
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.Executor

abstract class AmqpMessagingTestBase extends FunSuite with ShouldMatchers {

  implicit def stringToBytes(str: String): Array[Byte] = str.getBytes("UTF-8")
  implicit def bytesToString(bytes: Array[Byte]): String = new String(bytes, "UTF-8")

  def responseFor(request: String): String = request + "_response"

  class QueueingHandler(exe: Executor) extends ServiceHandler {
    val received = mutable.Queue.empty[String]

    def handleMessage(msg: Array[Byte], responseHandler: (Array[Byte]) => Unit) {
      exe.execute(new Runnable {
        def run() {

          received.synchronized {
            received += msg
          }

          responseHandler(responseFor(bytesToString(msg)))
        }
      })
    }
  }


  def runTest(test: (AmqpMessagingDriver, AmqpServiceOperations, Executor) => Unit)

  test("Basic request/response") {
    runTest { (driver, ops, exe) =>

      val handler = new QueueingHandler(exe)
      ops.bindCompetingService(handler, "testEx")

      val future = driver.request("testEx", "something", None)

      val response = bytesToString(Await.result(future, Duration(5000, MILLISECONDS)))
      response should equal(responseFor("something"))

      handler.received.toSeq should equal(Seq("something"))

    }
  }

  test("Routed request/response") {
    runTest { (driver, ops, exe) =>

      val handler = new QueueingHandler(exe)
      val dest = ops.bindRoutedService(handler).getId()

      ops.bindQueue(dest, "testEx", dest)
      val future = driver.request("testEx", "something", Some(dest))

      val response = bytesToString(Await.result(future, Duration(5000, MILLISECONDS)))
      response should equal(responseFor("something"))

      handler.received.toSeq should equal(Seq("something"))

    }
  }

  test("Routed/Competing non interference") {
    runTest { (driver, ops, exe) =>

      val routedHandler = new QueueingHandler(exe)
      val dest = ops.bindRoutedService(routedHandler).getId()
      ops.bindQueue(dest, "testEx", dest)

      val competeHandler = new QueueingHandler(exe)
      ops.bindCompetingService(competeHandler, "testEx")

      var routedFutures = List.empty[Future[Array[Byte]]]
      var competingFutures = List.empty[Future[Array[Byte]]]

      routedFutures ::= driver.request("testEx", "a", Some(dest))
      competingFutures ::= driver.request("testEx", "x", None)
      routedFutures ::= driver.request("testEx", "b", Some(dest))
      competingFutures ::= driver.request("testEx", "y", None)
      routedFutures ::= driver.request("testEx", "c", Some(dest))
      competingFutures ::= driver.request("testEx", "z", None)

      val routedResps = Await.result(Future.sequence(routedFutures), Duration(5000, MILLISECONDS))
      val competingResps = Await.result(Future.sequence(competingFutures), Duration(5000, MILLISECONDS))

      routedResps.map(bytesToString).toSet should equal(Set("a", "b", "c").map(responseFor))
      competingResps.map(bytesToString).toSet should equal(Set("x", "y", "z").map(responseFor))

      routedHandler.received.toSet should equal(Set("a", "b", "c"))
      competeHandler.received.toSet should equal(Set("x", "y", "z"))
    }
  }
}
