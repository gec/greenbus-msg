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

import java.util.concurrent.ConcurrentLinkedQueue
import io.greenbus.msg.SessionUnusableException
import com.typesafe.scalalogging.slf4j.Logging
import io.greenbus.msg.amqp.AmqpAddressedMessage

class AmqpChannelPool(acquireChannel: () => AmqpChannelOperations) extends AmqpChannelOperations with Logging {

  private val channels = new ConcurrentLinkedQueue[AmqpChannelOperations]()

  private def borrow(): AmqpChannelOperations = {
    Option(channels.poll()).getOrElse(acquireChannel())
  }

  private def unborrow(channel: AmqpChannelOperations) {
    channels.add(channel)
  }

  private def execute[A](runWith: AmqpChannelOperations => A): A = {
    val channel = borrow()
    val result = try {
      runWith(channel)
    } catch {
      case dead: SessionUnusableException =>
        logger.debug("Removing channel from AMQP channel pool")
        throw dead
      case ex: Throwable =>
        unborrow(channel)
        throw ex
    }

    unborrow(channel)
    result
  }

  def declareExchange(exchange: String, exchangeType: String) {
    execute(_.declareExchange(exchange, exchangeType))
  }

  def bindQueue(queue: String, exchange: String, key: String) {
    execute(_.bindQueue(queue, exchange, key))
  }

  def publish(exchange: String, key: String, bytes: Array[Byte]) {
    execute(_.publish(exchange, key, bytes))
  }

  def publishBatch(messages: Seq[AmqpAddressedMessage]): Unit = {
    execute(_.publishBatch(messages))
  }

  def sendRequest(exchange: String, key: String, correlationId: String, replyTo: String, bytes: Array[Byte]) {
    execute(_.sendRequest(exchange, key, correlationId, replyTo, bytes))
  }

  def sendResponse(queue: String, correlationId: String, bytes: Array[Byte]) {
    execute(_.sendResponse(queue, correlationId, bytes))
  }
}
