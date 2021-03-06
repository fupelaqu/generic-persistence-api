/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.softnetwork.utils

import com.typesafe.scalalogging.StrictLogging

import java.io.InputStream
import scala.io.{Source => ScalaIOSource}

object ClasspathResources extends ClasspathResources

trait ClasspathResources extends StrictLogging {

  def streamToString(is: InputStream): String = ScalaIOSource.fromInputStream(is).mkString

  def fromClasspathAsString(fileName: String): String = streamToString(fromClasspathAsStream(fileName))

  def fromClasspathAsStream(fileName: String): InputStream = Option(getClass.getClassLoader.getResourceAsStream(fileName)) match {
    case Some(i) => i
    case _ => 
      logger.error(s"file $fileName not found in the classpath")
      null
  }

}