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
package io.greenbus.msg.compiler.jvm

import io.greenbus.msg.compiler._
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import io.greenbus.msg.compiler.Message

import CodeWriter._

object ScalaGenerator extends JvmGenerator {

  def packageName(service: Service): String = {
    service.options.scalaPackage match {
      case Some(pkg) => pkg
      case None => service.context.targetInfo.javaInfo.javaPackage + ".service"
    }
  }

  def tab: String = "  "
  def newLine: String = "\n"
  def fileExt: String = "scala"

  def collectionOf(typ: String): String = s"Seq[$typ]"

  def futureOf(typ: String): String = s"Future[$typ]"

  def argument(typ: String, name: String): String = s"$name: $typ"

  def boolType: String = "Boolean"

  def langType(field: FieldDescriptorProto, map: Map[String, Message]): String = {
    import FieldDescriptorProto.Type._

    val typ = field.getType

    typ match {
      case TYPE_BOOL => "Boolean"
      case TYPE_BYTES => "Array[Byte]"
      case TYPE_DOUBLE => "Double"
      case TYPE_INT32 => "Int"
      case TYPE_INT64 => "Long"
      case TYPE_STRING => "String"
      case TYPE_MESSAGE => {
        map.get(field.getTypeName.drop(1)) match {
          case None => throw new Exception("Couldn't find message type for: " + field.getTypeName)
          case Some(msg) => msg.context.targetInfo.javaInfo.fullName + "." + msg.protoName
        }
      }
      case other => throw new Exception("Unsupported proto type: " + typ)
    }
  }

  def renderServiceFile(servicePackage: String, serviceName: String, signatures: Seq[String], definitions: Seq[CodeWriter => Unit], descriptors: Seq[CodeWriter => Unit]): CodeWriter => Unit = {
    { implicit w: CodeWriter =>
      0 << s"package $servicePackage"
      0 << ""

      0 << "import scala.concurrent.Future"
      0 << "import io.greenbus.msg.{Subscription, SessionMessaging, RequestDescriptor}"
      0 << "import scala.collection.JavaConversions._"
      0 << "import scala.concurrent.ExecutionContext.Implicits.global"
      0 << ""

      0 << s"trait $serviceName {"
      0 << ""
      signatures.foreach(1 << _)
      0 << ""
      0 <<  "}"
      0 << ""

      0 << s"object $serviceName {"
      0 << ""
      1 << s"def client(session: SessionMessaging): $serviceName = new Default$serviceName(session)"
      0 << ""
      1 << s"private class Default$serviceName(session: SessionMessaging) extends $serviceName {"
      0 << ""
      definitions.foreach { procedure =>
        procedure(w.indented(2))
        0 << ""
      }
      0 << ""
      1 <<  "}"

      0 << ""
      1 << "object Descriptors {"
      descriptors.foreach { f => f(w.indented(2))}
      1 << "}"
      0 << ""
      0 <<  "}"
    }
  }

  def renderRequestDescriptor(methodName: String, requestId: String, requestType: String, responseType: String): CodeWriter => Unit = {
    { implicit w: CodeWriter =>
      0 << s"object ${methodName.capitalize} extends RequestDescriptor[$requestType, $responseType] {"
      1 << s"def requestId: String = $requestId"
      1 << s"def decodeRequest(bytes: Array[Byte]): $requestType = $requestType.parseFrom(bytes)"
      1 << s"def encodeResponse(response: $responseType): Array[Byte] = response.toByteArray"
      0 <<  "}"
    }
  }

  def renderRequestSignature(methodName: String, argList: Seq[String], addressed: Boolean, headers: Boolean, retType: String): String = {
    val addressArg = if (addressed) Seq("destination: String") else Seq()
    val headersArg = if (headers) Seq("headers: Map[String, String]") else Seq()

    val args = (argList ++ addressArg ++ headersArg).mkString(", ")

    s"def $methodName($args): Future[$retType]"
  }

