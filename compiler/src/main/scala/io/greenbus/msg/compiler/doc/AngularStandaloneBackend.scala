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
package io.greenbus.msg.compiler.doc

import java.io.StringWriter

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.File
import org.apache.commons.io.IOUtils
import io.greenbus.msg.compiler.doc.JsonBackend.{MessageApiData, MessageJson}
import play.api.libs.json.Json

object AngularStandaloneBackend {

  def output(messages: Seq[Message], enums: Seq[Enum], services: Seq[Service], context: JsonBackend.Context): Seq[File] = {

    val messageTemplate = loadClassPathFile("templates/message.html")
    val enumTemplate = loadClassPathFile("templates/enum.html")
    val serviceTemplate = loadClassPathFile("templates/service.html")
    val navbarTemplate = loadClassPathFile("templates/navbar.html")
    val summaryTemplate = loadClassPathFile("templates/summary.html")

    val crossReferences = CrossReferences.findReferences(messages, enums, services)

    def msgToFile(msg: MessageApiData): File = {
      val json = Json.toJson(msg).toString()
      fillTemplate(msg.message.fullName, json, context, messageTemplate)
    }
    def enumToFile(enum: Enum): File = {
      val json = Json.toJson(JsonBackend.enumJson(enum, context, crossReferences)).toString()
      fillTemplate(enum.fullName, json, context, enumTemplate)
    }
    def serviceToFile(service: Service, messageMap: Map[String, MessageJson]): File = {
      val json = Json.toJson(JsonBackend.serviceJson(service, context, messageMap)).toString()
      fillTemplate(service.fullName, json, context, serviceTemplate)
    }

    val navbarJson = Json.toJson(JsonBackend.navbarJson(messages, enums, services)).toString()

    val navbar = fillTemplate("navbar", navbarJson, context, navbarTemplate)

    val summary = fillTemplate("summary", navbarJson, context, summaryTemplate)

    val jsonMessageApis = messages.map(JsonBackend.messageJson(_, context, crossReferences))

    val messageMap = jsonMessageApis.map(m => (m.message.fullName, m.message)).toMap

    Seq(navbar, summary) ++
      jsonMessageApis.map(msgToFile) ++
      enums.map(enumToFile) ++
      services.map(serviceToFile(_, messageMap))
  }

  def fillTemplate(name: String, json: String, context: JsonBackend.Context, template: String): File = {
    val filename = name + ".html"

    val fileContent = template.replace("@@INSERT_JSON@@", json)

    File.newBuilder()
      .setName(filename)
      .setContent(fileContent)
      .build()
  }

  def loadClassPathFile(file: String): String = {
    val input = this.getClass.getResourceAsStream(file)
    val w = new StringWriter()
    IOUtils.copy(input, w)
    w.toString
  }
}
