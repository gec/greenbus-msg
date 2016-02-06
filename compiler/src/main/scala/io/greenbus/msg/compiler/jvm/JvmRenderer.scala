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
import io.greenbus.msg.compiler.Message
import io.greenbus.msg.compiler.Method
import io.greenbus.msg.compiler.Service
import io.greenbus.msg.compiler.ParseResult
import io.greenbus.msg.compiler.proto.CompilerExtensions
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.File

trait JvmRenderer {

  def packageName(service: Service): String

  def tab: String
  def newLine: String
  def fileExt: String

  def collectionOf(typ: String): String
  def futureOf(typ: String): String
  def argument(typ: String, name: String): String
  def boolType: String

  def langType(field: FieldDescriptorProto, map: Map[String, Message]): String

  def renderServiceFile(servicePackage: String, serviceName: String, signatures: Seq[String], definitions: Seq[CodeWriter => Unit], descriptors: Seq[CodeWriter => Unit]): CodeWriter => Unit

  def renderRequestDescriptor(methodName: String, requestId: String, requestType: String, responseType: String): CodeWriter => Unit
  def renderRequestSignature(methodName: String, argList: Seq[String], addressed: Boolean, headers: Boolean, retType: String): String
  def renderRequest(requestId: String, signature: String, buildProcedure: CodeWriter => Unit, extractProcedure: CodeWriter => Unit, addressed: Boolean, headers: Boolean, retType: String): CodeWriter => Unit
  def renderSubscriptionSignature(methodName: String, argList: Seq[String], addressed: Boolean, headers: Boolean, retType: String, eventType: String): String
  def renderSubscription(requestId: String, signature: String, buildProcedure: CodeWriter => Unit, extractProcedure: CodeWriter => Unit, addressed: Boolean, headers: Boolean, retType: String, eventType: String): CodeWriter => Unit

  def renderBuildProcedure(wrapType: String, setPrefix: String, fieldName: String): CodeWriter => Unit
  def renderNoArgsBuildProcedure(wrapType: String): CodeWriter => Unit
  def renderMultiArgsBuildProcedure(wrapType: String): CodeWriter => Unit

  def renderExtractProcedure(wrapType: String, responseType: String, isCollection: Boolean, fieldName: String): CodeWriter => Unit
  def renderVoidExtractProcedure(wrapType: String): CodeWriter => Unit
  def renderMultiExtractProcedure(wrapType: String): CodeWriter => Unit

}


trait JvmGenerator extends JvmRenderer {
  import Util._

  def generate(parse: ParseResult): Seq[File] = {
    val ParseResult(map, services) = parse

    services.map(serv => generateService(map, serv))
  }

  def generateService(map: Map[String, Message], service: Service): File = {
    val javaInfo = service.context.targetInfo.javaInfo

    val servicePackage = packageName(service)

    val serviceName = service.name

    val code = serviceCode(map, servicePackage, serviceName, service.methods)

    val fileName = servicePackage.split('.').mkString("/") + "/" + serviceName + "." + fileExt

    File.newBuilder
      .setName(fileName)
      .setContent(code)
      .build()
  }

  def serviceCode(map: Map[String, Message], servicePackage: String, serviceName: String, methods: Seq[Method]): String = {

    val b = new StringBuilder
    val w = CodeWriter(tab, newLine, b)

    val tuples = methods.map(methodCode(map, _))

    val descriptors = tuples.map(_._2)

    val (signatures, definitions) = tuples.flatMap(_._1).unzip

    val procedure = renderServiceFile(servicePackage, serviceName, signatures, definitions, descriptors)
    procedure(w)

    b.toString()
  }

