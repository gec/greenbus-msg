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
package io.greenbus.msg.japi.amqp.impl

import io.greenbus.msg.amqp.AmqpServiceOperations
import io.greenbus.msg.japi
import io.greenbus.msg.japi.service.ServiceHandler
import io.greenbus.msg.japi.SubscriptionBinding
import io.greenbus.msg.japi.service.impl.ServiceHandlerShim
import io.greenbus.msg.japi.impl.SubscriptionBindingShim

class AmqpServiceOperationsShim(amqp: AmqpServiceOperations) extends japi.amqp.AmqpServiceOperations {

  def publishEvent(exchange: String, message: Array[Byte], key: String) {
    amqp.publishEvent(exchange, message, key)
  }

  def bindCompetingService(handler: ServiceHandler, exchange: String): SubscriptionBinding = {
    val binding = amqp.bindCompetingService(new ServiceHandlerShim(handler), exchange)
    new SubscriptionBindingShim(binding)
  }

  def bindServiceToQueue(handler: ServiceHandler): SubscriptionBinding = {
    val binding = amqp.bindRoutedService(new ServiceHandlerShim(handler))
    new SubscriptionBindingShim(binding)
  }

  def bindQueue(queue: String, exchange: String, key: String) {
    amqp.bindQueue(queue, exchange, key)
  }

  def declareExchange(exchange: String) {
    amqp.declareExchange(exchange)
  }
}
