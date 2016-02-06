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
package io.greenbus.msg.amqp.japi

import io.greenbus.msg.amqp.japi.{AmqpSettings => JAmqpSettings}
import java.util.concurrent.ScheduledExecutorService
import io.greenbus.msg.util.Scheduler
import io.greenbus.msg.amqp.{AmqpSslSettings, AmqpBroker}
import io.greenbus.msg.amqp.japi.impl.AmqpConnectionShim
import io.greenbus.msg.amqp.{AmqpSettings => SAmqpSettings}

class AmqpConnectionFactory(settings: JAmqpSettings, broker: AmqpBroker, timeoutMs: Long, scheduler: ScheduledExecutorService) {
  import AmqpConnectionFactory._

  def connect(): AmqpConnection = {
    val scalaConn = broker.connection(convert(settings), timeoutMs, Scheduler(scheduler))
    new AmqpConnectionShim(scalaConn)
  }
}

object AmqpConnectionFactory {

  private def convert(settings: JAmqpSettings): SAmqpSettings = {

    val sslConfig = if (settings.getSsl) {
      Some(AmqpSslSettings(
        settings.getTrustStore,
        settings.getTrustStorePassword,
        Option(settings.getKeyStore),
        Option(settings.getKeyStorePassword)))
    } else {
      None
    }

    SAmqpSettings(
      settings.getHost,
      settings.getPort,
      settings.getVirtualHost,
      settings.getUser,
      settings.getPassword,
      settings.getHeartbeatTimeSeconds,
      settings.getTtlMilliseconds,
      sslConfig
    )
  }
}
