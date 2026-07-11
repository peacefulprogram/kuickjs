import io.github.peacefulprogram.kuickjs.JSContext
import io.github.peacefulprogram.kuickjs.JsValue
import io.github.peacefulprogram.kuickjs.use
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

fun jsExample() {
    val ctx = JSContext {
        it.printStackTrace()
    }
    runBlocking {
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
        ctx.runInJsThread {
            ctx.eval(
                """
                var console = {
                    log(){
                        _logMessage.apply(null, arguments)
                    }
                }
            """.trimIndent()
            )
            try {
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
                mytimes(1, 2)
            """.trimIndent()
                )?.use {
                    println(it)
                }
                println("end")
                val start = Clock.System.now().toEpochMilliseconds()
                ctx.eval("""
                    setTimeout(function() {
                        console.log.apply(console, arguments)
                    }, 3000, 1, 'hello', true)
                """.trimIndent())
                    ?.use {
                        (it as JsValue.JsPromise).await()?.free()
                    }
                println("cost ${Clock.System.now().toEpochMilliseconds() - start}ms")
                delay(10.minutes)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
