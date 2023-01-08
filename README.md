<div align=center>
</div>
<h1 align=center>mill-rust-jni</h1>

<p align=center ><b>A mill plugin for build rust jni code!</b></p>

![GitHub](https://img.shields.io/github/license/otavia-projects/mill-rust-jni)

<hr>

Language: [简体中文](./README.zh_cn.md)

<hr>

## Setup

In your project build script `build.sc`, import the plugin with the following code:

```scala
import $ivy.`io.otavia::mill-rust:{version}`
import io.otavia.jni.plugin.RustJniModule
```

Then define a jni source code module, the module name `libjni` can be anything you like.

```scala
object libjni extends RustJniModule {

}
```

You can then choose to generate a rust jni project template using the following command

```shell
mill libjni.nativeInit
```

This command will generate the `native` directory in the `libjni` module, the `native` directory is A simple `Cargo`
project with the following project directory structure.

```text
|-- libjni
|   |-- native
|   |   |-- Cargo.toml
|   |   |-- src
|   |   |   |-- lib.rs

```

You can of course create the `native` project structure manually, but if the `native` directory already exists
the `nativeInit` command will be ignored.

### Customized settings

The `RustJniModule` contains several special definitions that you can override.

| Definition        | Description                                                                               |
|-------------------|-------------------------------------------------------------------------------------------|
| defaultNativeName | Define the jni library name for the `nativeInit` command to generate the project template |
| release           | Define cargo build mode                                                                   |

Also contains the following commands or targets.

| Definition     | Description                                                                                                                                          |
|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| nativeName     | jni library name, parsed from Cargo.toml [package].name                                                                                              |
| rustSourceRoot | rust source code root, i.e. {module}/native                                                                                                          |
| localTarget    | The rust target that can be run on the current computer, set using the environment variable MILL_RUST_TARGET                                         |
| crossTargets   | The targets supported by rust cross-compilation are set using the environment variable MILL_CROSS_RUST_TARGET, with a comma separating the targets   |
| compileNative  | Compiling rust code                                                                                                                                  |
| resources      | The output of `compileNative` is appended to `resources`, so the resulting binary library is automatically included in the module's `localClasspath` |

## Building jar packages

Since the results of `compileNative` are automatically appended to the `resources` of the module, the jar package of the
module built with mill will include all the generated binary libraries.
jar package will contain the following directory structure.

```text
|-- native
|   |-- {rust_target1}
|   |   |-- {library_name}
|   |-- {rust_target2}
|   |   |-- {library_name}
...
```

## Load

Using jni-generated libraries is very simple, for example you have the following building script:

```scala
object libjni extends RustJniModule { // You can also extends PublishModule to publish this library jar to maven central 

  override def release: Boolean = true

}

object jni_jvm_interface extends ScalaModule { // jni_jvm_interface is example module, it can be  
  override def scalaVersion = "3.2.1"

  // use this to dependent maven central jni module, or other dependencies.
  // if this module is include jni jvm interface, you can also add a loader helper by this project.
  override def ivyDeps: T[Loose.Agg[Dep]] = Agg(
    ivy"{organization}:{artifactId}:{version}", // some dependency
    ivy"io.otavia::jni-loader:{version}" // loader helper
  )

  // use this to dependent local jni module dependencies.
  override def moduleDeps: scala.Seq[JavaModule] = scala.Seq(libjni)

}


```

Then define the native methods on the jvm in the jni_jvm_interface module:

defined by scala

```scala
package com.github.example

object RustJNI extends NativeLoader("libjni") {
  @native def add(a: Int, b: Int): Int
}

// or in class

class Adder(val base: Int) extends NativeLoader("libjni") {
  @native def plus(term: Int): Int
}
```

defined by java

```java
package com.github.example;

class JavaJNI {
    private int base = 0;

    public static native int add(int a, int b);

    public native void plus(int term);

}

```

Correspondingly, the code implemented in the libjni module native/src/lib.rs is

```rust
use jni::JNIEnv;
use jni::objects::*;
use jni::sys::*;

#[no_mangle]
pub unsafe extern "C" fn Java_com_github_example_RustJNI_00024_add(env: JNIEnv, this: jobject, a: jint, b: jint) -> jint {
    a + b
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_github_example_Adder_plus(env: JNIEnv, this: jobject, term: jint) -> jint {
    todo!()
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_github_example_JavaJNI_add(env: JNIEnv, clz: jclass, a: jint, b: jint) -> jint {
    todo!()
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_github_example_JavaJNI_plus(env: JNIEnv, this: jobject, term: jint) {
    todo!()
}

```

`NativeLoader` uses the environment variable `RUN_RUST_TARGET` to specify the correct runtime rust target, for example
on the author's x86_64 Windows 10 machine

```shell
RUN_RUST_TARGET="x86_64-pc-windows-msvc"
```

If not specified, `NativeLoader` will speculate on possible targets, which may result in not loading the correct library
file.

## rust jni method naming rules

This plugin does not use `javah` to generate C headers, because the corresponding jni method naming rules are very
simple:

1. Use the `Java_` prefix for the method name.
2. Next, use the package name of the class where the native method is located, and convert the dot to an underscore.
3. Next, use the class name, or in the case of scala object objects, using the object name + `_00024`.
4. Next is the native method name.
