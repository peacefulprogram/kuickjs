# kuickjs

`kuickjs` is a Kotlin Multiplatform library for embedding the [QuickJS-ng](https://github.com/quickjs-ng/quickjs) JavaScript engine in Kotlin applications.

It provides a Kotlin API for creating JavaScript runtimes and contexts, evaluating JavaScript code, exchanging values between Kotlin and JavaScript, defining Kotlin-backed JavaScript functions, and working with JavaScript promises.

## Features

- Kotlin Multiplatform support
- JavaScript evaluation from Kotlin
- Kotlin wrappers for common JavaScript values
- Kotlin-backed synchronous and asynchronous JavaScript functions
- Promise awaiting from Kotlin coroutines
- Native targets using QuickJS-ng
- JVM integration through JNI
- Support for 32-bit and 64-bit JavaScript value representations

## Supported Targets

The project is configured for JVM, Android Native, Linux, macOS, Windows MinGW, iOS, tvOS, and watchOS targets where supported by Kotlin Multiplatform and the bundled native dependencies.

## Usage

```kotlin
import io.github.peacefulprogram.kuickjs.JSContext
import io.github.peacefulprogram.kuickjs.JsValue
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val context = JSContext { exception ->
        exception.printStackTrace()
    }

    context.initialize()

    context.defineFunction("add") { args ->
        val left = (args[0] as JsValue.JsNumber).value
        val right = (args[1] as JsValue.JsNumber).value
        left + right
    }

    context.runInJsThread {
        val result = context.eval("add(1, 2)")
        println((result as JsValue.JsNumber).value)
        result.free()
    }
}
```

Functions can also be defined as asynchronous Kotlin functions. They are exposed to JavaScript as promise-returning functions and can be awaited from Kotlin using `JsValue.JsPromise.await()`.

## Project Structure

- `kuickjs`: the Kotlin Multiplatform library
- `examples`: example applications and usage demonstrations

## Building

Use the Gradle wrapper to build the project:

```bash
./gradlew build
```

Native targets require a compatible Kotlin/Native toolchain and platform SDK. JVM usage also requires the generated native library to be available through the configured JNI packaging.

## Dependencies and Credits

kuickjs is built on the following projects:

- [QuickJS-ng](https://github.com/quickjs-ng/quickjs): the embedded JavaScript engine
- [Kotlin-JNI](https://github.com/dshatz/Kotlin-JNI): Kotlin/Native and JNI integration

Please review the licenses of these projects and the licenses of their bundled dependencies when distributing applications built with kuickjs.

## Status

kuickjs is under active development. APIs and platform support may change as the native bridge and JavaScript value handling continue to evolve.
