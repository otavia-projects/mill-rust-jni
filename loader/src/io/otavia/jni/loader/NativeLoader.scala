package io.otavia.jni.loader

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.util.Properties

class NativeLoader(nativeLibrary: String) {
  NativeLoader.load(nativeLibrary)
}

object NativeLoader {

  private val loaded = mutable.HashSet.empty[String]
  private val failure = mutable.HashSet.empty[String]

  final def load(nativeLibrary: String): Unit = {
    val lib = System.mapLibraryName(nativeLibrary)
    val resourcePath = "/" + getCurrentTargetName + "/" + lib

    if (!loaded.contains(nativeLibrary) && !failure.contains(nativeLibrary)) {
      val tmp: Path = Files.createTempDirectory("rust-jni-")
      val extractedPath = tmp.resolve(lib)

      Option(this.getClass.getResourceAsStream(resourcePath)) match {
        case None =>
          failure.add(nativeLibrary)
          throw new UnsatisfiedLinkError(
            "Native library " + lib + " (" + resourcePath + ") cannot be found on the classpath."
          )
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

  private def getCurrentTargetName: String = Properties.propOrNone("RUN_RUST_TARGET") match {
    case Some(value) => value
    case None => targetName0()
  }

  private def targetName0(): String = {
    val os = toRustOS(System.getProperty("os.name").toLowerCase)
    val arch = System.getProperty("os.arch")
    s"$arch-$os"
  }

  private final def toRustOS(os: String): String = {
    if (os.contains("windows")) "-pc-windows-msvc"
    else if (os.contains("linux")) "-unknown-linux-gnu"
    else os
  }

}