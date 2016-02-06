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
package io.greenbus.msg.rabbitmq.impl

import com.rabbitmq.client.Channel
import com.rabbitmq.client.AMQP.BasicProperties
import io.greenbus.msg.amqp.AmqpAddressedMessage
import io.greenbus.msg.amqp.broker.AmqpChannelOperations

class RabbitmqChannelOperations(channel: Channel) extends AmqpChannelOperations {

  def declareExchange(exchange: String, exchangeType: String) {
    channel.exchangeDeclare(exchange, exchangeType)
  }

  def bindQueue(queue: String, exchange: String, key: String) {
    channel.queueBind(queue, exchange, key)
  }

  def publish(exchange: String, key: String, bytes: Array[Byte]) {
    channel.basicPublish(exchange, key, null, bytes)
  }

  def publishBatch(messages: Seq[AmqpAddressedMessage]): Unit = {
    messages.foreach { message =>
      channel.basicPublish(message.exchange, message.key, null, message.message.msg)
    }
  }

  def sendRequest(exchange: String, key: String, correlationId: String, replyTo: String, bytes: Array[Byte]) {
    val properties = new BasicProperties.Builder()
      .correlationId(correlationId)
      .replyTo(replyTo)
      .build()

    channel.basicPublish(exchange, key, properties, bytes)
  }

  def sendResponse(queue: String, correlationId: String, bytes: Array[Byte]) {
    val properties = new BasicProperties.Builder()
      .correlationId(correlationId)
      .build()

    channel.basicPublish("", queue, properties, bytes)
  }
}
