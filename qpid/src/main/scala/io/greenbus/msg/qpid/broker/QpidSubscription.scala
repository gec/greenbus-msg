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

import io.greenbus.msg.amqp.AmqpMessage
import org.apache.qpid.transport.{MessageTransfer, SessionException, Session, SessionListener}
import com.typesafe.scalalogging.slf4j.Logging
import io.greenbus.msg.amqp.broker.AmqpSubscription


object QpidSubscription {

  def apply(session: Session, detachSession: Session => Unit, queueName: String): AmqpSubscription = {
    new QpidSubscriptionImpl(session, detachSession, queueName)
  }

  private class Listener(handler: AmqpMessage => Unit) extends SessionListener with Logging {

    override def closed(s: Session) {
      logger.debug("Qpid session closed")
    }
    override def exception(s: Session, e: SessionException) {
      logger.error("Qpid session exception", e)
    }
    override def opened(s: Session) {
      logger.debug("Qpid session opened")
    }
    override def resumed(s: Session) {
      logger.debug("Qpid session resumed")
    }

    override def message(s: Session, msg: MessageTransfer) {
      val replyTo = Option(msg.getHeader.getMessageProperties.getReplyTo)
      val correlationId = Option(msg.getHeader.getMessageProperties.getCorrelationId)
      val dest = replyTo.map(r => QpidDestination(r.getExchange, r.getRoutingKey))
      try {
        handler(AmqpMessage(msg.getBodyBytes, correlationId.map(new String(_, "UTF-8")), dest.map(_.key)))
      } catch {
        case ex: Exception =>
          logger.error("Exception thrown during subscription event processing.", ex)
      }
      s.processed(msg)
    }
  }


  class QpidSubscriptionImpl(session: Session, detachSession: Session => Unit, queueName: String) extends AmqpSubscription with Logging {

    def start(handler: AmqpMessage => Unit) {
      //if (!connection.isConnected()) throw new ServiceIOException("Connection closed")
      // TODO: shoud we remove session listener on close?
      session.setSessionListener(new Listener(handler))
      if (!session.isClosing) {
        QpidChannelOperations.subscribe(session, queue)
      }
    }

    def queue = queueName

    def close() {
      QpidChannelOperations.close(session)
      detachSession(session)
    }

  }

}