  def methodCode(map: Map[String, Message], method: Method): (Seq[(String, CodeWriter => Unit)], CodeWriter => Unit) = {

    val (argList, buildProcedure) = interpretInput(map, method.inputType)

    val (responseType, extractProcedure) = interpretOutput(map, method.outputType)

    val methodName = toCamelCase(method.name)
    val requestId = "\"" + method.inputType.protoName + "\""

    val descriptor = renderRequestDescriptor(methodName, requestId, protoJvmType(method.inputType), protoJvmType(method.outputType))

    val renderedMethods = method.subscription match {
      case None => {

        def render(addressed: Boolean, headers: Boolean): (String, CodeWriter => Unit) = {
          val signature = renderRequestSignature(methodName, argList, addressed, headers, responseType)
          val code = renderRequest(requestId, signature, buildProcedure, extractProcedure, addressed, headers, responseType)
          (signature, code)
        }

        def renderHeadersPair(addressed: Boolean): Seq[(String, CodeWriter => Unit)] = {
          Seq(render(addressed, false), render(addressed, true))
        }

        method.addressing match {
          case CompilerExtensions.ServiceAddressing.NEVER =>
            renderHeadersPair(false)
          case CompilerExtensions.ServiceAddressing.OPTIONALLY =>
            renderHeadersPair(false) ++ renderHeadersPair(true)
          case CompilerExtensions.ServiceAddressing.ALWAYS =>
            renderHeadersPair(true)
        }
      }
      case Some(eventMsg) => {
        val eventType = eventMsg.context.targetInfo.javaInfo.fullName + "." + eventMsg.protoName

        def render(addressed: Boolean, headers: Boolean): (String, CodeWriter => Unit) = {
          val signature = renderSubscriptionSignature(methodName, argList, addressed, headers, responseType, eventType)
          val code = renderSubscription(requestId, signature, buildProcedure, extractProcedure, addressed, headers, responseType, eventType)
          (signature, code)
        }

        def renderHeadersPair(addressed: Boolean): Seq[(String, CodeWriter => Unit)] = {
          Seq(render(addressed, false), render(addressed, true))
        }

        method.addressing match {
          case CompilerExtensions.ServiceAddressing.NEVER =>
            renderHeadersPair(false)
          case CompilerExtensions.ServiceAddressing.OPTIONALLY =>
            renderHeadersPair(false) ++ renderHeadersPair(true)
          case CompilerExtensions.ServiceAddressing.ALWAYS =>
            renderHeadersPair(true)
        }

      }
    }

    (renderedMethods, descriptor)
  }

  def protoJvmType(msg: Message): String = msg.context.targetInfo.javaInfo.fullName + "." + msg.protoName

  def interpretInput(map: Map[String, Message], input: Message): (Seq[String], CodeWriter => Unit) = {
    val inputDesc = input.descriptor
    val wrapType = protoJvmType(input)

    inputDesc.getFieldList.size() match {
      case 0 => {
        (Seq(), renderNoArgsBuildProcedure(wrapType))
      }
      case 1 => {
        val inputField = inputDesc.getFieldList.get(0)
        val name = toCamelCase(inputField.getName)
        val isCollection = inputField.getLabel == FieldDescriptorProto.Label.LABEL_REPEATED
        val fieldTyp = langType(inputField, map)

        val typ = isCollection match {
          case true => collectionOf(fieldTyp)
          case false => fieldTyp
        }

        val argList = Seq(argument(typ, name))

        val setPrefix = isCollection match {
          case true => "addAll"
          case false => "set"
        }

        val buildProcedure = renderBuildProcedure(wrapType, setPrefix, name)

        (argList, buildProcedure)
      }
      case n => {
        (Seq(argument(wrapType, "request")), renderMultiArgsBuildProcedure(wrapType))
      }
    }
  }


  def interpretOutput(map: Map[String, Message], output: Message): (String, CodeWriter => Unit) = {
    val desc = output.descriptor
    val wrapType = protoJvmType(output)

    desc.getFieldList.size() match {
      case 0 => {
        (boolType, renderVoidExtractProcedure(wrapType))
      }
      case 1 => {
        val field = desc.getFieldList.get(0)
        val name = toCamelCase(field.getName)
        val isCollection = field.getLabel == FieldDescriptorProto.Label.LABEL_REPEATED
        val fieldTyp = langType(field, map)

        val responseType = isCollection match {
          case true => collectionOf(fieldTyp)
          case false => fieldTyp
        }

        val buildProcedure = renderExtractProcedure(wrapType, responseType, isCollection, name)

        (responseType, buildProcedure)
      }
      case n => {
        (futureOf(wrapType), renderMultiExtractProcedure(wrapType))
      }
    }
  }


}

