/*
 * Copyright (C) 2011 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray
package utils

import akka.config.Config._

abstract class AkkaConfSettings(prefix: String) extends Logging {
  val definedSettings = getClass.getMethods.toList.map(_.getName).filter(_.head.isUpper).map(settingsKeyFromMethodName)

  def configString = config.getString(settingsKey)
  def configBool   = config.getBool(settingsKey)
  def configInt    = config.getInt(settingsKey)
  def configLong   = config.getLong(settingsKey)
  def configFloat  = config.getFloat(settingsKey)
  def configDouble = config.getDouble(settingsKey)

  def configString(defaultValue: String) = config.getString(settingsKey, defaultValue)
  def configBool(defaultValue: Boolean)  = config.getBool(settingsKey, defaultValue)
  def configInt(defaultValue: Int)       = config.getInt(settingsKey, defaultValue)
  def configLong(defaultValue: Long)     = config.getLong(settingsKey, defaultValue)
  def configFloat(defaultValue: Float)   = config.getFloat(settingsKey, defaultValue)
  def configDouble(defaultValue: Double) = config.getDouble(settingsKey, defaultValue)

  protected def settingsKey = prefix + settingsKeyFromMethodName(callingMethod(3))

  protected def callingMethod(depth: Int): String = {
    try {
      throw new Throwable
    } catch {
      case t: Throwable => t.getStackTrace()(depth).getMethodName
    }
  }

  protected def settingsKeyFromMethodName(methodName: String) = methodName.head.toLower + methodName.tail.foldLeft("") {
    case (result, c) if c.isUpper => result + '-' + c.toLower
    case (result, c) => result + c
  }

  def warnOnUndefinedExcept(additionallyDefined: String*) {
    val allDefined = definedSettings ++ additionallyDefined.map(settingsKeyFromMethodName)
    config.keys.toList.filter { key =>
      key.startsWith(prefix) && key.lastIndexOf('.') == prefix.length - 1 && !allDefined.contains(key.substring(prefix.length))
    } match {
      case Nil => // everything fine
      case illegalSettings => log.warn("Akka.conf contains one or more unknown settings: %s",
        illegalSettings.mkString("'", "', '", "'"))
    }
  }
}