@file:OptIn(ExperimentalAtomicApi::class, ExperimentalCoroutinesApi::class)

package io.github.peacefulprogram.kuickjs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class JSContext(val unhandledExceptionHandler: UnhandledExceptionHandler) {
    private val contextId = contextIdGenerator.incrementAndFetch()
    private val bridgeFunctionName = "_jsBridge$contextId"

    private val userFunctions = mutableListOf<(Array<JsValue?>) -> Any?>()

    private val userAsyncFunctions = mutableListOf<suspend (Array<JsValue?>) -> Any?>()

    private var runtime: Long = 0L
    private var context: Long = 0L

    private var initialized: Boolean = false

    private var disposed: Boolean = false

    @OptIn(DelicateCoroutinesApi::class)
    val dispatcher = newFixedThreadPoolContext(1, "JSContext$contextId")

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    suspend fun <R> runInJsThread(block: suspend () -> R): R {
        return withContext(dispatcher) {
            block()
        }
    }

    suspend fun initialize() {
        runInJsThread {
            if (initialized) {
                return@runInJsThread
            }
            runtime = newRuntime()
            context = newContext(runtime)
            if (runtime == 0L || context == 0L) {
                JsPlatform.freeContext(context)
                freeRuntime(runtime)
                throw RuntimeException("failed to initialize context")
            }
            JsPlatform.initializeJsFunctionBridge(context, contextId, bridgeFunctionName)
            scope.launch {
                while (isActive) {
                    while (runtimeHasPendingJob(runtime)) {
                        runPendingJob()
                    }
                    delay(15.milliseconds)
                }
            }
            contextMapLock.withLock {
                contextMap[contextId] = this
            }
        }
    }

    suspend fun defineFunction(name: String, function: (Array<JsValue?>) -> Any?) {
        runInJsThread {
            val idx = userFunctions.size
            val code = """
                function $name(){
                    const args = [$idx, false];
                    for(let i = 0; i < arguments.length; i++) {
                        args.push(arguments[i]);
                    }
                    return $bridgeFunctionName.apply(null, args);
                }
            """.trimIndent()
            evalJs(context, code, "unknown.js")
            userFunctions.add(function)
        }
    }

    suspend fun defineAsyncFunction(name: String, function: suspend (Array<JsValue?>) -> Any?) {
        runInJsThread {
            val idx = userAsyncFunctions.size
            val code = """
                function $name(){
                    const args = [$idx, true];
                    for(let i = 0; i < arguments.length; i++) {
                        args.push(arguments[i]);
                    }
                    return $bridgeFunctionName.apply(null, args);
                }
            """.trimIndent()
            evalJs(context, code, "unknown.js")
            userAsyncFunctions.add(function)
        }
    }

    internal suspend fun stringify(value: JsValue.JsObject): String {
        return runInJsThread {
            jsonStringify(context, value.ptr)
        }
    }

    internal suspend fun getArrayValue(index: Int, array: JsValue.JsArray): JsValue? {
        return runInJsThread {
            parseJsValue(getArrayValue(context, array.ptr, index))
        }
    }

    suspend fun freeValue(value: JsValue) {
        runInJsThread {
            freeValue(context, value.ptr)
        }
    }

    private fun runPendingJob() {
        val ptr = executeJsPendingJob(runtime)
        if (ptr == 0L) {
            return
        }
        try {
            unhandledExceptionHandler.handleException(convertException(ptr))
        } catch (_: Throwable) {
        }
    }

    private fun parseJsValue(ptr: Long): JsValue? {
        if (ptr == 0L) {
            return null
        }
        if (jsValueIsNull(ptr) || jsValueIsUndefined(ptr)) {
            freeValue(context, ptr)
            return null
        }
        if (jsValueIsException(ptr)) {
            throw convertException(ptr)
        }
        if (jsValueIsError(ptr)) {
            val e = convertException(ptr)
            return JsValue.JsError(
                ptr = ptr,
                context = this,
                message = e.message ?: "",
                stack = e.stack
            )
        }
        if (jsValueIsNumber(ptr)) {
            return JsValue.JsNumber(ptr, this, getNumberValue(ptr))
        }
        if (jsValueIsString(ptr)) {
            return JsValue.JsString(ptr, this)
        }
        if (jsValueIsBool(ptr)) {
            return JsValue.JsBoolean(ptr = ptr, context = this, value = getBoolValue(ptr))
        }
        if (jsValueIsFunction(context, ptr) || jsValueIsAsyncFunction(ptr)) {
            return JsValue.JsFunction(ptr, this)
        }
        if (jsValueIsPromise(ptr)) {
            val holder = PromiseHolder(
                ptr = ptr,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                state = PromiseState.Pending,
                handler = null
            )
            return JsValue.JsPromise(ptr, this, holder = holder)
        }
        if (jsValueIsArray(ptr)) {
            val len = getProperty(context, ptr, "length").useValuePtr {
                getNumberValue(it)
            }.toInt()
            return JsValue.JsArray(ptr = ptr, context = this, length = len)
        }
        TODO()
    }

    private fun convertException(errorPtr: Long): JSException {
        val message = getProperty(context, errorPtr, "message").useValuePtr {
            jsValueToString(context, it)
        }
        val stack = getProperty(context, errorPtr, "stack").useValuePtr {
            jsValueToString(context, it)
        }
        return JSException(message = message ?: "", stack = stack ?: "")
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <R> Long.useValuePtr(block: (Long) -> R): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        try {
            return block(this)
        } finally {
            freeValue(context, this)
        }
    }

    private fun convertValueToJsValue(value: Any): JsValue {
        if (value is JsValue) {
            return value
        }
        TODO()
    }


    companion object {

        private val contextMapLock = Mutex()

        private val contextMap = mutableMapOf<Long, JSContext>()

        private val contextIdGenerator = AtomicLong(0)

        internal fun onAsyncJsFunctionCall(contextId: Long, functionIndex: Int, args: Array<Long>): Long {
            try {
                val ctx = contextMap[contextId] ?: throw IllegalStateException("context is null")
                val f =
                    ctx.userAsyncFunctions.getOrNull(functionIndex) ?: throw IllegalStateException("function is null")
                val actualArgs = args.slice(0 until args.size - 2)
                    .map { ctx.parseJsValue(it) }
                    .toTypedArray()
                val resolve = args[actualArgs.size - 2]
                val reject = args[actualArgs.size - 1]
                ctx.scope.launch(ctx.dispatcher) {
                    try {
                        val value = f.invoke(actualArgs)
                        var free = false
                        val resolveArgs = if (value is JsValue) {
                            arrayOf(value.ptr)
                        } else if (value == null) {
                            arrayOf(0L)
                        } else {
                            free = true
                            arrayOf(ctx.convertValueToJsValue(value).ptr)
                        }
                        callJsFunction(ctx.context, 0L, resolve, resolveArgs)
                        if (free) {
                            args.forEach { freeValue(ctx.context, it) }
                        }
                    } catch (e: Throwable) {
                        val err = jsNewError(contextId, e.message ?: "")
                        try {
                            callJsFunction(ctx.context, 0L, reject, arrayOf(err))
                        } finally {
                            freeValue(ctx.context, err)
                        }
                    } finally {
                        freeValue(ctx.context, resolve)
                        freeValue(ctx.context, reject)
                    }
                }
                return 0
            } catch (e: Throwable) {
                return jsNewError(contextId, e.message ?: "")
            }
        }

        internal fun onJsFunctionCall(contextId: Long, functionIndex: Int, args: Array<Long>): Long {
            try {
                val ctx = contextMap[contextId] ?: throw IllegalStateException("context is null")
                val f =
                    ctx.userFunctions.getOrNull(functionIndex) ?: throw IllegalStateException("function is null")
                val actualArgs = args.map { ctx.parseJsValue(it) }
                    .toTypedArray()
                val result = f.invoke(actualArgs)
                if (result == null) {
                    return 0L
                }
                if (result is JsValue) {
                    return result.ptr
                }
                return ctx.convertValueToJsValue(result).ptr
            } catch (e: Throwable) {
                return jsNewError(contextId, e.message ?: "")
            }
        }

    }
}

data class PromiseHolder(
    val ptr: Long,
    val timestamp: Long,
    val state: PromiseState,
    val handler: (suspend (JsValue?) -> Unit)?,
)


sealed class PromiseState {
    data object Pending : PromiseState()
    data class Fulfilled(val value: JsValue?) : PromiseState()
    data class Rejected(val exception: JSException) : PromiseState()
}

fun interface UnhandledExceptionHandler {
    fun handleException(e: JSException)
}