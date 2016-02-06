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

object Util {
  def toCamelCase(str: String): String = {
    val parts = str.split('_')
    (parts.head ++ parts.tail.map(_.capitalize)).mkString("")
  }
}

trait LineWriter {
  def line(str: String)
  def <<(str: String) {
    line(str)
  }
}

trait CodeWriter {
  def apply(indent: Int): LineWriter
  def indented(n: Int): CodeWriter
  def space()
}

object CodeWriter {

  implicit def intToLineWriter(n: Int)(implicit w: CodeWriter) = w(n)

  def apply(tab: String, newLine: String, b: StringBuilder): CodeWriter = {
    new TabbedWriter(tab, newLine, 0, b)
  }

  private class TabbedWriter(tab: String, nl: String, baseIndent: Int, b: StringBuilder) extends CodeWriter {

    private class InternalLineWriter(indent: Int) extends LineWriter {
      def line(str: String) {
        Range(0, indent + baseIndent).foreach(_ => b.append(tab))
        b.append(str)
        b.append(nl)
      }
    }

    def apply(indent: Int): LineWriter = new InternalLineWriter(indent)

    def space() {
      b.append(nl)
    }

    def indented(n: Int): CodeWriter = new TabbedWriter(tab, nl, baseIndent + n, b)
  }

}

