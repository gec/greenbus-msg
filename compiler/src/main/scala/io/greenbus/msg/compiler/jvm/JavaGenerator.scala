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

import io.greenbus.msg.compiler.{Service, Message, CodeWriter}
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto

object JavaGenerator extends JvmGenerator {
  import CodeWriter._

  def packageName(service: Service): String = {
    service.options.javaPackage match {
      case Some(pkg) => pkg
      case None => service.context.targetInfo.javaInfo.javaPackage + ".japi.service"
    }
  }

  def tab: String = "\t"

  def newLine: String = "\n"

  def fileExt: String = "java"

  def collectionOf(typ: String): String = s"java.util.List<$typ>"

  def futureOf(typ: String): String = s"ListenableFuture<$typ>"

  def argument(typ: String, name: String): String = s"$typ $name"

  def boolType: String = "Boolean"

  def langType(field: FieldDescriptorProto, map: Map[String, Message]): String = {
    import FieldDescriptorProto.Type._

    val typ = field.getType

    typ match {
      case TYPE_BOOL => "bool"
      case TYPE_BYTES => "byte[]"
      case TYPE_DOUBLE => "double"
      case TYPE_INT32 => "int"
      case TYPE_INT64 => "long"
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

  def renderServiceFile(servicePackage: String, serviceName: String, signatures: Seq[String], definitions: Seq[(CodeWriter) => Unit], descriptors: Seq[CodeWriter => Unit]): (CodeWriter) => Unit = {
    implicit w: CodeWriter => {
      0 << s"package $servicePackage;"
      0 << ""

      0 << "import javax.annotation.Nullable;"
      0 << "import com.google.common.util.concurrent.AsyncFunction;"
      0 << "import com.google.common.util.concurrent.Futures;"
      0 << "import com.google.common.util.concurrent.ListenableFuture;"
      0 << "import io.greenbus.msg.japi.*;"
      0 << ""

      0 << s"public class $serviceName {"
      0 << ""
      1 << s"private $serviceName() {}"
      0 << ""
      1 << "public static Client client(SessionMessaging session) {"
      2 << "return new ClientImpl(session);"
      1 << "}"
      0 << ""
      1 << "public interface Client {"
      signatures.foreach(2 << _ + ";")
      1 << "}"
      0 << ""

      1 << "private static class ClientImpl implements Client {"
      0 << ""
      2 << "private final SessionMessaging session;"
      0 << ""
      2 << "public ClientImpl(SessionMessaging session) {"
      3 << "this.session = session;"
      2 << "}"
      0 << ""

      definitions.foreach { procedure =>
        procedure(w.indented(2))
        0 << ""
      }
      1 << "}"
      0 << ""
      1 << "public static class Descriptors {"
      descriptors.foreach(f => f(w.indented(2)))
      1 << "}"
      0 << ""
      0 << "}"
    }
  }

  def renderRequestDescriptor(methodName: String, requestId: String, requestType: String, responseType: String): CodeWriter => Unit = {
    { implicit w: CodeWriter =>
      0 << s"public static class ${methodName.capitalize} implements RequestDescriptor<$requestType, $responseType> {"
      1 <<  "public String getRequestId() {"
      2 << s"return $requestId;"
      1 <<  "}"
      1 << s"public $requestType decodeRequest(byte[] bytes) throws java.io.IOException {"
      2 << s"return $requestType.parseFrom(bytes);"
      1 <<  "}"
      1 << s"public byte[] encodeResponse($responseType response) {"
      2 << s"return response.toByteArray();"
      1 <<  "}"
      0 <<  "}"
    }
  }

  def renderRequestSignature(methodName: String, argList: Seq[String], addressed: Boolean, headers: Boolean, retType: String): String = {
    val addressArg = if (addressed) Seq("String destination") else Seq()
    val headersArg = if (headers) Seq("java.util.Map<String, String> headers") else Seq()

    val args = (argList ++ addressArg ++ headersArg).mkString(", ")

    s"public ${futureOf(retType)} $methodName($args)"
  }

  def renderRequest(requestId: String, signature: String, buildProcedure: (CodeWriter) => Unit, extractProcedure: (CodeWriter) => Unit, addressed: Boolean, headers: Boolean, retType: String): (CodeWriter) => Unit = {
    val addressRendered = if (addressed) "destination" else "null"
    val headerRendered = if (headers) "headers" else "null"

    val writeRequest = {
      implicit w: CodeWriter => {
        0 << "@Override"
        0 << signature + " {"
        buildProcedure(w.indented(1))
        0 << ""
        1 << s"final ListenableFuture<byte[]> future = session.request($requestId, $headerRendered, $addressRendered, requestBytes);"
        0 << ""
        1 << s"final ListenableFuture<$retType> mappedFuture = Futures.transform(future, new AsyncFunction<byte[], $retType>() {"
        2 <<  "@Nullable"
        2 <<  "@Override"
        2 << s"public ListenableFuture<$retType> apply(@Nullable final byte[] responseBytes) throws Exception {"
        3 <<  "try {"
        extractProcedure(w.indented(4))
        4 <<  "return Futures.immediateFuture(response);"
        3 <<  "} catch (Throwable ex) {"
        4 <<  "return Futures.immediateFailedFuture(ex);"
        3 <<  "}"
        2 <<  "}"
        1 <<  "});"
        0 << ""
        1 << "return mappedFuture;"
        0 << "}"
      }
    }

    writeRequest
  }

  def renderSubscriptionSignature(methodName: String, argList: Seq[String], addressed: Boolean, headers: Boolean, retType: String, eventType: String): String = {
    val addressArg = if (addressed) Seq("String destination") else Seq()
    val headersArg = if (headers) Seq("java.util.Map<String, String> headers") else Seq()

    val args = (argList ++ addressArg ++ headersArg).mkString(", ")

    s"public ListenableFuture<SubscriptionResult<$retType, $eventType>> $methodName($args)"
  }

  def renderSubscription(requestId: String, signature: String, buildProcedure: (CodeWriter) => Unit, extractProcedure: (CodeWriter) => Unit, addressed: Boolean, headers: Boolean, retType: String, eventType: String): (CodeWriter) => Unit = {
    val addressRendered = if (addressed) "destination" else "null"
    val headerRendered = if (headers) "headers" else "null"

    val origFutType = "SubscriptionResult<byte[], byte[]>"
    val mappedFutType = s"SubscriptionResult<$retType, $eventType>"

    val writeRequest = {
      implicit w: CodeWriter => {
        0 <<  "@Override"
        0 << signature + " {"
        buildProcedure(w.indented(1))
        0 <<  ""
        1 << s"final ListenableFuture<SubscriptionResult<byte[], byte[]>> future = session.subscribe($requestId, $headerRendered, $addressRendered, requestBytes);"
        0 <<  ""
        1 << s"final ListenableFuture<$mappedFutType> mappedFuture = Futures.transform(future, new AsyncFunction<$origFutType, $mappedFutType>() {"
        2 <<  "@Nullable"
        2 <<  "@Override"
        2 << s"public ListenableFuture<$mappedFutType> apply(@Nullable final $origFutType subscriptionResult) throws Exception {"
        3 <<  "try {"
        4 <<  "final byte[] responseBytes = subscriptionResult.getResult();"
        extractProcedure(w.indented(4))
        0 <<  ""
        4 << s"final Subscription<$eventType> mappedSubscription = new Subscription<$eventType>() {"
        5 <<  "@Override"
        5 << s"public void start(final SubscriptionHandler<$eventType> acceptor) {"
        6 <<  "subscriptionResult.getSubscription().start(new SubscriptionHandler<byte[]>() {"
        7 <<  "@Override"
        7 <<  "public void handle(byte[] eventBytes) {"
        8 <<  "try {"
        9 << s"final $eventType event = $eventType.parseFrom(eventBytes);"
        9 <<  "acceptor.handle(event);"
        8 <<  "} catch (Throwable ex) {"
        9 <<  "// TODO: log parse error"
        8 <<  "}"
        7 <<  "}"
        6 <<  "});"
        5 <<  "}"
        0 <<  ""
        5 <<  "@Override"
        5 <<  "public void cancel() {"
        6 <<  "subscriptionResult.getSubscription().cancel();"
        5 <<  "}"
        0 <<  ""
        5 <<  "@Override"
        5 <<  "public String getId() {"
        6 <<  "return subscriptionResult.getSubscription().getId();"
        5 <<  "}"
        4 <<  "};"
        0 <<  ""
        4 << s"return Futures.immediateFuture(new $mappedFutType(response, mappedSubscription));"

        0 <<  ""
        3 <<  "} catch (Throwable ex) {"
        4 <<  "return Futures.immediateFailedFuture(ex);"
        3 <<  "}"
        2 <<  "}"
        1 <<  "});"
        0 <<  ""
        1 <<  "return mappedFuture;"
        0 <<  "}"
      }
    }

    writeRequest
  }

  def renderBuildProcedure(wrapType: String, setPrefix: String, fieldName: String): (CodeWriter) => Unit = {
    implicit w: CodeWriter => {
      0 << s"final $wrapType.Builder reqWrapperBuilder = $wrapType.newBuilder();"
      0 << s"reqWrapperBuilder.${setPrefix}${fieldName.capitalize}($fieldName);"
      0 << s"final byte[] requestBytes = reqWrapperBuilder.build().toByteArray();"
    }
  }

  def renderNoArgsBuildProcedure(wrapType: String): (CodeWriter) => Unit = {
    implicit w: CodeWriter => {
      0 << s"final byte[] requestBytes = $wrapType.newBuilder().build().toByteArray();"
    }
  }


  def renderMultiArgsBuildProcedure(wrapType: String): (CodeWriter) => Unit = {
    implicit w: CodeWriter => {
      0 << s"final byte[] requestBytes = request.toByteArray();"
    }
  }

  def renderExtractProcedure(wrapType: String, responseType: String, isCollection: Boolean, fieldName: String): (CodeWriter) => Unit = {
    implicit w: CodeWriter => {
      0 << s"final $wrapType responseWrapper = $wrapType.parseFrom(responseBytes);"
      isCollection match {
        case true => 0 << s"final $responseType response = responseWrapper.get${fieldName.capitalize}List();"
        case false => 0 << s"final $responseType response = responseWrapper.get${fieldName.capitalize}();"
      }
    }
  }

  def renderVoidExtractProcedure(wrapType: String): (CodeWriter) => Unit = {
    implicit w: CodeWriter => {
      0 << "final Boolean response = true;"
    }
  }

  def renderMultiExtractProcedure(wrapType: String): (CodeWriter) => Unit = {
    implicit w: CodeWriter => {
      0 << s"final $wrapType response = $wrapType.parseFrom(responseBytes);"
    }
  }
}

