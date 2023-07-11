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
 */


package io.github.otavia.jni.plugin

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
  private val NAME_MATCH = "name\\s*=\\s*\"(.+)\".*".r

  /** Default name used in Cargo.toml [package].name when use [[nativeInit]] to generate template project. */
  def defaultNativeName = T {
    artifactName()
  }

  /** Name used in Cargo.toml [package].name. */
  final def nativeName: T[String] = T {
    var startPackageScope: Boolean = false
    var endPackageScope: Boolean = false
    var nameInToml: Option[String] = None

    for (line <- os.read.lines(rustSourceRoot().head.path / "Cargo.toml")) {
      if (line.trim == "[package]") startPackageScope = true
      else if (line.trim.matches("[.*]")) if (startPackageScope) endPackageScope = true
      if (inPackageScope(startPackageScope, endPackageScope)) {
        line.trim match {
          case NAME_MATCH(name) => nameInToml = Some(name)
          case _ =>
        }
      }
    }
    nameInToml match {
      case Some(value) => value
      case None =>
        throw new IllegalArgumentException("You should define name in Cargo.toml at [package]")
    }
  }

  private def inPackageScope(start: Boolean, end: Boolean): Boolean = start && !end

  /** A mill command to generate native template project. */
  def nativeInit() = T.command {
    val home = T.workspace / artifactName() / "native"
    if (!os.exists(home)) {
      val src = home / "src"
      os.makeDir.all(src)
      val toml = home / "Cargo.toml"
      val tomlTemplate = // TODO: upgrade jni version
        s"""[package]
           |name = "${defaultNativeName()}" # generated by nativeInit with defaultNativeName
           |version = "0.1.0"
           |authors = ["Yan Kun <yan_kun_1992@foxmail.com>"]
           |edition = "2021"
           |
           |[dependencies]
           |jni = "0.20"
           |
           |[lib]
           |crate_type = ["cdylib"]""".stripMargin
      os.write(toml, tomlTemplate)


      os.write(src / "lib.rs",
        """use jni::JNIEnv;
          |use jni::objects::*;
          |use jni::sys::*;
          |""".stripMargin)
    } else T.ctx().log.error(s"$home already exists, ignore this step!")
  }

  /** Sources of rust jni project. */
  def rustSourceRoot = T.sources {
    T.workspace / artifactName() / "native"
  }

  /** Use release or debug model to build rust project. */
  def release: Boolean = true

  /** Rust build target of local computer, you can use the environment variable MILL_RUST_TARGET to set it. */
  def localTarget: String = if (System.getenv("MILL_RUST_TARGET") != null)
    System.getenv("MILL_RUST_TARGET") else {
    val os = System.getProperty("os.name").toLowerCase
    if (os.contains("windows")) "x86_64-pc-windows-msvc" else if (os.contains("linux")) "x86_64-unknown-linux-gnu"
    else "x86_64-pc-windows-msvc"
  }

  /**
   * All support target of cargo build.
   *
   * Setting up multiple compilation targets requires that your rust environment supports cross-compilation.
   * You can use the environment variable MILL_CROSS_RUST_TARGET to set it.
   *
   * format: MILL_CROSS_RUST_TARGET={target1},{target2}
   *
   * @return multiple compilation targets.
   */
  def crossTargets: Set[String] = {
    val cross = if (System.getenv("MILL_CROSS_RUST_TARGET") != null)
      System.getenv("MILL_CROSS_RUST_TARGET").split(",").toSet
    else Set.empty

    cross ++ Set(localTarget)
  }

  private def getNativeLibName(target: String, libraryName: String): String = target match {
    case _: String if target.contains("windows") => libraryName + ".dll"
    case _: String if target.contains("linux") => "lib" + libraryName + ".so"
    case _ => throw new IllegalArgumentException(s"Not support rust target: $target")
  }

  /**
   * Environment variables used in `cargo build`
   *
   * @since 0.2.0
   *
   */
  def cargoBuildEnvs: Map[String, String] = Map.empty

  /** Compile rust code. This target result is append to [[resources]] target, so the generated library is in
   * module's classpath. */
  def compileNative = T {
    val library = T.dest / "native"
    os.makeDir.all(library)

    val crateHome = rustSourceRoot().head.path

    for (target <- crossTargets) {
      if (release)
        os.proc("cargo", "build", "--release", "--target", target).call(cwd = crateHome, env = cargoBuildEnvs)
      else
        os.proc("cargo", "build", "--target", target).call(cwd = crateHome, env = cargoBuildEnvs)

      val mode = if (release) "release" else "debug"
      val name = getNativeLibName(target, nativeName())

      val from = crateHome / "target" / target / mode / name

      val to = library / target / name

      os.copy(from, to, replaceExisting = true, createFolders = true)
    }

    PathRef(T.dest)
  }


  override def resources: Sources = T.sources {
    super.resources().++(Seq(compileNative()))
  }

  /** cargo clean */
  def cleanNativeLib() = T.command {
    os.proc("cargo", "clean").call(cwd = rustSourceRoot().head.path)
  }

}
