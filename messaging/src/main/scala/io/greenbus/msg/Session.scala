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
package io.greenbus.msg

import scala.concurrent.Future

case class SubscriptionResult[A, B](result: A, subscription: Subscription[B])

trait SessionMessaging {

  def request(requestId: String, headers: Map[String, String], destination: Option[String], payload: Array[Byte]): Future[Array[Byte]]
  def subscribe(requestId: String, headers: Map[String, String], destination: Option[String], payload: Array[Byte]): Future[(Array[Byte], Subscription[Array[Byte]])]
}

trait Session extends SessionMessaging {

  def headers: Map[String, String]
  def addHeader(key: String, value: String)
  def removeHeader(key: String, value: String)
  def addHeaders(headers: Seq[(String, String)])
  def clearHeaders()

  /*
  request listeners
  subscription listeners
   */

  def spawn(): Session
}


object Session {

  def withMessaging(messaging: SessionMessaging): Session = new DelegatingSession(messaging)

  private class DelegatingSession(messaging: SessionMessaging, hdrs: Map[String, String] = Map.empty[String, String]) extends Session {
    private var map = hdrs

    def request(requestId: String, headers: Map[String, String], destination: Option[String], payload: Array[Byte]): Future[Array[Byte]] = {
      messaging.request(requestId, this.headers ++ headers, destination: Option[String], payload)
    }

    def subscribe(requestId: String, headers: Map[String, String], destination: Option[String], payload: Array[Byte]): Future[(Array[Byte], Subscription[Array[Byte]])] = {
      messaging.subscribe(requestId, this.headers ++ headers, destination: Option[String], payload)
    }

    def spawn(): Session = new DelegatingSession(messaging, headers)

    def headers: Map[String, String] = map

    def addHeader(key: String, value: String) {
      map += (key -> value)
    }

    def removeHeader(key: String, value: String) {
      map -= key
    }

    def addHeaders(headers: Seq[(String, String)]) {
      map = map ++ headers
    }
    def clearHeaders()   {
      map = Map.empty[String, String]
    }
  }
}