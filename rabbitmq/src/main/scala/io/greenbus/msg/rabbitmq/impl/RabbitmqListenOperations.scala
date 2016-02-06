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

import com.rabbitmq.client.{Envelope, DefaultConsumer, Channel}
import io.greenbus.msg.amqp.AmqpMessage
import io.greenbus.msg.amqp.broker.{AmqpSubscription, AmqpListenOperations}
import com.rabbitmq.client.AMQP.BasicProperties
import java.io.IOException
import java.util.UUID

class RabbitmqListenOperations(acquireChannel: () => Channel) extends AmqpListenOperations {
  import RabbitmqListenOperations._

  def createQueueAndListen(): AmqpSubscription = {
    val channel = acquireChannel()
    val queueName = channel.queueDeclare().getQueue

    new RabbitmqSubscription(queueName, channel)
  }

  def listen(queue: String): AmqpSubscription = {
    val channel = acquireChannel()
    val acquiredName = channel.queueDeclare(queue, false, false, true, null).getQueue
    if (queue != acquiredName) {
      throw new IOException("Not given queue name we asked for. Got: " + acquiredName + " requested: " + queue)
    }

    new RabbitmqSubscription(acquiredName, channel)
  }

  def declareResponseQueue(): AmqpSubscription = {
    val channel = acquireChannel()
    val queueName = channel.queueDeclare().getQueue

    new RabbitmqSubscription(queueName, channel)
  }

  def declareExternallyBoundQueue(): AmqpSubscription = {
    val channel = acquireChannel()
    val queue = UUID.randomUUID().toString
    val acquiredName = channel.queueDeclare(queue, false, false, true, null).getQueue
    if (queue != acquiredName) {
      throw new IOException("Not given queue name we asked for. Got: " + acquiredName + " requested: " + queue)
    }

    new RabbitmqSubscription(acquiredName, channel)
  }

  def declareCompetingQueue(name: String): AmqpSubscription = {
    val channel = acquireChannel()
    val acquiredName = channel.queueDeclare(name, false, false, true, null).getQueue
    if (name != acquiredName) {
      throw new IOException("Not given queue name we asked for. Got: " + acquiredName + " requested: " + name)
    }

    new RabbitmqSubscription(acquiredName, channel)
  }

}

object RabbitmqListenOperations {

  private class ConsumerShim(channel: Channel, handler: AmqpMessage => Unit) extends DefaultConsumer(channel) {
    override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
      val msg = AmqpMessage(body, Option(properties.getCorrelationId), Option(properties.getReplyTo))
      handler(msg)
    }
  }

  private class RabbitmqSubscription(queueName: String, channel: Channel) extends AmqpSubscription {
    def start(handler: (AmqpMessage) => Unit) {
      channel.basicConsume(queueName, new ConsumerShim(channel, handler))
    }

    def queue: String = queueName

    def close() {
      channel.close()
    }
  }
}
