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

import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location
import com.google.protobuf.DescriptorProtos._
import io.greenbus.msg.compiler.proto.CompilerExtensions
import scala.collection.JavaConversions._

case class TargetInfo(javaInfo: JavaFileInfo)

case class JavaFileInfo(javaPackage: String, javaClassName: String) {
  def fullName: String = javaPackage + "." + javaClassName
}

case class ServiceOptions(scalaPackage: Option[String], javaPackage: Option[String])

case class ProtoContext(protoPackage: String, fileName: String, targetInfo: TargetInfo)

case class ParseResult(messages: Seq[Message], enums: Seq[Enum], services: Seq[Service])

case class ServiceTemp(name: String, methods: Seq[MethodTemp], options: ServiceOptions, doc: DocInfo, context: ProtoContext)

case class Service(name: String, methods: Seq[Method], options: ServiceOptions, doc: DocInfo, context: ProtoContext) {
  def fullName: String =  context.protoPackage + "." + name
}

case class MethodTemp(name: String, inputType: String, outputType: String, subscription: Option[String], addressing: CompilerExtensions.ServiceAddressing, docInfo: DocInfo)

case class Method(name: String, inputType: Message, outputType: Message, subscription: Option[Message], addressing: CompilerExtensions.ServiceAddressing, docInfo: DocInfo)

case class Field(name: String, label: FieldDescriptorProto.Label, number: Int, fieldType: FieldDescriptorProto.Type, fieldTypeName: Option[String], docInfo: DocInfo)

case class Message(name: String, fields: Seq[Field], doc: DocInfo, context: ProtoContext) {
  def fullName: String =  context.protoPackage + "." + name
}

case class EnumField(name: String, number: Int, doc: DocInfo)

case class Enum(name: String, fields: Seq[EnumField], doc: DocInfo, context: ProtoContext) {
  def fullName: String =  context.protoPackage + "." + name
}

case class DocInfo(leadingOpt: Option[String], trailingOpt: Option[String])

object Parser {

