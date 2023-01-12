<div align=center>
</div>
<h1 align=center>mill-rust-jni</h1>

<p align=center ><b>用于编译 rust 实现的 jni 代码的 mill 构建插件！</b></p>

![GitHub](https://img.shields.io/github/license/otavia-projects/mill-rust-jni)
![Maven Central](https://img.shields.io/maven-central/v/io.github.otavia-projects/mill-rust_mill0.10_2.13)
![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/io.github.otavia-projects/mill-rust_mill0.10_2.13?server=https%3A%2F%2Fs01.oss.sonatype.org)
![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/io.github.otavia-projects/mill-rust_mill0.10_2.13?server=https%3A%2F%2Fs01.oss.sonatype.org)

<hr>

Language: [English](./README.md)

<hr>

## 示例

[rust-scala-demo](https://github.com/yankun1992/rust-scala-demo)

## 设置

在您的项目构建文件 `build.sc` 中， 通过如下代码引入插件：

```scala
import $ivy.`io.github.otavia-projects::mill-rust_mill$MILL_BIN_PLATFORM:{version}`
import io.github.otavia.jni.plugin.RustJniModule
```

然后定义一个 jni 源码模块：

```scala
object libjni extends RustJniModule {

}
```

然后您可以选择使用如下命令生成 rust jni 项目模板

```shell
mill libjni.nativeInit
```

这个命令将会在 `libjni` (您自定义的模块名) 模块中生成 `native` 目录，`native` 目录是
一个简单的 `Cargo` 项目，项目目录结构如下：

```text
|-- libjni
|   |-- native
|   |   |-- Cargo.toml
|   |   |-- src
|   |   |   |-- lib.rs

```

当然您也可以手动创建 `native` 项目结构，如果 `native` 目录已经存在 `nativeInit`
命令将会被忽略。

### 自定义设置

`RustJniModule` 包含几个特殊的定义您可以重载：

| 定义                | 描述                               |
|-------------------|----------------------------------|
| defaultNativeName | 定义 jni 库名，用于 nativeInit 命令生成项目模板 |
| release           | 定义 cargo 构建模式                    |

还包含以下命令或目标：

| 定义             | 描述                                                                      |
|----------------|-------------------------------------------------------------------------|
| nativeName     | jni 库名， 从 Cargo.toml [package].name 解析                                  |
| rustSourceRoot | rust 源码根目录，即 {module}/native                                            |
| localTarget    | 当前计算机可运行的 rust target ， 使用环境变量 MILL_RUST_TARGET 设置                      |
| crossTargets   | rust 交叉编译支持的 target，使用环境变量 MILL_CROSS_RUST_TARGET 设置，各 target之间使用逗号分割   |
| compileNative  | 编译 rust 代码                                                              |
| resources      | compileNative 的输出结果追加在  resources 中， 所以生成的二进制库自动包含在模块的 localClasspath 中 |

## 构建 jar 包

由于 `compileNative` 的结果自动追加到了模块的 `resources` 中，所以使用 mill 构建模块的 jar
包将会把生成的所有二进制库包含进 jar 包中。jar 包中将会包含如下目录结构

```text
|-- native
|   |-- {rust_target1}
|   |   |-- {library_name}
|   |-- {rust_target2}
|   |   |-- {library_name}
...
```

## 加载

使用 jni 生成的库非常简单，例如您有如下构建模块

```scala
object libjni extends RustJniModule { // 您也可以通过继承 PublishModule 将您的二进制 jar 包发布到 maven 仓库

  override def release: Boolean = true

}

object jni_jvm_interface extends ScalaModule { // jni_jvm_interface 是一个示例名称，您可以设置任何合法的名称
  override def scalaVersion = "3.2.1"

  // 使用 ivyDeps 依赖二进制 jar 包， 或其他 jar 包 
  // 如果您的模块包含 native 方法，您可以使用 jni-loader 轻松加载您的 二进制 jar 包
  override def ivyDeps: T[Loose.Agg[Dep]] = Agg(
    ivy"{organization}:{artifactId}:{version}", // 一些依赖
    ivy"io.github.otavia-projects::jni-loader:{version}" // jni-loader 依赖
  )

  // 如果您的二进制模块是本地的，使用 moduleDeps 加入依赖
  override def moduleDeps: scala.Seq[JavaModule] = scala.Seq(libjni)

}


```

然后在 jni_jvm_interface 模块中定义 jvm 上的 native 方法:

使用 scala 定义

```scala
package io.github.example

import io.github.otavia.jni.loader.NativeLoader

object RustJNI extends NativeLoader("libjni") {
  @native def add(a: Int, b: Int): Int
}

// or in class

class Adder(val base: Int) extends NativeLoader("libjni") {
  @native def plus(term: Int): Int
}
```

使用 java 定义

```java
package io.github.example;

import io.github.otavia.jni.loader.NativeLoader;

class JavaJNI extends NativeLoader {
    private int base = 0;

    JavaJNI() {
        super("libjni");
    }

    public static native int add(int a, int b);

    public native void plus(int term);

}

```

对应的，在 libjni 模块 native/src/lib.rs 中实现的代码为

```rust
use jni::JNIEnv;
use jni::objects::*;
use jni::sys::*;

#[no_mangle]
pub unsafe extern "C" fn Java_io_github_example_RustJNI_00024_add(env: JNIEnv, this: jobject, a: jint, b: jint) -> jint {
    a + b
}

#[no_mangle]
pub unsafe extern "C" fn Java_io_github_example_Adder_plus(env: JNIEnv, this: jobject, term: jint) -> jint {
    todo!()
}

#[no_mangle]
pub unsafe extern "C" fn Java_io_github_example_JavaJNI_add(env: JNIEnv, clz: jclass, a: jint, b: jint) -> jint {
    todo!()
}

#[no_mangle]
pub unsafe extern "C" fn Java_io_github_example_JavaJNI_plus(env: JNIEnv, this: jobject, term: jint) {
    todo!()
}

```

`NativeLoader` 使用环境变量 `RUN_RUST_TARGET` 指定正确的运行时 rust target，比如在作者 x86_64 windows 10机器上

```shell
RUN_RUST_TARGET="x86_64-pc-windows-msvc"
```

如果不指定，`NativeLoader` 将会推测可能的 target，这可能导致不能加载正确的库文件。

## rust jni 方法命名规则

本插件没有使用 `javah` 生成 C 头文件，因为对应的 jni 方法命名规则非常简单：

1. 方法名使用 Java_ 前缀。
2. 接下来使用 native 方法所在类的包名，并且将点号转换成下划线。
3. 接下来使用类名，如果是 scala object 对象，使用 object 名称 + `_00024` 。
4. 接下来是 native 方法名。
