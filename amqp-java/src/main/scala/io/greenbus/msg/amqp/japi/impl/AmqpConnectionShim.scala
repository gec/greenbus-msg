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
package io.greenbus.msg.amqp.japi.impl

import io.greenbus.msg.japi.{ConnectionCloseListener, Session}

import scala.collection.mutable
import io.greenbus.msg.amqp.AmqpConnection
import io.greenbus.msg.amqp.japi.{AmqpConnection => JAmqpConnection}
import io.greenbus.msg.RequestMessagingCodec
import io.greenbus.msg.japi.impl.SessionShim
import io.greenbus.msg.japi.amqp.AmqpServiceOperations
import io.greenbus.msg.japi.amqp.impl.AmqpServiceOperationsShim


class AmqpConnectionShim(conn: AmqpConnection) extends JAmqpConnection {

  def disconnect() {
    conn.disconnect()
  }

  private val listenMap = mutable.Map.empty[ConnectionCloseListener, Boolean => Unit]

  def addConnectionCloseListener(listener: ConnectionCloseListener) {
    val func = { expected: Boolean =>
      listener.onConnectionClosed(expected)
    }
    conn.addConnectionListener(func)
    listenMap.synchronized(listenMap.update(listener, func))
  }

  def removeConnectionCloseListener(listener: ConnectionCloseListener) {
    listenMap.synchronized {
      listenMap.get(listener).foreach(func => conn.removeConnectionListener(func))
    }
  }

  def createSession(): Session = null


  def createSession(codec: RequestMessagingCodec): Session = {
    new SessionShim(conn.createSession(codec))
  }

  def getServiceOperations: AmqpServiceOperations = {
    new AmqpServiceOperationsShim(conn.serviceOperations)
  }
}
