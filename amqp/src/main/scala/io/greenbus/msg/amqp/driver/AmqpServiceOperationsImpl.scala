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
package io.greenbus.msg.amqp.driver

import io.greenbus.msg.{Subscription, SubscriptionBinding}
import com.typesafe.scalalogging.LazyLogging
import io.greenbus.msg.service.{ServiceHandlerSubscription, ServiceHandler}
import io.greenbus.msg.amqp.broker.{AmqpSubscription, AmqpOperations}
import io.greenbus.msg.amqp.{AmqpMessage, AmqpAddressedMessage, AmqpServiceOperations}


class AmqpServiceOperationsImpl(ops: AmqpOperations) extends AmqpServiceOperations with LazyLogging {


  def bindCompetingService(handler: ServiceHandler, exchange: String): SubscriptionBinding = {

    ops.declareExchange(exchange, "topic")

    val amqpSub = ops.declareCompetingQueue(exchange + "_server")

    ops.bindQueue(amqpSub.queue, exchange, "any")

    bindServiceToAmqpSub(handler, amqpSub)
  }

  def bindRoutedService(handler: ServiceHandler): SubscriptionBinding = {

    val amqpSub = ops.declareExternallyBoundQueue()

    bindServiceToAmqpSub(handler, amqpSub)
  }


  def competingServiceBinding(exchange: String): ServiceHandlerSubscription = {
    ops.declareExchange(exchange, "topic")

    val amqpSub = ops.declareCompetingQueue(exchange + "_server")

    ops.bindQueue(amqpSub.queue, exchange, "any")

    amqpSubToSubscription(amqpSub)
  }

  def routedServiceBinding(): ServiceHandlerSubscription = {

    val amqpSub = ops.declareExternallyBoundQueue()

    amqpSubToSubscription(amqpSub)
  }

  private def amqpSubToSubscription(amqpSub: AmqpSubscription): ServiceHandlerSubscription = {
    new ServiceHandlerSubscription {
      def getId(): String = amqpSub.queue

      def cancel() { amqpSub.close() }

      def start(handler: ServiceHandler) {
        amqpSub.start {
          case AmqpMessage(bytes, correlationOpt, replyOpt) =>
            replyOpt match {
              case None => logger.warn("Service request without replyTo field")
              case Some(replyTo) => {
                correlationOpt match {
                  case None =>  logger.warn("Service request without correlation id")
                  case Some(id) => {
                    handler.handleMessage(bytes, resp => ops.sendResponse(replyTo, id, resp))
                  }
                }
              }
            }
        }
      }
    }
  }

  private def bindServiceToAmqpSub(handler: ServiceHandler, amqpSub: AmqpSubscription): SubscriptionBinding = {
    amqpSub.start {
      case AmqpMessage(bytes, correlationOpt, replyOpt) =>
        replyOpt match {
          case None => logger.warn("Service request without replyTo field")
          case Some(replyTo) => {
            correlationOpt match {
              case None =>  logger.warn("Service request without correlation id")
              case Some(id) => {
                handler.handleMessage(bytes, resp => ops.sendResponse(replyTo, id, resp))
              }
            }
          }
        }
    }

    new SubscriptionBinding {
      def getId(): String = amqpSub.queue
      def cancel() { amqpSub.close() }
    }
  }

  def bindQueue(queue: String, exchange: String, key: String) {
    ops.bindQueue(queue, exchange, key)
  }

  def declareExchange(exchange: String) {
    ops.declareExchange(exchange, "topic")
  }

  def publishEvent(exchange: String, msg: Array[Byte], key: String) {
    ops.publish(exchange, key, msg)
  }

  def publishBatch(messages: Seq[AmqpAddressedMessage]): Unit = {
    ops.publishBatch(messages)
  }

  def simpleSubscription(): Subscription[Array[Byte]] = {
    val amqpSub = ops.declareExternallyBoundQueue()

    new AmqpSubscriptionShim(amqpSub)
  }
}
