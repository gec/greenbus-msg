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


import io.greenbus.msg.amqp.AmqpAddressedMessage
import org.apache.qpid.transport._

import com.typesafe.scalalogging.slf4j.Logging
import io.greenbus.msg.amqp.broker.AmqpChannelOperations

object QpidWorkerChannel {
  class LoggingSessionListener extends SessionListener with Logging {
    def closed(s: Session) {
      logger.debug("Qpid session closed")
    }
    def exception(s: Session, e: SessionException) {
      logger.error("Qpid session exception", e)
    }
    def opened(s: Session) {
      logger.debug("Qpid session opened")
    }
    def resumed(s: Session) {
      logger.debug("Qpid session resumed")
    }
    def message(s: Session, msg: MessageTransfer) {
      logger.error("Unexpected msg on worker channel: " + msg)
    }

  }
}


class QpidWorkerChannel(session: Session, detachSession: Session => Unit, ttlMilliseconds: Int) extends AmqpChannelOperations {

  private val listener = new QpidWorkerChannel.LoggingSessionListener

  session.setSessionListener(listener)

  def isOpen = !session.isClosing


  def declareExchange(exchange: String, exchangeType: String) {
    QpidChannelOperations.declareExchange(session, exchange, exchangeType)
  }

  def bindQueue(queue: String, exchange: String, key: String) {
    QpidChannelOperations.bindQueue(session, queue, exchange, key, false)
  }

  def publish(exchange: String, key: String, bytes: Array[Byte]) {
    QpidChannelOperations.publish(session, exchange, key, bytes, None, ttlMilliseconds)
  }

  def publishBatch(messages: Seq[AmqpAddressedMessage]): Unit = {
    QpidChannelOperations.publishBatch(session, messages, ttlMilliseconds)
  }

  def sendRequest(exchange: String, key: String, correlationId: String, replyTo: String, bytes: Array[Byte]) {
    val replyToDest = QpidDestination("amq.direct", replyTo)
    QpidChannelOperations.publish(session, exchange, key, correlationId.getBytes("UTF-8"), bytes, Some(replyToDest), ttlMilliseconds)
  }

  def sendResponse(queue: String, correlationId: String, bytes: Array[Byte]) {
    QpidChannelOperations.publish(session, "amq.direct", queue, correlationId.getBytes("UTF-8"), bytes, None, ttlMilliseconds)
  }

  def close() {
    QpidChannelOperations.close(session)
    detachSession(session)
  }
}