  def renderRequest(requestId: String, signature: String, buildProcedure: CodeWriter => Unit, extractProcedure: CodeWriter => Unit, addressed: Boolean, headers: Boolean, retType: String): CodeWriter => Unit = {

    val addressRendered = if (addressed) "Some(destination)" else "None"
    val headerRendered = if (headers) "headers" else "Map()"

    val writeRequest = { implicit w: CodeWriter =>
      0 << signature + " = {"
      buildProcedure(w.indented(1))
      1 << s"val future = session.request($requestId, $headerRendered, $addressRendered, requestBytes)"
      1 << "future.map { responseBytes =>"
      extractProcedure(w.indented(2))
      2 << "response"
      1 << "}"
      0 << "}"
    }

    writeRequest
  }

  def renderSubscriptionSignature(methodName: String, argList: Seq[String], addressed: Boolean, headers: Boolean, retType: String, eventType: String): String = {
    val addressArg = if (addressed) Seq("destination: String") else Seq()
    val headersArg = if (headers) Seq("headers: Map[String, String]") else Seq()

    val args = (argList ++ addressArg ++ headersArg).mkString(", ")

    s"def $methodName($args): Future[($retType, Subscription[$eventType])]"
  }

  def renderSubscription(requestId: String, signature: String, buildProcedure: CodeWriter => Unit, extractProcedure: CodeWriter => Unit, addressed: Boolean, headers: Boolean, retType: String, eventType: String): CodeWriter => Unit = {
    val addressRendered = if (addressed) "Some(destination)" else "None"
    val headerRendered = if (headers) "headers" else "Map()"

    val writeRequest = { implicit w: CodeWriter =>
      0 << signature + " = {"
      buildProcedure(w.indented(1))
      1 << s"val future = session.subscribe($requestId, $headerRendered, $addressRendered, requestBytes)"
      1 << "future.map { case (responseBytes, subscription) =>"
      2 << "try {"
      extractProcedure(w.indented(3))
      3 << s"val mappedSubscription = new Subscription[$eventType] {"
      4 <<  "def cancel() { subscription.cancel() }"
      4 <<  "def getId(): String = subscription.getId()"
      4 << s"def start(handler: ($eventType) => Unit) {"
      5 <<  "subscription.start { bytes =>"
      6 << s"val event = $eventType.parseFrom(bytes)"
      6 <<  "handler(event)"
      5 <<  "}"
      4 <<  "}"
      3 <<  "}"
      3 << "(response, mappedSubscription)"
      2 << "} catch {"
      3 << "case ex: Throwable =>"
      4 << "subscription.cancel()"
      4 << "throw ex"
      2 << "}"
      1 << "}"
      0 << "}"
    }

    writeRequest
  }

  def renderBuildProcedure(wrapType: String, setPrefix: String, fieldName: String): CodeWriter => Unit = {
    { implicit w: CodeWriter =>
      0 << s"val reqWrapperBuilder = $wrapType.newBuilder"
      0 << s"reqWrapperBuilder.${setPrefix}${fieldName.capitalize}($fieldName)"
      0 <<  "val requestBytes = reqWrapperBuilder.build.toByteArray"
    }
  }

  def renderNoArgsBuildProcedure(wrapType: String): (CodeWriter) => Unit = {
    implicit w: CodeWriter => 0 << s"val requestBytes = $wrapType.newBuilder.build.toByteArray"
  }

  def renderMultiArgsBuildProcedure(wrapType: String): (CodeWriter) => Unit = {
    implicit w: CodeWriter => 0 << "val requestBytes = request.toByteArray"
  }

  def renderExtractProcedure(wrapType: String, responseType: String, isCollection: Boolean, fieldName: String): CodeWriter => Unit = {
    { implicit w: CodeWriter =>
      0 << s"val respWrapper = $wrapType.parseFrom(responseBytes)"
      isCollection match {
        case true => 0 << s"val response = respWrapper.get${fieldName.capitalize}List.toSeq"
        case false => 0 << s"val response = respWrapper.get${fieldName.capitalize}"
      }
    }
  }

  def renderVoidExtractProcedure(wrapType: String): (CodeWriter) => Unit = {
    implicit w: CodeWriter => 0 << "val response = true"
  }

  def renderMultiExtractProcedure(wrapType: String): (CodeWriter) => Unit = {
    implicit w: CodeWriter => 0 << s"val response = $wrapType.parseFrom(responseBytes)"
  }

}
