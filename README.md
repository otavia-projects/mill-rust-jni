<div align=center>
</div>
<h1 align=center>mill-rust-jni</h1>

<p align=center ><b>A mill plugin for build jni code power by rust!</b></p>

![GitHub](https://img.shields.io/github/license/otavia-projects/mill-rust-jni)

<hr>

Language: [简体中文](./README.zh_cn.md)

<hr>

## Setup
In your `build.sc`, import the plugin with:
```scala
import $ivy.`io.otavia::mill-rust:{version}`
import io.otavia.jni.plugin.RustJniModule
```
then define a native jni module:
```scala
object libjni extends RustJniModule {
  
}
```

then, generate template project:
```shell
mill libjni.nativeInit
```

