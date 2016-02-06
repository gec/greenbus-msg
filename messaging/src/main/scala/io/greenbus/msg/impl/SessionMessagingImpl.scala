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
package io.greenbus.msg.impl

import io.greenbus.msg.{RequestMessagingCodec, Subscription, SessionMessaging}
import scala.concurrent.Future
import io.greenbus.msg.driver.MessagingDriver
import scala.util.{Failure, Success}

import scala.concurrent.ExecutionContext.Implicits.global

class SessionMessagingImpl[RequestType, ResponseType](messaging: MessagingDriver, codec: RequestMessagingCodec) extends SessionMessaging {

  def request(requestId: String, headers: Map[String, String], destination: Option[String], payload: Array[Byte]): Future[Array[Byte]] = {
    val bytes = codec.encode(requestId, headers, payload)
    request(requestId, destination, bytes)
  }

  private def request(requestId: String, destination: Option[String], bytes: Array[Byte]): Future[Array[Byte]] = {

    messaging.request(requestId, bytes, destination) flatMap { bytes =>

      codec.decodeAndProcess(bytes) match {
        case Success(payload) => Future.successful(payload)
        case Failure(ex) => Future.failed(ex)
      }
    }
  }

  def subscribe(requestId: String, headers: Map[String, String], destination: Option[String], payload: Array[Byte]): Future[(Array[Byte], Subscription[Array[Byte]])] = {

    val subscription = messaging.createSubscription()

    val requestBytes = codec.encodeSubscription(requestId, headers, payload, subscription.getId())

    val fut = request(requestId, destination, requestBytes)
    fut onFailure {
      case _ => subscription.cancel()
    }

    fut.map(resp => (resp, subscription))
  }
}
