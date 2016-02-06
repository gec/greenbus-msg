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
package io.greenbus.msg.japi.impl

import io.greenbus.msg.{japi, Subscription, Session}
import java.util
import scala.collection.JavaConversions._
import io.greenbus.msg.japi.{SubscriptionHandler, SubscriptionResult}
import scala.concurrent.ExecutionContext.Implicits.global
import com.google.common.util.concurrent.{AbstractFuture, ListenableFuture}
import scala.util.{Success, Failure}

class SessionShim(session: Session) extends japi.Session {
  import SessionShim._

  def spawn(): japi.Session = new SessionShim(session.spawn())


  def addHeader(key: String, value: String) {
    session.addHeader(key, value)
  }

  def addHeaders(headers: util.Map[String, String]) {
    session.addHeaders(headers.toSeq)
  }

  def removeHeader(key: String, value: String) {
    session.removeHeader(key, value)
  }

  def clearHeaders() {
    session.clearHeaders()
  }

  def request(requestId: String, headers: util.Map[String, String], destination: String, payload: Array[Byte]): ListenableFuture[Array[Byte]] = {
    val hdrs = Option(headers).map(_.toMap).getOrElse(Map())
    val dest = Option(destination)

    val future = session.request(requestId, hdrs, dest, payload)
    new FutureShim(future)
  }

  def subscribe(requestId: String, headers: util.Map[String, String], destination: String, payload: Array[Byte]): ListenableFuture[SubscriptionResult[Array[Byte], Array[Byte]]] = {
    val hdrs = Option(headers).map(_.toMap).getOrElse(Map())
    val dest = Option(destination)

    val future = session.subscribe(requestId, hdrs, dest, payload)
    val mappedFuture = future.map {
      case (bytes, sub) => new SubscriptionResult(bytes, new SubscriptionShim(sub))
    }
    new FutureShim(mappedFuture)
  }
}

object SessionShim {

  private class FutureShim[A](future: scala.concurrent.Future[A]) extends AbstractFuture[A] {
    future.onComplete {
      case Failure(ex) => this.setException(ex)
      case Success(result) => this.set(result)
    }
  }

  private class SubscriptionShim[A](sub: Subscription[A]) extends japi.Subscription[A] {
    def cancel() {
      sub.cancel()
    }

    def getId: String =  sub.getId()


    def start(acceptor: SubscriptionHandler[A]) = {
      sub.start(obj => acceptor.handle(obj))
    }
  }

}
