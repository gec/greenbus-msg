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

case class CrossReferences(
        fromMessages: Map[String, Seq[String]],
        //fromMethods: Map[String, Seq[(String, String)]],
        fromServiceParams: Map[String, Seq[(String, String)]],
        fromServiceResults: Map[String, Seq[(String, String)]])

object CrossReferences {

  private def smartGroup[A, B](seq: Seq[(A, B)]): Map[A, Seq[B]] = {
    seq.groupBy(_._1).mapValues(_.map(_._2).distinct)
  }

  def findReferences(messages: Seq[Message], enums: Seq[Enum], services: Seq[Service]): CrossReferences = {

    val fromMessageFields: Seq[(String, String)] = messages.flatMap { m =>
      m.fields.flatMap(f => f.fieldTypeName.map(fname => (fname, m.fullName)))
    }

    /*val fromMethods: Seq[(String, (String, String))] = services.flatMap { s =>
      s.methods.flatMap { meth =>
        meth.inputType.fields.flatMap(f => f.fieldTypeName.map(fname => (fname, (s.fullName, meth.name))))
      }
    }*/

    val fromServiceParams: Seq[(String, (String, String))] = services.flatMap { s =>
      s.methods.flatMap { meth =>
        meth.inputType.fields.flatMap(f => f.fieldTypeName.map(fname => (fname, (s.fullName, meth.name))))
      }
    }

    val fromServiceResults: Seq[(String, (String, String))] = services.flatMap { s =>
      s.methods.flatMap { meth =>
        meth.outputType.fields.headOption.flatMap(f => f.fieldTypeName.map(fname => (fname, (s.fullName, meth.name))))
      }
    }

    CrossReferences(
      smartGroup(fromMessageFields),
      //smartGroup(fromMethods),
      smartGroup(fromServiceParams),
      smartGroup(fromServiceResults)
    )
  }
}
