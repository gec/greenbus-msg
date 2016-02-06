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

import com.github.rjeschke.txtmark.{Configuration, Processor}
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import io.greenbus.msg.compiler.proto.CompilerExtensions.ServiceAddressing
import play.api.libs.json.Json

import scala.io.Source


object JsonBackend {

  case class Context(version: String)

  object MethodMention {
    implicit val writer = Json.writes[MethodMention]
    implicit val reader = Json.reads[MethodMention]
  }
  case class MethodMention(service: String, method: String)

  object Mentions {
    implicit val writer = Json.writes[Mentions]
    implicit val reader = Json.reads[Mentions]
  }
  case class Mentions(messageMentions: Option[Seq[String]], serviceParamMentions: Option[Seq[MethodMention]], serviceResultMentions: Option[Seq[MethodMention]])

  object FieldType {
    implicit val writer = Json.writes[FieldType]
    implicit val reader = Json.reads[FieldType]
  }
  case class FieldType(simpleType: Option[String], messageType: Option[String], messagePackage: Option[String], multiplicity: String)

  object MessageField {
    implicit val writer = Json.writes[MessageField]
    implicit val reader = Json.reads[MessageField]
  }
  case class MessageField(name: String, `type`: FieldType, label: String, description: String, requirement: Option[String], nested: Option[Boolean])

  object MessageJson {
    implicit val writer = Json.writes[MessageJson]
    implicit val reader = Json.reads[MessageJson]
  }
  case class MessageJson(name: String, `package`: String, description: String, fields: Seq[MessageField], simple: Boolean) {
    def fullName = `package` + "." + name
  }

  object MessageApiData {
    implicit val writer = Json.writes[MessageApiData]
    implicit val reader = Json.reads[MessageApiData]
  }
  case class MessageApiData(version: String, message: MessageJson, mentions: Mentions)

  object EnumField {
    implicit val writer = Json.writes[EnumField]
    implicit val reader = Json.reads[EnumField]
  }
  case class EnumField(name: String, number: Int, description: String)

  object EnumApiData {
    implicit val writer = Json.writes[EnumApiData]
    implicit val reader = Json.reads[EnumApiData]
  }
  case class EnumApiData(version: String, name: String, `package`: String, description: String, fields: Seq[EnumField], mentions: Mentions)

  object MethodParameter {
    implicit val writer = Json.writes[MethodParameter]
    implicit val reader = Json.reads[MethodParameter]
  }
  case class MethodParameter(name: String, `type`: FieldType, nested: Option[MessageJson])

  object MethodJson {
    implicit val writer = Json.writes[MethodJson]
    implicit val reader = Json.reads[MethodJson]
  }
  case class MethodJson(name: String, parameters: Seq[MethodParameter], returnValue: FieldType, subscription: Option[FieldType], addressing: Option[String], description: String)

  object ServiceApiData {
    implicit val writer = Json.writes[ServiceApiData]
    implicit val reader = Json.reads[ServiceApiData]
  }
  case class ServiceApiData(version: String, name: String, `package`: String, description: String, methods: Seq[MethodJson], nested: Map[String, MessageJson])

  object NavbarService {
    implicit val writer = Json.writes[NavbarService]
    implicit val reader = Json.reads[NavbarService]
  }
  case class NavbarService(name: String, link: String)

  object NavbarServicePackage {
    implicit val writer = Json.writes[NavbarServicePackage]
    implicit val reader = Json.reads[NavbarServicePackage]
  }
  case class NavbarServicePackage(`package`: String, services: Seq[NavbarService])

  object NavbarType {
    implicit val writer = Json.writes[NavbarType]
    implicit val reader = Json.reads[NavbarType]
  }
  case class NavbarType(name: String, link: String, isEnum: Boolean)

  object NavbarTypePackage {
    implicit val writer = Json.writes[NavbarTypePackage]
    implicit val reader = Json.reads[NavbarTypePackage]
  }
  case class NavbarTypePackage(`package`: String, types: Seq[NavbarType])

  object NavbarApiData {
    implicit val writer = Json.writes[NavbarApiData]
    implicit val reader = Json.reads[NavbarApiData]
  }
  case class NavbarApiData(servicePackages: Seq[NavbarServicePackage], typePackages: Seq[NavbarTypePackage])

  def htmlTransform(s: String): String = {
    val source = Source.fromString(s.trim)

    val lines = source.getLines().map(_.trim).toList

    if (lines.size > 1) {

      def split(remains: List[String], results: List[List[String]]): List[List[String]] = {
        remains match {
          case Nil => results.reverse
          case _ =>
            val (successiveLines, nextRemains) = remains.span(_ != "")
            val rest = if (nextRemains.nonEmpty) nextRemains.tail else Nil
            split(rest, successiveLines :: results)
        }
      }

      val splitLines = split(lines, Nil)

      splitLines.map { parLines =>
        parLines.mkString("<p>", " ", "</p>")
      }.mkString("")

    } else {
      s
    }
  }

