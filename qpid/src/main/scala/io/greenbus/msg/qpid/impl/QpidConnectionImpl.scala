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
package io.greenbus.msg.qpid.impl

import io.greenbus.msg.qpid.broker.QpidConnectionManager
import com.typesafe.scalalogging.slf4j.Logging
import io.greenbus.msg.{RequestMessagingCodec, Session}
import io.greenbus.msg.amqp.{AmqpConnection, AmqpServiceOperations}
import io.greenbus.msg.impl.{ConnectionListening, SessionMessagingImpl}
import io.greenbus.msg.util.Scheduler
import io.greenbus.msg.amqp.driver.{AmqpMessagingDriver, AmqpServiceOperationsImpl}

class QpidConnectionImpl(mgr: QpidConnectionManager, timeoutMs: Long, scheduler: Scheduler) extends AmqpConnection with ConnectionListening with Logging {

  mgr.setOnDisconnect { expected =>
    notifyListeners(expected)
  }

  private val serviceOps = new AmqpServiceOperationsImpl(mgr.operations)
  private val messagingDriver = new AmqpMessagingDriver(mgr.operations, timeoutMs, scheduler)

  def disconnect() {
    mgr.disconnect()
  }

  def createSession(codec: RequestMessagingCodec): Session = {
    val messaging = new SessionMessagingImpl(messagingDriver, codec)
    Session.withMessaging(messaging)
  }

  def serviceOperations: AmqpServiceOperations = serviceOps

}
