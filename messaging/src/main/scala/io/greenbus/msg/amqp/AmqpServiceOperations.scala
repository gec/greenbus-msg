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
package io.greenbus.msg.amqp

import io.greenbus.msg.{Subscription, SubscriptionBinding}
import io.greenbus.msg.service.{ServiceHandlerSubscription, ServiceHandler}


trait AmqpServiceOperations {

  def publishEvent(exchange: String, msg: Array[Byte], key: String)
  def publishBatch(messages: Seq[AmqpAddressedMessage])

  def bindCompetingService(handler: ServiceHandler, exchange: String): SubscriptionBinding
  def bindRoutedService(handler: ServiceHandler): SubscriptionBinding

  def competingServiceBinding(exchange: String): ServiceHandlerSubscription
  def routedServiceBinding(): ServiceHandlerSubscription

  def bindQueue(queue: String, exchange: String, key: String)
  def declareExchange(exchange: String)

  def simpleSubscription(): Subscription[Array[Byte]]
}
