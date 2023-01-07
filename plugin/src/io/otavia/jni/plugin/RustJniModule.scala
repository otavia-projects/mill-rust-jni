package io.otavia.jni.plugin

import mill._
import mill.define.Sources
import mill.scalalib.JavaModule

/**
 * A module to build rust cargo project.
 * <h3>Step</h3>
 *  1. `mill {module}.nativeInit` to generate native template project.
 *  1. implementation jni method by rust in native/src/lib.rs
 *  1. `mill {module}.compileNative` to compile native project.
 *
 */
trait RustJniModule extends JavaModule {

  /** Default name used in Cargo.toml [package].name when use [[nativeInit]] to generate template project. */
  def defaultNativeName = T {
    artifactName()
  }

  /** Name used in Cargo.toml [package].name. */
  def nativeName = T {
    os.read(rustSourceRoot().head.path / "Cargo.toml").linesIterator
    artifactName()
  }

  /** A mill command to generate native template project */
  def nativeInit() = T.command {
    val home = T.workspace / artifactName() / "native"
    if (!os.exists(home)) {
      os.makeDir.all(home / "src")
      val toml = home / "Cargo.toml"

    }
  }

  def rustSourceRoot = T.sources {
    T.workspace / artifactName() / "native"
  }

  def release: Boolean = true

  def localTarget: String = if (System.getenv("MILL_RUST_TARGET") != null)
    System.getenv("MILL_RUST_TARGET") else "x86_64-pc-windows-msvc"

  def crossTargets: Set[String] = {
    val cross = if (System.getenv("MILL_CROSS_RUST_TARGET") != null)
      System.getenv("MILL_CROSS_RUST_TARGET").split(",").toSet
    else Set.empty

    cross ++ Set(localTarget)
  }

  def getNativeLibName(target: String, libraryName: String): String = target match {
    case _: String if target.contains("windows") => libraryName + ".dll"
    case _: String if target.contains("linux") => libraryName + ".so"
    case _ => throw new IllegalArgumentException(s"Not support rust target: $target")
  }

  def compileNative = T {
    val library = T.dest / "native"
    os.makeDir.all(library)

    val crateHome = rustSourceRoot().head.path

    for (target <- crossTargets) {
      if (release)
        os.proc("cargo", "build", "--release", "--target", target).call(cwd = crateHome)
      else
        os.proc("cargo", "build", "--target", target).call(cwd = crateHome)

      val mode = if (release) "release" else "debug"
      val name = getNativeLibName(target, artifactName())

      val from = crateHome / "target" / target / mode / name

      val to = library / target / name

      os.copy(from, to, replaceExisting = true, createFolders = true)
    }

    PathRef(library)
  }


  override def resources: Sources = T.sources {
    super.resources().++(Seq(compileNative()))
  }

  def cleanNativeLib() = T.command {
    os.proc("cargo", "clean").call(cwd = rustSourceRoot().head.path)
  }

}
