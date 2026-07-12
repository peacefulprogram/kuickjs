import io.github.peacefulprogram.kuickjs.JSContext
import io.github.peacefulprogram.kuickjs.JsValue
import io.github.peacefulprogram.kuickjs.use
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

inline fun <reified T> Any.`as`():T{
    return this as T
}
inline fun <reified T> Any.asSafely():T?{
    return this as? T
}

fun jsExample() {
    val ctx = JSContext {
        it.printStackTrace()
    }
    runBlocking {
        ctx.runInJsThread {
            ctx.initialize()
            ctx.defineFunction("myplus") { args ->
                (args[0] as JsValue.JsNumber).value + (args[1] as JsValue.JsNumber).value
            }
            ctx.defineFunction("mytimes") { args ->
                (args[0] as JsValue.JsNumber).value * (args[1] as JsValue.JsNumber).value
            }
            ctx.defineFunction("_logMessage") { args ->
                println(args.joinToString(separator = " ") { it.toString() })
            }
            ctx.defineAsyncFunction("setTimeout") { args ->
                val timeout = (args.getOrNull(1) as? JsValue.JsNumber)?.value?.toLong() ?: 0L
                val callback = args.first() as JsValue.JsFunction
                val funArgs = if (args.size > 2) args.slice(2 until args.size).toTypedArray() else args
                if (timeout <= 0) {
                    callback.invoke(funArgs)?.free()
                } else {
                    delay(timeout.milliseconds)
                    callback.invoke(funArgs)?.free()
                }
            }
            ctx.defineAsyncFunction("errFunction") { args ->
                delay(1.seconds)
                throw RuntimeException("this is error")
            }
            ctx.eval(
                $$"""
                function mysum(arr) {
                    let s = 0;
                    console.log(`is array: ${Array.isArray(arr)}`);
                    console.log(`array len: ${arr.length}`);
                    for(const i of arr) {
                        console.log(`i=${i}`);
                        s += i;
                    }
                    return s;
                }
            """.trimIndent())
            ctx.eval(
                """
                var console = {
                    log(){
                        _logMessage.apply(null, arguments)
                    }
                }
            """.trimIndent()
            )
            println("start")
            ctx.eval(
                """
                myplus(1, 2)
            """.trimIndent()
            )?.use {
                println(it)
            }
            ctx.eval(
                """
                mytimes(3, 2)
            """.trimIndent()
            )?.use {
                println(it)
            }
            println("end")
        }

        val start = Clock.System.now().toEpochMilliseconds()
        val timeoutPromise = ctx.runInJsThread {
            ctx.eval(
                """
                    setTimeout(function() {
                        console.log.apply(console, arguments)
                    }, 3000, 1, 'hello', true)
                """.trimIndent()
            )
        }
        try {
            (timeoutPromise as JsValue.JsPromise).await()?.let { result ->
                ctx.runInJsThread { result.free() }
            }
        } finally {
            ctx.runInJsThread { timeoutPromise?.free() }
        }
        println("cost ${Clock.System.now().toEpochMilliseconds() - start}ms")

        val errorPromise = ctx.runInJsThread { ctx.eval("errFunction()") }
        try {
            (errorPromise as JsValue.JsPromise).await()?.let { result ->
                ctx.runInJsThread { result.free() }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            ctx.runInJsThread { errorPromise?.free() }
        }
        println(ctx.runInJsThread { ctx.getGlobalObject().use { it.keys() } })
        ctx.runInJsThread {
            val numbers = 1..10
            ctx.getGlobalObject().use { global->
                ctx.convertToJsValue(numbers).use { arg->
                    global.getProperty("mysum")?.use { f->
                        f.`as`<JsValue.JsFunction>().invoke(arrayOf(arg))
                            ?.use { result->
                                println("sum result: $result")
                            }
                    }

                }
            }
        }
        ctx.runInJsThread { ctx.dispose() }
    }
}
