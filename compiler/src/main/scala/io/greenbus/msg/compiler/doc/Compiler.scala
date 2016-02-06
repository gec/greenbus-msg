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

import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorResponse, CodeGeneratorRequest}
import io.greenbus.msg.compiler.proto.CompilerExtensions
import scala.collection.JavaConversions._

object Compiler {

  def main(args: Array[String]) {
    val registry = ExtensionRegistry.newInstance
    CompilerExtensions.registerAllExtensions(registry)

    val request = CodeGeneratorRequest.parseFrom(System.in, registry)

    val parsed = Parser.parseFiles(request.getProtoFileList.toSeq)

    //System.err.println(request)

    val version = System.getProperty("api.version", "unknown")

    val (messages, enums, services) = filterRelevantSet(parsed)

    val files = AngularStandaloneBackend.output(messages, enums, services, JsonBackend.Context(version))

    val response = CodeGeneratorResponse.newBuilder
      .addAllFile(files)
      .build()

    response.writeTo(System.out)
  }

  def filterRelevantSet(parsed: ParseResult): (Seq[Message], Seq[Enum], Seq[Service]) = {

    val bannedSources = Set("CompilerExtensions.proto")

    val bannedPackages = Set("google.protobuf")

    val methodWrappers: Seq[(String, String)] = parsed.services.flatMap { s =>
      s.methods.flatMap { m =>
        List(
          (m.inputType.context.protoPackage, m.inputType.name),
          (m.outputType.context.protoPackage, m.outputType.name)
        )
      }
    }

    val methodWrapperSet = methodWrappers.toSet

    def isWrapper(m: Message): Boolean = methodWrapperSet.contains((m.context.protoPackage, m.name))

    val messages = parsed.messages
      .filterNot(isWrapper)
      .filterNot(v => bannedPackages.contains(v.context.protoPackage))
      .filterNot(v => bannedSources.contains(v.context.fileName))

    val enums = parsed.enums
      .filterNot(v => bannedPackages.contains(v.context.protoPackage))
      .filterNot(v => bannedSources.contains(v.context.fileName))

    val services = parsed.services
      .filterNot(v => bannedPackages.contains(v.context.protoPackage))
      .filterNot(v => bannedSources.contains(v.context.fileName))

    (messages, enums, services)
  }
}