  def parseTagsAndDescription(s: String): (Set[String], String) = {

    def lineIsTag(l: String) = l.trim.startsWith("@")

    val (tagLineItr, descLineItr) = Source.fromString(s)
      .getLines()
      .partition(lineIsTag)

    val tagLines = tagLineItr.toList
    val descLines = descLineItr.toList

    val tags = tagLines.map(_.trim).map(_.stripPrefix("@")).toList.toSet
    val desc = descLines.toList.mkString("\n")

    (tags, desc)
  }

  case class FieldDocumentation(description: String, requirement: Option[String], nested: Boolean)

  def docToDescriptionWithTags(doc: DocInfo): (Set[String], String) = {

    val leadResult = doc.leadingOpt.map(parseTagsAndDescription)
    val trailResult = doc.trailingOpt.map(parseTagsAndDescription)

    val tags: Set[String] = leadResult.map(_._1).getOrElse(Set.empty[String]) ++ trailResult.map(_._1).getOrElse(Set.empty[String])

    val desc = docMarkdownToHtml(leadResult.map(_._2), trailResult.map(_._2))

    (tags, desc)
  }

  def fieldDoc(doc: DocInfo): FieldDocumentation = {
    val (tags, desc) = docToDescriptionWithTags(doc)

    val requirement = (tags.contains("optional"), tags.contains("required")) match {
      case (true, _) => Some("optional")
      case (false, true) => Some("required")
      case _ => None
    }

    val nested = tags.contains("nested")

    FieldDocumentation(desc, requirement, nested)
  }

  case class MessageDocumentation(description: String, simple: Boolean)

  def messageDoc(doc: DocInfo): MessageDocumentation = {
    val (tags, desc) = docToDescriptionWithTags(doc)

    val simple = tags.contains("simple")

    MessageDocumentation(desc, simple)
  }

  def docMarkdownToHtml(leadOpt: Option[String], trailOpt: Option[String]): String = {

    def txtMarkTransform(s: String): String = {
      Processor.process(s, Configuration.builder().forceExtentedProfile().build())
    }

    val leading = leadOpt.map(txtMarkTransform).getOrElse("")

    val trailing = trailOpt.map(txtMarkTransform).getOrElse("")

    leading + trailing
  }

  def docToDescription(doc: DocInfo): String = {
    val (_, desc) = docToDescriptionWithTags(doc)
    desc
  }

  def serviceUrl(v: Service): String = {
    v.context.protoPackage + "." + v.name + ".html"
  }
  def msgUrl(v: Message): String = {
    v.context.protoPackage + "." + v.name + ".html"
  }
  def enumUrl(v: Enum): String = {
    v.context.protoPackage + "." + v.name + ".html"
  }

  def navbarJson(messages: Seq[Message], enums: Seq[Enum], services: Seq[Service]): NavbarApiData = {

    val servicePackages: Seq[NavbarServicePackage] = services.groupBy(_.context.protoPackage)
      .mapValues(_.map(s => NavbarService(s.name, serviceUrl(s))).sortBy(_.name))
      .toSeq
      .map { case (pkg, srvList) => NavbarServicePackage(pkg, srvList) }
      .sortBy(_.`package`)

    val msgTypesWithPackage: Seq[(String, NavbarType)] = messages.map(m => (m.context.protoPackage, NavbarType(m.name, msgUrl(m), false)))

    val enumTypesWithPackage: Seq[(String, NavbarType)] = enums.map(e => (e.context.protoPackage, NavbarType(e.name, enumUrl(e), true)))

    val allTypesWithPackage = msgTypesWithPackage ++ enumTypesWithPackage

    val typePackages: Seq[NavbarTypePackage] = allTypesWithPackage.groupBy(_._1)
      .mapValues(_.map(_._2).sortBy(_.name))
      .toSeq
      .map { case (pkg, typList) => NavbarTypePackage(pkg, typList) }
      .sortBy(_.`package`)

    NavbarApiData(servicePackages, typePackages)
  }


  val multiplicitySingle = "single"
  val multiplicityMultiple = "multiple"

  def messageFieldType(field: Field) = {

    import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type
    val name = field.fieldType match {
      case Type.TYPE_DOUBLE => "double"
      case Type.TYPE_FLOAT => "float"
      case Type.TYPE_INT32 => "int32"
      case Type.TYPE_INT64 => "int64"
      case Type.TYPE_UINT32 => "uint32"
      case Type.TYPE_UINT64 => "uint64"
      case Type.TYPE_SINT32 => "sint32"
      case Type.TYPE_SINT64 => "sint64"
      case Type.TYPE_FIXED32 => "fixed32"
      case Type.TYPE_FIXED64 => "fixed64"
      case Type.TYPE_SFIXED32 => "sfixed32"
      case Type.TYPE_SFIXED64 => "sfixed64"
      case Type.TYPE_BOOL => "bool"
      case Type.TYPE_STRING => "string"
      case Type.TYPE_BYTES => "bytes"
      case Type.TYPE_GROUP => "group"
      case Type.TYPE_MESSAGE =>
        field.fieldTypeName.getOrElse { throw new IllegalArgumentException("Saw type message but had no type name") }
      case Type.TYPE_ENUM =>
        field.fieldTypeName.getOrElse { throw new IllegalArgumentException("Saw type enum but had no type name") }
    }

    import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label
    val category = field.label match {
      case Label.LABEL_REQUIRED | Label.LABEL_OPTIONAL => multiplicitySingle
      case Label.LABEL_REPEATED => multiplicityMultiple
    }

    field.fieldType match {
      case Type.TYPE_MESSAGE | Type.TYPE_ENUM  =>
        val (pre, suf) = name.splitAt(name.lastIndexOf('.'))
        FieldType(None, Some(suf.drop(1)), Some(pre), category)
      case _ =>
          FieldType(Some(name), None, None, category)
    }

  }

