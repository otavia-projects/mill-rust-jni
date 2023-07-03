/*
 * Copyright 2023 Yan Kun <yan_kun_1992@foxmail.com>
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
 *
 * This file is fork from sbt-jni(https://github.com/sbt/sbt-jni) under
 * BSD-3-Clause license.
 */

package io.github.otavia.jni.loader

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.util.Properties

/**
 * A helper class for load native library publish by `io.otavia.jni.plugin`
 *
 * @param nativeLibrary library name.
 */
class NativeLoader(nativeLibrary: String) {
  NativeLoader.load(nativeLibrary)
}

object NativeLoader {

  private val loaded = mutable.HashSet.empty[String]
  private val failure = mutable.HashSet.empty[String]

  final def load(nativeLibrary: String): Unit = {
    val lib = System.mapLibraryName(nativeLibrary)
    val resourcePath = "/native/" + getCurrentTargetName + "/" + lib

    if (!loaded.contains(nativeLibrary) && !failure.contains(nativeLibrary)) {
      val tmp: Path = Files.createTempDirectory("rust-jni-")
      val extractedPath = tmp.resolve(lib)

      Option(this.getClass.getResourceAsStream(resourcePath)) match {
        case None =>
          try {
            System.loadLibrary(nativeLibrary)
          } catch {
            case ex: Exception =>
              failure.add(nativeLibrary)
              throw new UnsatisfiedLinkError("Native library " + lib +
                " (" + resourcePath + ") cannot be found on the classpath." + " And also can't find native library [" +
                lib + "] from java.library.path " + ex.getMessage)
          }
          loaded.add(nativeLibrary)
        case Some(resourceStream) =>
          try {
            Files.copy(resourceStream, extractedPath)
            System.load(extractedPath.toAbsolutePath.toString)
          } catch {
            case ex: Exception =>
              failure.add(nativeLibrary)
              throw new UnsatisfiedLinkError("Error while extracting native library: " + ex)
          }
          loaded.add(nativeLibrary)
      }
    } else if (failure.contains(nativeLibrary)) {
      throw new UnsatisfiedLinkError(
        "Native library " + lib + " (" + resourcePath + ") cannot be found on the classpath."
      )
    }
  }

  private def getCurrentTargetName: String = Properties.envOrNone("RUN_RUST_TARGET") match {
    case Some(value) => value
    case None => targetName0()
  }

  private def targetName0(): String = {

    val os = toRustOS(System.getProperty("os.name").toLowerCase)
    val arch = toRustArch(System.getProperty("os.arch"))
    s"$arch$os"
  }

  private def toRustArch(arch: String): String = {
    if (arch.contains("amd64")) "x86_64" else arch
  }

  private final def toRustOS(os: String): String = {
    if (os.contains("windows")) "-pc-windows-msvc"
    else if (os.contains("linux")) "-unknown-linux-gnu"
    else os
  }

}