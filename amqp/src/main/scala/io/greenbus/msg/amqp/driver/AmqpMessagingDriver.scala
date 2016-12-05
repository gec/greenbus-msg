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

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import io.greenbus.msg.Subscription
import io.greenbus.msg.amqp.AmqpMessage
import io.greenbus.msg.amqp.broker.AmqpOperations
import io.greenbus.msg.driver.MessagingDriver
import io.greenbus.msg.util.{Cancelable, Scheduler}

import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.duration._


object AmqpMessagingDriver {
  val directExchange = "amq.direct"
}

class AmqpMessagingDriver(ops: AmqpOperations, timeoutMs: Long, scheduler: Scheduler) extends MessagingDriver with LazyLogging {
  import io.greenbus.msg.amqp.driver.AmqpMessagingDriver._

  private case class Record(promise: Promise[Array[Byte]], timer: Cancelable)
  private val outstanding = mutable.Map.empty[String, Record]

  private def removeOutstanding(id: String): Option[Record] = {
    outstanding.synchronized(outstanding.remove(id))
  }

  //ops.declareExchange(directExchange, "topic")
  private val subscription = ops.declareResponseQueue()
  subscription.start(onResponse)
  ops.bindQueue(subscription.queue, directExchange, subscription.queue)

  def request(requestId: String, message: Array[Byte], destination: Option[String]): Future[Array[Byte]] = {
    val correlationId = UUID.randomUUID().toString

    val respPromise = promise[Array[Byte]]

    val timer = scheduler.schedule(Duration(timeoutMs, MILLISECONDS)) {
      onTimeout(correlationId, requestId)
    }

    outstanding.synchronized {
      outstanding += (correlationId -> Record(respPromise, timer))
    }

    try {
      ops.sendRequest(requestId, destination.getOrElse("any"), correlationId, subscription.queue, message)
    } catch {
      case ex: Throwable =>
        logger.error("Problem issuing request with AMQP: " + ex)
        removeOutstanding(correlationId) match {
          case None => logger.warn("Failed issuing request, but future already timed out")
          case Some(Record(recordedPromise, recordedTimer)) => {
            timer.cancel()
            respPromise.failure(ex)
          }
        }
    }

    respPromise.future
  }


  def createSubscription(): Subscription[Array[Byte]] = {
    val amqpSub = ops.declareExternallyBoundQueue()
    new AmqpSubscriptionShim(amqpSub)
  }


  private def onResponse(amqpMessage: AmqpMessage) {

    amqpMessage.correlationId match {
      case None => logger.warn("Saw response without a correlation id")
      case Some(id) => {

        removeOutstanding(id) match {
          case None => logger.warn("Unexpected response w/ correlation id: " + id.toString)
          case Some(Record(respPromise, timer)) => {
            timer.cancel()

            respPromise.success(amqpMessage.msg)
          }
        }
      }
    }

  }

  private def onTimeout(correlationId: String, requestId: String) {
    removeOutstanding(correlationId) match {
      case None => logger.warn("Unexpected service response timeout w/ correlation id: " + correlationId.toString + " for request id: " + requestId)
      case Some(Record(respPromise, timer)) => {
        respPromise.failure(new TimeoutException("Timed out waiting for correlation id: " + correlationId + " for request id: " + requestId))
      }
    }
  }
}



