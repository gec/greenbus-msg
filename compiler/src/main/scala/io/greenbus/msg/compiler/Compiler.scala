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

import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import io.greenbus.msg.compiler.proto.CompilerExtensions
import io.greenbus.msg.compiler.jvm.{JavaGenerator, ScalaGenerator}

import scala.collection.JavaConversions._

object Compiler {
  def main(args: Array[String]) {
    val registry = ExtensionRegistry.newInstance
    CompilerExtensions.registerAllExtensions(registry)

    val request = CodeGeneratorRequest.parseFrom(System.in, registry)

    val parsed = Parser.parseFiles(request.getProtoFileList.toSeq)

    //System.err.println(request)
    //System.err.println(parsed)

    val files = Option(System.getProperty("genTarget")) match {
      case Some("scala") => ScalaGenerator.generate(parsed)
      case Some("java") => JavaGenerator.generate(parsed)
      case Some(thingElse) => Seq()
      case None => Seq()
    }
    //System.err.println(files)

    val response = CodeGeneratorResponse.newBuilder.addAllFile(files).build
    response.writeTo(System.out)
  }
}