  def parseFiles(protoFiles: Seq[FileDescriptorProto]): ParseResult = {
    val results = protoFiles.map(fileParts)
    val messages = results.flatMap(_._1)
    val enums = results.flatMap(_._2)
    val tempServices = results.flatMap(_._3)

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

        Method(tempMeth.name, inputMsg, outputMsg, subMsg, tempMeth.addressing, tempMeth.docInfo)
      }
      Service(tempServ.name, methods, tempServ.options, tempServ.doc, tempServ.context)
    }

    ParseResult(messages, enums, services)
  }

  def docInfo(location: Location): DocInfo = {
    val leadOpt = if(location.hasLeadingComments) Some(location.getLeadingComments) else None
    val trailOpt = if(location.hasTrailingComments) Some(location.getTrailingComments) else None

    DocInfo(leadOpt, trailOpt)
  }

  def enum(desc: EnumDescriptorProto, loc: LocEnumNode, parents: List[String], context: ProtoContext): Enum = {

    val name = if (parents.nonEmpty) {
      parents.reverse.mkString(".") + "." + desc.getName
    }  else {
      desc.getName
    }

    val fields = desc.getValueList.zip(loc.fields).map {
      case (enumDesc, l) => EnumField(enumDesc.getName, enumDesc.getNumber, docInfo(l))
    }
    Enum(name, fields, docInfo(loc.location), context)
  }

  def messageField(desc: FieldDescriptorProto, location: Location): Field = {
    val name = desc.getName
    val label = desc.getLabel
    val number = desc.getNumber
    val fieldType = desc.getType
    val fieldTypeName = if (desc.hasTypeName) Some(desc.getTypeName.drop(1)) else None

    Field(name, label, number, fieldType, fieldTypeName, docInfo(location))
  }

  def messageContent(desc: DescriptorProto, loc: LocMessageNode, parents: List[String], context: ProtoContext): Message = {

    val name = if (parents.nonEmpty) {
      parents.reverse.mkString(".") + "." + desc.getName
    }  else {
      desc.getName
    }

    val fields = desc.getFieldList
      .zip(loc.fields)
      .map { case (d, l) => messageField(d, l) }

    Message(name, fields, docInfo(loc.location), context)
  }

  def message(desc: DescriptorProto, loc: LocMessageNode, parents: List[String], context: ProtoContext): (Seq[Message], Seq[Enum]) = {

    val here = messageContent(desc, loc, parents, context)

    val ownedEnums = desc.getEnumTypeList
      .zip(loc.enums)
      .map { case (enumDesc, locNode) => enum(enumDesc, locNode, desc.getName :: parents, context) }

    val nestedResults: Seq[(Seq[Message], Seq[Enum])] = desc.getNestedTypeList
      .zip(loc.subMessages)
      .map { case (msgDesc, locNode) => message(msgDesc, locNode, desc.getName :: parents, context) }

    val nestedMessages = nestedResults.flatMap(_._1)
    val nestedEnums = nestedResults.flatMap(_._2)

    (here +: nestedMessages, ownedEnums ++ nestedEnums)
  }

  def method(methodDesc: MethodDescriptorProto, loc: Location): MethodTemp = {
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
    MethodTemp(name, inputType, outputType, subscription, addressing, docInfo(loc))
  }

  def service(desc: ServiceDescriptorProto, loc: LocServiceNode, context: ProtoContext): ServiceTemp = {
    val name = desc.getName

    val methods = desc.getMethodList.toSeq
      .zip(loc.methods)
      .map { case (methDesc, locNode) => method(methDesc, locNode) }

    val javaPackage = if (desc.getOptions.hasExtension(CompilerExtensions.javaPackage)) {
      Some(desc.getOptions.getExtension(CompilerExtensions.javaPackage))
    } else {
      None
    }

    val scalaPackage = if (desc.getOptions.hasExtension(CompilerExtensions.scalaPackage)) {
      Some(desc.getOptions.getExtension(CompilerExtensions.scalaPackage))
    } else {
      None
    }

    val options = ServiceOptions(scalaPackage, javaPackage)

    ServiceTemp(name, methods, options, docInfo(loc.location), context)
  }

  def fileParts(fileDescriptor: FileDescriptorProto): (Seq[Message], Seq[Enum], Seq[ServiceTemp]) = {
    val protoPackage = if (fileDescriptor.hasPackage) {
      fileDescriptor.getPackage
    } else {
      ""
    }

    val locationInfo = pathTrie(fileDescriptor.getSourceCodeInfo.getLocationList.toSeq)

    val fileName = fileDescriptor.getName

    val javaPackage = fileDescriptor.getOptions.getJavaPackage
    val javaClassName = fileDescriptor.getOptions.getJavaOuterClassname

    val javaInfo = JavaFileInfo(javaPackage, javaClassName)

    val targetInfo = TargetInfo(javaInfo)

    val context = ProtoContext(protoPackage, fileName, targetInfo)

    val messageResults = fileDescriptor.getMessageTypeList.toSeq
      .zip(locationInfo.messages)
      .map { case (msgDesc, locNode) => message(msgDesc, locNode, Nil, context) }

    val enums = fileDescriptor.getEnumTypeList.toSeq
      .zip(locationInfo.enums)
      .map { case (enumDesc, locNode) => enum(enumDesc, locNode, Nil, context) }

    val services = fileDescriptor.getServiceList.toSeq
      .zip(locationInfo.services)
      .map { case (servDesc, locNode) => service(servDesc, locNode, context) }

    (messageResults.flatMap(_._1), enums ++ messageResults.flatMap(_._2), services)
  }


  case class LocEnumNode(location: Location, fields: Seq[Location])

  case class LocMessageNode(location: Location, fields: Seq[Location], subMessages: Seq[LocMessageNode], enums: Seq[LocEnumNode])

  case class LocServiceNode(location: Location, methods: Seq[Location])

  case class LocFileNode(messages: Seq[LocMessageNode], enums: Seq[LocEnumNode], services: Seq[LocServiceNode])


  def pathTrie(locations: Seq[Location]): LocFileNode = {

    def enumTrie(locsForEnum: Seq[Location], offset: Int): LocEnumNode = {

      val locForDefinition = locsForEnum.find(_.getPathCount == offset).getOrElse {
        throw new IllegalArgumentException(s"Expected enum definition (offset: $offset)")
      }

      val subLocsGrouped: Map[Int, Seq[Location]] = locsForEnum.filter(_.getPathCount > (offset + 1)).groupBy(_.getPath(offset))

      val fields = subLocsGrouped.getOrElse(2, Vector.empty[Location]).filter(_.getPathCount == offset + 2).sortBy(_.getPath(offset + 1))

      LocEnumNode(locForDefinition, fields)
    }

    def msgTrie(locsForMessage: Seq[Location], offset: Int): LocMessageNode = {

      val locForDefinition = locsForMessage.find(_.getPathCount == offset).getOrElse {
        throw new IllegalArgumentException(s"Expected message definition (offset: $offset)")
      }

      /*
        DescriptorProto

        repeated FieldDescriptorProto field = 2;
        repeated DescriptorProto nested_type = 3;
        repeated EnumDescriptorProto enum_type = 4;
       */

      val subLocsGrouped: Map[Int, Seq[Location]] = locsForMessage.filter(_.getPathCount > (offset + 1)).groupBy(_.getPath(offset))

      val fields = subLocsGrouped.getOrElse(2, Vector.empty[Location]).filter(_.getPathCount == offset + 2).sortBy(_.getPath(offset + 1))

      val enumLocs: Map[Int, Seq[Location]] = subLocsGrouped.getOrElse(4, Vector.empty[Location]).groupBy(_.getPath(offset + 1))

      val enums: Seq[LocEnumNode] = enumLocs.mapValues(enumTrie(_, offset + 2)).toSeq
        .sortBy(_._1)
        .map(_._2)

      val nestedMessageLocs: Map[Int, Seq[Location]] = subLocsGrouped.getOrElse(3, Vector.empty[Location]).groupBy(_.getPath(offset + 1))

      val nested: Seq[LocMessageNode] = nestedMessageLocs.mapValues(msgTrie(_, offset + 2)).toSeq
        .sortBy(_._1)
        .map(_._2)

      LocMessageNode(locForDefinition, fields, nested, enums)
    }

    def serviceTrie(locsForService: Seq[Location]): LocServiceNode = {

      val locForDefinition = locsForService.find(_.getPathCount == 2).getOrElse {
        throw new IllegalArgumentException(s"Expected service definition")
      }

      /*
        ServiceDescriptorProto

        repeated MethodDescriptorProto method = 2;
       */

      val subLocsGrouped: Map[Int, Seq[Location]] = locsForService.filter(_.getPathCount > 3).groupBy(_.getPath(2))

      val methods = subLocsGrouped.getOrElse(2, Vector.empty[Location]).filter(_.getPathCount == 4).sortBy(_.getPath(3))

      LocServiceNode(locForDefinition, methods)
    }

    /*
      FileDescriptorProto

      repeated DescriptorProto message_type = 4;
      repeated EnumDescriptorProto enum_type = 5;
      repeated ServiceDescriptorProto service = 6;
      repeated FieldDescriptorProto extension = 7;
    */

    val grouped: Map[Int, Seq[Location]] = locations.filter(_.getPathCount > 1).groupBy(_.getPath(0))

    val baseMsgByIndex: Map[Int, Seq[Location]] = grouped.getOrElse(4, Vector.empty[Location]).groupBy(_.getPath(1))

    val messages = baseMsgByIndex.mapValues(msgTrie(_, 2)).toSeq
      .sortBy(_._1)
      .map(_._2)

    val enumLocs: Map[Int, Seq[Location]] = grouped.getOrElse(5, Vector.empty[Location]).groupBy(_.getPath(1))

    val enums = enumLocs.mapValues(enumTrie(_, 2)).toSeq
      .sortBy(_._1)
      .map(_._2)

    val serviceLocs: Map[Int, Seq[Location]] = grouped.getOrElse(6, Vector.empty[Location]).groupBy(_.getPath(1))

    val services = serviceLocs.mapValues(serviceTrie).toSeq
      .sortBy(_._1)
      .map(_._2)

    LocFileNode(messages, enums, services)
  }

}
