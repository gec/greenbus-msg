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


import io.greenbus.msg.amqp.util.PropertyLoading._

case class AmqpSettings(host: String, port: Int, vhost: String, user: String, password: String, heartBeatTimeSeconds: Int, ttlMilliseconds: Int, ssl: Option[AmqpSslSettings]) {
  override def toString = {
    val postfix = ssl.map(_ => "s").getOrElse("")
    s"amqp$postfix://$user@$host:$port/$vhost"
  }
}

case class AmqpSslSettings(trustStore: String, trustStorePassword: String, keyStore: Option[String], keyStorePassword: Option[String])

object AmqpSettings {

  def load(file: String): AmqpSettings = {
    apply(loadFile(file))
  }

  def apply(props: Map[String, String]): AmqpSettings = {

    val sslSettings = if(getBoolean(props, "io.greenbus.msg.amqp.ssl")) {
      Some(AmqpSslSettings(
        get(props, "io.greenbus.msg.amqp.trustStore"),
        get(props, "io.greenbus.msg.amqp.trustStorePassword"),
        optional(props, "io.greenbus.msg.amqp.keyStore"),
        optional(props, "io.greenbus.msg.amqp.keyStorePassword")
      ))
    } else {
      None
    }

    AmqpSettings(
      get(props, "io.greenbus.msg.amqp.host"),
      getInt(props, "io.greenbus.msg.amqp.port"),
      get(props, "io.greenbus.msg.amqp.virtualHost"),
      get(props, "io.greenbus.msg.amqp.user"),
      get(props, "io.greenbus.msg.amqp.password"),
      getInt(props, "io.greenbus.msg.amqp.heartbeatTimeSeconds"),
      5000,
      sslSettings)
  }
}
