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

import io.greenbus.msg.rabbitmq.RabbitmqConnection
import io.greenbus.msg.{RequestMessagingCodec, Session}
import io.greenbus.msg.amqp.AmqpServiceOperations
import io.greenbus.msg.util.Scheduler
import com.rabbitmq.client._
import io.greenbus.msg.amqp.driver.{AmqpMessagingDriver, AmqpServiceOperationsImpl}
import io.greenbus.msg.amqp.broker.{AmqpChannelOperations, AmqpOperations}
import io.greenbus.msg.impl.{ConnectionListening, SessionMessagingImpl}
import io.greenbus.msg.amqp.AmqpSettings
import java.util.concurrent.atomic.AtomicBoolean


class RabbitmqConnectionImpl(conn: Connection, timeoutMs: Long, scheduler: Scheduler) extends RabbitmqConnection with ConnectionListening {

  private val disconnectCalled = new AtomicBoolean(false)

  conn.addShutdownListener(new ShutdownListener {
    def shutdownCompleted(p1: ShutdownSignalException) {
      notifyListeners(disconnectCalled.get())
    }
  })

  private def getChannel(): Channel = conn.createChannel()
  private def getChannelOps(): AmqpChannelOperations = {
    new RabbitmqChannelOperations(getChannel())
  }

  private val operations = {
    val listenOps = new RabbitmqListenOperations(getChannel)
    AmqpOperations.pooled(getChannelOps, listenOps)
  }

  private val driver = new AmqpMessagingDriver(operations, timeoutMs, scheduler)

  def createSession(codec: RequestMessagingCodec): Session = {
    val messaging = new SessionMessagingImpl(driver, codec)
    Session.withMessaging(messaging)
  }

  def serviceOperations: AmqpServiceOperations = {
    new AmqpServiceOperationsImpl(operations)
  }

  def disconnect() {
    disconnectCalled.set(true)
    conn.close()
  }



}

object RabbitmqConnectionImpl {

  def operations(conn: Connection): AmqpOperations = {
    def getChannel(): Channel = conn.createChannel()
    def getChannelOps(): AmqpChannelOperations = {
      new RabbitmqChannelOperations(getChannel())
    }

    val listenOps = new RabbitmqListenOperations(getChannel)
    AmqpOperations.pooled(getChannelOps, listenOps)
  }

  def connectionFactory(config: AmqpSettings): ConnectionFactory = {
    val fac = new ConnectionFactory()
    fac.setHost(config.host)
    fac.setPort(config.port)
    fac.setVirtualHost(config.vhost)
    fac.setUsername(config.user)
    fac.setPassword(config.password)
    fac.setRequestedHeartbeat(config.heartBeatTimeSeconds)

    if (config.ssl.isDefined) {
      throw new IllegalArgumentException("SSL not currently supported")
    }
    fac
  }
}
