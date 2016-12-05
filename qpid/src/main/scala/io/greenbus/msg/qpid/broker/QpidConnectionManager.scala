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
package io.greenbus.msg.qpid.broker

import org.apache.qpid.transport.{Session, ConnectionException, ConnectionListener, Connection}
import com.typesafe.scalalogging.LazyLogging
import java.io.IOException
import io.greenbus.msg.amqp.broker.{AmqpChannelOperations, AmqpOperations}


class QpidConnectionManager(conn: Connection, ttlMilliseconds: Int) extends LazyLogging {

  private val mutex = new Object
  private var isDisconnected = false
  private var isClosed = false
  private var sessions = Set.empty[Session]
  private var onDisconnect = Option.empty[(Boolean) => Unit]


  private val listener = new ConnectionListener {

    // Should already be open
    def opened(c: Connection) {}

    def closed(c: Connection) {

      logger.info("Qpid connection closed")

      val expected = mutex.synchronized {
        // If we were already "disconnected" it means this was user-instigated through disconnect()
        val result = isDisconnected
        isDisconnected = true
        isClosed = true
        result
      }

      try {
        onDisconnect.foreach(call => call(expected))
      } catch {
        case ex: Exception =>
          logger.error("Unexpected error handling onDisconnect: " + ex.getMessage, ex)
      }

      mutex.synchronized {
        // Wake up anyone sitting in disconnect()
        mutex.notifyAll()
      }
    }

    def exception(c: Connection, ex: ConnectionException) {
      logger.error("Exception on qpid connection", ex)
    }
  }

  private val sessionSource = new QpidSessionSource {
    def createSession(): Session = {
      mutex.synchronized {
        if (isDisconnected) throw new IOException("Connection closed")
        val session = conn.createSession(0)
        sessions += session
        session
      }
    }

    // child subscriptions and workers call back to remove themselves from the list of sessions
    // if they have been closed manually by the user
    def detachSession(session: Session) {
      mutex.synchronized {
        sessions -= session
      }
    }
  }

  private def getChannelOps(): AmqpChannelOperations = {
    new QpidWorkerChannel(sessionSource.createSession(), sessionSource.detachSession, ttlMilliseconds)
  }

  private val ops = {
    val listenOps = QpidListenOperations(sessionSource)
    AmqpOperations.pooled(getChannelOps, listenOps)
  }

  //private val ops = AmqpOperations.pooled(sessionSource, ttlMilliseconds)

  conn.addConnectionListener(listener)



  def setOnDisconnect(callback: Boolean => Unit) {
    mutex.synchronized {
      onDisconnect = Some(callback)
    }
  }

  def disconnect() {
    mutex.synchronized {
      if (!isDisconnected) {
        isDisconnected = true
        sessions foreach { QpidChannelOperations.close }
        conn.close()
      }
      while (!isClosed) {
        mutex.wait()
      }
    }
  }

  // TODO: merge?
  def operations: AmqpOperations = ops


}