  def labelText(label: FieldDescriptorProto.Label): String = {
    label match {
      case FieldDescriptorProto.Label.LABEL_OPTIONAL => "optional"
      case FieldDescriptorProto.Label.LABEL_REQUIRED => "required"
      case FieldDescriptorProto.Label.LABEL_REPEATED => "repeated"
    }
  }

  def mentionsFor(name: String, references: CrossReferences): Mentions = {

    def toMethodMention(tup: (String, String)): MethodMention = MethodMention(tup._1, tup._2)

    val params = references.fromServiceParams.get(name).map(_.map(toMethodMention))
    val results = references.fromServiceResults.get(name).map(_.map(toMethodMention))

    Mentions(references.fromMessages.get(name), params, results)
  }

  def messageJson(msg: Message, context: Context, references: CrossReferences): MessageApiData = {

    val fields = msg.fields.map { f =>

      val doc = fieldDoc(f.docInfo)

      val fieldType = messageFieldType(f)

      val nestedOpt = if (doc.nested) Some(true) else None

      MessageField(f.name, fieldType, labelText(f.label), doc.description, doc.requirement, nestedOpt)
    }

    val msgDoc = messageDoc(msg.doc)

    val message = MessageJson(msg.name, msg.context.protoPackage, msgDoc.description, fields, msgDoc.simple)

    MessageApiData(context.version, message, mentionsFor(msg.fullName, references))
  }

  def enumJson(enum: Enum, context: Context, references: CrossReferences): EnumApiData = {

    val fields = enum.fields.map { f => EnumField(f.name, f.number, docToDescription(f.doc)) }

    EnumApiData(context.version, enum.name, enum.context.protoPackage, docToDescription(enum.doc), fields, mentionsFor(enum.fullName, references))
  }

  def methodJson(method: Method): MethodJson = {

    val inputMessage = method.inputType

    val outputMessage = method.outputType.fields.headOption

    val params = inputMessage.fields.map { f =>

      val doc = fieldDoc(f.docInfo)

      val fieldType = messageFieldType(f)

      MethodParameter(f.name, fieldType, None)
    }

    val subscription = method.subscription.map(msg => FieldType(None, Some(msg.name), Some(msg.context.protoPackage), "single"))

    val addressing = method.addressing match {
      case ServiceAddressing.NEVER => None
      case ServiceAddressing.OPTIONALLY => Some("optional")
      case ServiceAddressing.ALWAYS => Some("required")
    }

    val returnValue = outputMessage.map(messageFieldType).getOrElse(FieldType(Some("void"), None, None, "single"))

    MethodJson(method.name, params, returnValue, subscription, addressing, docToDescription(method.docInfo))
  }


  def nestedMessagesForMethod(m: MethodJson, messageMap: Map[String, MessageJson]): Seq[String] = {

    def messagesForFieldType(fieldType: FieldType): Seq[String] = {
      if (fieldType.messageType.nonEmpty) {
        val fullMessageName = fieldType.messagePackage.get + "." + fieldType.messageType.get

        messageMap.get(fullMessageName).map { msg =>
          if (!msg.simple) {
            Seq(msg.fullName) ++ nestedMessagesInMessage(msg)
          } else {
            Nil
          }
        }.getOrElse(Nil)
      } else {
        Nil
      }
    }

    def nestedMessagesInMessage(msg: MessageJson): Seq[String] = {
      msg.fields.flatMap { f =>
        if (f.nested == Some(true)) {
          messagesForFieldType(f.`type`)
        } else {
          Nil
        }
      }
    }

    m.parameters.flatMap { p => messagesForFieldType(p.`type`) }
  }

  def serviceJson(service: Service, context: Context, messageMap: Map[String, MessageJson]) = {

    val methods = service.methods.map(methodJson)

    val nestedMessages: Seq[String] = methods.flatMap(m => nestedMessagesForMethod(m, messageMap))

    val nestedMap = nestedMessages.distinct.flatMap(name => messageMap.get(name).map(m => (name, m))).toMap

    ServiceApiData(context.version, service.name, service.context.protoPackage, docToDescription(service.doc), methods, nestedMap)
  }

}
