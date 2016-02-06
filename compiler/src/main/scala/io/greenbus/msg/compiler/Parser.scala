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
package io.greenbus.msg.compiler

import com.google.protobuf.DescriptorProtos.{DescriptorProto, FileDescriptorProto}
import io.greenbus.msg.compiler.proto.CompilerExtensions
import scala.collection.JavaConversions._

case class TargetInfo(javaInfo: JavaFileInfo)

case class JavaFileInfo(javaPackage: String, javaClassName: String) {
  def fullName: String = javaPackage + "." + javaClassName
}

case class ServiceOptions(scalaPackage: Option[String], javaPackage: Option[String])

case class ProtoContext(protoPackage: String, fileName: String, targetInfo: TargetInfo)

case class ParseResult(messages: Map[String, Message], services: Seq[Service])

case class ServiceTemp(name: String, methods: Seq[MethodTemp], options: ServiceOptions, context: ProtoContext)
case class Service(name: String, methods: Seq[Method], options: ServiceOptions, context: ProtoContext)

case class MethodTemp(name: String, inputType: String, outputType: String, subscription: Option[String], addressing: CompilerExtensions.ServiceAddressing)
case class Method(name: String, inputType: Message, outputType: Message, subscription: Option[Message], addressing: CompilerExtensions.ServiceAddressing)

case class Message(protoName: String, descriptor: DescriptorProto, context: ProtoContext) {
  def fullName: String =  context.protoPackage + "." + protoName
}

object Parser {
  def parseFiles(protoFiles: Seq[FileDescriptorProto]): ParseResult = {
    val results = protoFiles.map(messagesInFile)
    val messages = results.flatMap(_._1)
    val tempServices = results.flatMap(_._2)

    val messageMap = messages.map(msg => (msg.fullName, msg)).toMap

    val services = tempServices.map { tempServ =>
      val methods = tempServ.methods.map { tempMeth =>
        val inputMsg = messageMap.get(tempMeth.inputType).getOrElse {
          throw new Exception("Input " + tempMeth.inputType + " not defined in " + tempServ.name + "." + tempMeth.name)
        }
        val outputMsg = messageMap.get(tempMeth.outputType).getOrElse {
          throw new Exception("Output " + tempMeth.outputType + " not defined in " + tempServ.name + "." + tempMeth.name)
        }
        val subMsg = tempMeth.subscription.map { name =>
          messageMap.get(name).getOrElse {
            throw new Exception("Subscription type " + name + " not defined in " + tempServ.name + "." + tempMeth.name)
          }
        }

        Method(tempMeth.name, inputMsg, outputMsg, subMsg, tempMeth.addressing)
      }
      Service(tempServ.name, methods, tempServ.options, tempServ.context)
    }

    ParseResult(messageMap, services)
  }

  def messagesInFile(fileDescriptor: FileDescriptorProto): (Seq[Message], Seq[ServiceTemp]) = {
    val protoPackage = if (fileDescriptor.hasPackage) {
      fileDescriptor.getPackage
    } else {
      ""
    }

    val fileName = fileDescriptor.getName

    val javaPackage = fileDescriptor.getOptions.getJavaPackage
    val javaClassName = fileDescriptor.getOptions.getJavaOuterClassname

    val javaInfo = JavaFileInfo(javaPackage, javaClassName)

    val targetInfo = TargetInfo(javaInfo)

    val context = ProtoContext(protoPackage, fileName, targetInfo)

    val messages = fileDescriptor.getMessageTypeList.toSeq.map { desc =>
      Message(desc.getName, desc, context)
    }

    val services = fileDescriptor.getServiceList.toSeq.map { serviceDesc =>
      val name = serviceDesc.getName
      val methods = serviceDesc.getMethodList.toSeq.map { methodDesc =>
        val name = methodDesc.getName

        // protobuf prefaces message types with "."
        val inputType = methodDesc.getInputType.drop(1)
        val outputType = methodDesc.getOutputType.drop(1)


        val subscription: Option[String] = if (methodDesc.getOptions.hasExtension(CompilerExtensions.subscriptionType)) {
          Some(methodDesc.getOptions.getExtension(CompilerExtensions.subscriptionType))
        } else {
          None
        }

        val addressing = if (methodDesc.getOptions.hasExtension(CompilerExtensions.addressed)) {
          methodDesc.getOptions.getExtension(CompilerExtensions.addressed)
        } else {
          CompilerExtensions.ServiceAddressing.NEVER
        }

        // get extensions
        MethodTemp(name, inputType, outputType, subscription, addressing)
      }

      val javaPackage = if (serviceDesc.getOptions.hasExtension(CompilerExtensions.javaPackage)) {
        Some(serviceDesc.getOptions.getExtension(CompilerExtensions.javaPackage))
      } else {
        None
      }

      val scalaPackage = if (serviceDesc.getOptions.hasExtension(CompilerExtensions.scalaPackage)) {
        Some(serviceDesc.getOptions.getExtension(CompilerExtensions.scalaPackage))
      } else {
        None
      }

      val options = ServiceOptions(scalaPackage, javaPackage)

      ServiceTemp(name, methods, options, context)
    }

    (messages, services)
  }

}
