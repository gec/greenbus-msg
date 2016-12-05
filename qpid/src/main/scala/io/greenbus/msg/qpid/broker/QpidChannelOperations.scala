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
import com.typesafe.scalalogging.LazyLogging
import scala.{ Option => ScalaOption }
import java.io.IOException
import io.greenbus.msg.SessionUnusableException

class ChannelClosedException extends IOException("Session unexpectedly closing/closed")

case class QpidDestination(exchange: String, key: String)

/**
 * Helper methods for doing operations on qpid channels
 */
object QpidChannelOperations extends LazyLogging {

  def subscribe(session: Session, queue: String): Unit = {
    rewrap("starting subscription on: " + queue) {
      if (session.isClosing) throw new ChannelClosedException
      session.messageSubscribe(queue, queue, MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED, null, 0, null)
      session.messageFlow(queue, MessageCreditUnit.BYTE, Session.UNLIMITED_CREDIT)
      session.messageFlow(queue, MessageCreditUnit.MESSAGE, Session.UNLIMITED_CREDIT)
      session.sync()
      logger.debug("subscribed to queue: " + queue)
    }
  }

  def declareQueue(session: Session, queueNameTemplate: String, autoDelete: Boolean, exclusive: Boolean): String = {
    rewrap("declaring queue: " + queueNameTemplate + " autoDelete: " + autoDelete + " exclusive: " + exclusive) {

      if (session.isClosing) throw new ChannelClosedException

      val starIndex = queueNameTemplate.indexOf("*")
      val queue = if (starIndex != -1) {
        // session.getName.toString returns the uuid surrounded by double quotes for some reason
        val sessionName = session.getName.toString.replaceAll("\"", "")
        queueNameTemplate.patch(starIndex, sessionName, 1)
      } else queueNameTemplate
      var l = List.empty[Option]
      if (autoDelete) l ::= Option.AUTO_DELETE
      if (exclusive) l ::= Option.EXCLUSIVE
      session.queueDeclare(queue, null, null, l: _*)
      session.sync()
      logger.debug("Declared Queue: " + queue)
      queue //return the unique queue name
    }
  }

  def declareExchange(session: Session, exchange: String, exchangeType: String): Unit = {
    rewrap("declaring exchange: " + exchange + " type: " + exchangeType) {

      if (session.isClosing) throw new ChannelClosedException

      if (!exchange.startsWith("amq.")) {
        // Qpid quietly kills your session if you try to declare a built in queue, reevaluate if we switch to rabbit
        if (exchange.trim.length < 1) throw new Exception("Bad exchange name: " + exchange)
        session.exchangeDeclare(exchange, exchangeType, null, null)
        session.sync()
        logger.debug("Declared Exchange: " + exchange)
      }

    }
  }

  def bindQueue(session: Session, queue: String, exchange: String, key: String, unbindFirst: Boolean): Unit = {
    rewrap("binding: " + describeBinding(queue, exchange, key)) {
      if (session.isClosing) throw new ChannelClosedException
      if (unbindFirst) unbindQueue(session, queue, exchange, key)
      session.exchangeBind(queue, exchange, key, null)
      session.sync()
      logger.debug("Bound " + describeBinding(queue, exchange, key))
    }
  }

  def unbindQueue(session: Session, queue: String, exchange: String, key: String): Unit = {
    rewrap("unbinding: " + describeBinding(queue, exchange, key)) {
      if (session.isClosing) throw new ChannelClosedException
      session.exchangeUnbind(queue, exchange, key)
      session.sync()
      logger.debug("Unbound " + describeBinding(queue, exchange, key))
    }
  }

  def publish(session: Session, exchange: String, key: String, correlationId: Array[Byte], b: Array[Byte], replyTo: ScalaOption[QpidDestination], ttlMilliseconds: Int) = {
    rewrap("publishing to exchange: " + exchange + " key: " + key + " replyTo: " + replyTo) {
      if (session.isClosing) throw new ChannelClosedException
      val dev_props = new DeliveryProperties
      dev_props.setRoutingKey(key)
      val msg_props = new MessageProperties
      msg_props.setCorrelationId(correlationId)
      if (ttlMilliseconds > 0) dev_props.setTtl(ttlMilliseconds)
      replyTo.foreach(r => msg_props.setReplyTo(new ReplyTo(r.exchange, r.key)))
      val hdr = new Header(dev_props, msg_props)
      session.messageTransfer(exchange, MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED, hdr, b)
      session.sync()
    }
  }

  def publishBatch(session: Session, messages: Seq[AmqpAddressedMessage], ttlMilliseconds: Int) = {
    rewrap("publishing message batch") {
      messages.foreach { message =>
        if (session.isClosing)  {
          throw new ChannelClosedException
        }

        val devProps = new DeliveryProperties
        devProps.setRoutingKey(message.key)

        val msgProps = new MessageProperties
        message.message.correlationId.map(_.getBytes("UTF-8")).foreach(msgProps.setCorrelationId)

        if (ttlMilliseconds > 0) {
          devProps.setTtl(ttlMilliseconds)
        }

        val replyToDestOpt = message.message.replyTo.map(r => QpidDestination("amq.direct", r))
        replyToDestOpt.foreach(r => msgProps.setReplyTo(new ReplyTo(r.exchange, r.key)))

        val hdr = new Header(devProps, msgProps)
        session.messageTransfer(message.exchange, MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED, hdr, message.message.msg)
      }
      session.sync()
    }
  }

  def publish(session: Session, exchange: String, key: String, b: Array[Byte], replyTo: ScalaOption[QpidDestination], ttlMilliseconds: Int) = {
    rewrap("publishing to exchange: " + exchange + " key: " + key + " replyTo: " + replyTo) {
      if (session.isClosing) throw new ChannelClosedException
      val dev_props = new DeliveryProperties
      val msg_props = new MessageProperties
      dev_props.setRoutingKey(key)
      if (ttlMilliseconds > 0) dev_props.setTtl(ttlMilliseconds)
      replyTo.foreach(r => msg_props.setReplyTo(new ReplyTo(r.exchange, r.key)))
      val hdr = new Header(dev_props, msg_props)
      session.messageTransfer(exchange, MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED, hdr, b)
      session.sync()
    }
  }

  def describeBinding(queue: String, exchange: String, key: String): String =
    "queue " + queue + " to exchange " + exchange + " w/ key " + key

  /**
   * try to close the connection, silently eating failures if the closing fails (usually
   * this means it is already closed)
   */
  def close(session: Session) {
    if (!session.isClosing) try {
      session.close()
    } catch {
      case ex: Exception =>
        logger.error("Error closing session", ex)
    }
  }

  private def rewrap[A](msg: => String)(fun: => A): A = {
    try {
      fun
    } catch {
      case sse: SessionException =>
        // getException can be null in some cases
        ScalaOption(sse.getException).map(_.getErrorCode) match {
          case Some(ExecutionErrorCode.NOT_FOUND) =>
            throw new SessionUnusableException("Exchange not found. Usually indicates no services node is attached to the broker.")
          case Some(ExecutionErrorCode.UNAUTHORIZED_ACCESS) =>
            throw new IOException("Broker denied action, check that you have authorization to perform low level actions: " + msg, sse)
          case _ =>
            throw new IOException("Qpid error during " + msg + " cause: " + sse.getMessage, sse)
        }
      case ex: ChannelClosedException =>
        throw new SessionUnusableException(ex.getMessage)
      case ex: Exception =>
        throw new IOException("Unexpected error during " + msg + " cause: " + ex.getMessage, ex)
    }
  }
}