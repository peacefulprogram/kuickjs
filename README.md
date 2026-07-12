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

## Gradle Dependency

Add the Maven Central repository and the `kuickjs` dependency to your Kotlin Multiplatform project:

```kotlin
repositories {
    mavenCentral()
}

commonMain.dependencies {
    implementation("io.github.peacefulprogram:kuickjs:0.0.1")
}
```

With a Gradle Version Catalog:

```toml
[versions]
kuickjs = "0.0.1"

[libraries]
kuickjs = { module = "io.github.peacefulprogram:kuickjs", version.ref = "kuickjs" }
```

Then declare it in your source set:

```kotlin
commonMain.dependencies {
    implementation(libs.kuickjs)
}
```

## Usage

All JavaScript-related operations must run on the context's JavaScript thread. Use `runInJsThread` for context initialization, function registration, JavaScript evaluation, property access, function calls, and value conversion.

`JsValue` instances own a native QuickJS value. Every value returned by `eval`, `getProperty`, array access, `invoke`, or `convertToJsValue` must be released. Prefer `use` for synchronous operations; it releases the value automatically even when the block throws.

```kotlin
import io.github.peacefulprogram.kuickjs.JSContext
import io.github.peacefulprogram.kuickjs.JsValue
import io.github.peacefulprogram.kuickjs.use
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val context = JSContext { exception ->
        exception.printStackTrace()
    }

    try {
        context.runInJsThread {
            context.initialize()

            context.defineFunction("add") { args ->
                val left = (args[0] as JsValue.JsNumber).value
                val right = (args[1] as JsValue.JsNumber).value
                left + right
            }

            context.eval("add(1, 2)")?.use { result ->
                println((result as JsValue.JsNumber).value)
            }

            context.eval("({ name: 'kuickjs', version: 1 })")?.use { value ->
                val objectValue = value as JsValue.JsObject
                println(objectValue.toJsonString())
                println(objectValue.keys())
            }
        }
    } finally {
        context.runInJsThread {
            context.dispose()
        }
    }
}
```

Values returned from nested operations must also be released. For example, both the property value and the object that owns it are managed independently:

```kotlin
context.runInJsThread {
    context.eval("({ answer: 42 })")?.use { objectValue ->
        (objectValue as JsValue.JsObject).getProperty("answer")?.use { answer ->
            println(answer)
        }
    }
}
```

Functions can also be defined as asynchronous Kotlin functions. They are exposed to JavaScript as promise-returning functions. The Promise itself must also be released after awaiting it:

```kotlin
val promise = context.runInJsThread {
    context.eval("Promise.resolve(42)") as JsValue.JsPromise
}

try {
    promise.await()?.use { value ->
        println(value)
    }
} finally {
    context.runInJsThread {
        promise.free()
    }
}
```

Do not call QuickJS operations from an arbitrary thread or keep a `JsValue` after its context has been disposed. A value must be freed before its context is disposed.

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
