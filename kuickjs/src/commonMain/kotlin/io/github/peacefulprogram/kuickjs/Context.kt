@file:OptIn(ExperimentalAtomicApi::class, ExperimentalCoroutinesApi::class)

package io.github.peacefulprogram.kuickjs

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration.Companion.milliseconds

class JSContext(val unhandledExceptionHandler: UnhandledExceptionHandler) {
    private val contextId = contextIdGenerator.incrementAndFetch()
    private val bridgeFunctionName = "_jsBridge$contextId"

    private val promiseThenHandler = "_jsPromiseThenHandler$contextId"
    private val promiseCatchHandler = "_jsPromiseCatchHandler$contextId"

    private val promiseMap = mutableMapOf<Long, CancellableContinuation<JsValue?>>()

    private val userFunctions = mutableListOf<(Array<JsValue?>) -> Any?>()

    private val userAsyncFunctions = mutableListOf<suspend (Array<JsValue?>) -> Any?>()

    private var runtime: Long = 0L
    private var context: Long = 0L

    private var initialized: Boolean = false

    private var disposed: Boolean = false

    private var nextPromiseId = 0L

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
            val thenHandlerName = "_jsInternalThenHandler$contextId"
            val catchHandlerName = "_jsInternalCatchHandler$contextId"
            defineFunction(thenHandlerName) { args ->
                val id = (args.firstOrNull() as? JsValue.JsNumber)?.value?.toLong() ?: return@defineFunction null
                val result = args.getOrNull(1)
                if (result == null) {
                    resumePromise(id, null)
                } else {
                    resumePromise(id) {
                        parseOwnedJsValue(dupValue(context, result.ptr))
                    }
                }
                null
            }
            defineFunction(catchHandlerName) { args ->
                val id = (args.firstOrNull() as? JsValue.JsNumber)?.value?.toLong() ?: return@defineFunction null
                val reason = args.getOrNull(1)
                val ex = try {
                    promiseException(reason)
                } catch (e: Throwable) {
                    e
                }
                failPromise(id, ex)
                null
            }
            val result = evalJs(
                context, """
                function $promiseThenHandler(id){
                    return function(value){
                        $thenHandlerName(id, value);
                    }
                }

                function $promiseCatchHandler(id){
                    return function(error){
                        $catchHandlerName(id, error);
                    }
                }
            """.trimIndent(), "init.js"
            )
            freeValue(context, result)
            initialized = true
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

    fun eval(code: String, fileName: String = "unknown.js"): JsValue? {
        return parseJsValue(evalJs(context, code, fileName))
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
            evalJs(context, code, "unknown.js").let { if (it != 0L) freeValue(context, it) }
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
            evalJs(context, code, "unknown.js").let { if (it != 0L) freeValue(context, it) }
            userAsyncFunctions.add(function)
        }
    }

    internal fun jsonStringify(value: JsValue.JsObject): String {
        return jsonStringify(context, value.ptr)

    }

    internal fun jsValueToString(value: JsValue): String {
        return jsValueToString(context, value.ptr) ?: ""
    }

    internal fun getArrayValue(index: Int, array: JsValue.JsArray): JsValue? {
        val ptr = getArrayValue(context, array.ptr, index)
        if (ptr == 0L) {
            return null
        }
        try {
            return parseJsValue(ptr).also { value ->
                if (value == null) {
                    freeValue(context, ptr)
                }
            }
        } catch (e: Throwable) {
            freeValue(context, ptr)
            throw e
        }
    }

     fun freeValue(value: JsValue) {
        freeValue(context, value.ptr)
    }

    private fun runPendingJob() {
        val ptr = executeJsPendingJob(runtime)
        if (ptr == 0L) {
            return
        }
        try {
            unhandledExceptionHandler.handleException(convertException(ptr))
        } catch (_: Throwable) {
        } finally {
            freeValue(context, ptr)
        }
    }

    internal suspend fun awaitPromise(ptr: Long): JsValue? {
        return runInJsThread {
            require(ptr != 0L) { "promise value is null" }
            check(jsValueIsPromise(ptr)) { "value is not a Promise" }
            val state = getPromiseState(context, ptr)
            if (state == PROMISE_STATE_FULFILLED) {
                return@runInJsThread parseOwnedJsValue(getPromiseResult(context, ptr))
            }
            if (state == PROMISE_STATE_REJECTED) {
                throw promiseExceptionFromOwnedValue(getPromiseResult(context, ptr))
            }
            val id = nextPromiseId++
            suspendCancellableCoroutine { continuation ->
                try {
                    promiseMap[id] = continuation
                    continuation.invokeOnCancellation {
                        if (scope.isActive) {
                            scope.launch {
                                promiseMap.remove(id)
                            }
                        } else {
                            promiseMap.remove(id)
                        }
                    }
                    checkJsResult(jsNewNumber(context, id.toDouble())).useValuePtr { idValue ->
                        checkJsResult(getProperty(context, ptr, "then")).useValuePtr { then ->
                            checkJsResult(getGlobalObject(context)).useValuePtr { global ->
                                checkJsResult(getProperty(context, global, promiseThenHandler)).useValuePtr { handler ->
                                    checkJsResult(
                                        callJsFunction(
                                            context,
                                            0,
                                            handler,
                                            longArrayOf(idValue)
                                        )
                                    ).useValuePtr { th ->
                                        checkJsResult(
                                            getProperty(
                                                context,
                                                global,
                                                promiseCatchHandler
                                            )
                                        ).useValuePtr { chandler ->
                                            checkJsResult(
                                                callJsFunction(
                                                    context,
                                                    0,
                                                    chandler,
                                                    longArrayOf(idValue)
                                                )
                                            ).useValuePtr { ch ->
                                                val chained = checkJsResult(
                                                    callJsFunction(context, ptr, then, longArrayOf(th, ch))
                                                )
                                                freeValue(context, chained)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    promiseMap.remove(id)
                    throw e
                }
            }
        }
    }

    private fun parseOwnedJsValue(ptr: Long): JsValue? {
        if (ptr == 0L) {
            return null
        }
        return try {
            parseJsValue(ptr).also { value ->
                if (value == null) {
                    freeValue(context, ptr)
                }
            }
        } catch (e: Throwable) {
            freeValue(context, ptr)
            throw e
        }
    }

    private fun promiseException(reason: JsValue?): JSException {
        if (reason is JsValue.JsError) {
            return JSException(message = reason.message, stack = reason.stack)
        }
        val message = reason?.let { jsValueToString(context, it.ptr) }
        return JSException(message = message ?: "Promise rejected", stack = "")
    }

    private fun promiseExceptionFromOwnedValue(ptr: Long): JSException {
        if (ptr == 0L) {
            return JSException(message = "Promise rejected", stack = "")
        }
        return try {
            if (jsValueIsError(ptr)) {
                convertException(ptr)
            } else {
                JSException(message = jsValueToString(context, ptr) ?: "Promise rejected", stack = "")
            }
        } finally {
            freeValue(context, ptr)
        }
    }

    private fun checkJsResult(ptr: Long): Long {
        if (ptr == 0L) {
            throw IllegalStateException("QuickJS returned a null value")
        }
        if (jsValueIsException(ptr)) {
            try {
                throw convertException(ptr)
            } finally {
                freeValue(context, ptr)
            }
        }
        return ptr
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun resumePromise(id: Long, value: JsValue?) {
        val continuation = promiseMap.remove(id) ?: run {
            value?.let { freeValue(context, it.ptr) }
            return
        }
        val token = continuation.tryResume(value)
        if (token != null) {
            continuation.completeResume(token)
        } else {
            value?.let { freeValue(context, it.ptr) }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun resumePromise(id: Long, valueFactory: () -> JsValue?) {
        val continuation = promiseMap.remove(id) ?: return
        try {
            val value = valueFactory()
            val token = continuation.tryResume(value)
            if (token != null) {
                continuation.completeResume(token)
            } else {
                value?.let { freeValue(context, it.ptr) }
            }
        } catch (e: Throwable) {
            val token = continuation.tryResumeWithException(e)
            if (token != null) {
                continuation.completeResume(token)
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun failPromise(id: Long, error: Throwable) {
        val continuation = promiseMap.remove(id) ?: return
        val token = continuation.tryResumeWithException(error)
        if (token != null) {
            continuation.completeResume(token)
        }
    }

    private fun parseJsValue(ptr: Long): JsValue? {
        if (ptr == 0L) {
            return null
        }
        if (jsValueIsNull(ptr) || jsValueIsUndefined(ptr)) {
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
            return JsValue.JsPromise(ptr, this)
        }
        if (jsValueIsArray(ptr)) {
            val len = getProperty(context, ptr, "length").useValuePtr {
                getNumberValue(it)
            }.toInt()
            return JsValue.JsArray(ptr = ptr, context = this, length = len)
        }
        if (jsValueIsObject(ptr) || jsValueIsModule(ptr)) {
            return JsValue.JsObject(ptr, this)
        }
//        if (jsValueIsBigInt(ptr) || jsValueIsSymbol(ptr)) {
//            return JsValue.JsRaw(ptr, this)
//        }
        if (jsValueIsUninitialized(ptr)) {
            throw IllegalStateException("cannot expose an uninitialized JS value")
        }
        throw IllegalStateException("unsupported JS value")
    }

    private fun parseJsValues(ptrs: Array<Long>): Array<JsValue?> {
        val values = ArrayList<JsValue?>(ptrs.size)
        try {
            for (ptr in ptrs) {
                val value = parseJsValue(ptr)
                values += value
                if (value == null && ptr != 0L) {
                    freeValue(context, ptr)
                }
            }
            return values.toTypedArray()
        } catch (e: Throwable) {
            values.forEach { it?.let { value -> freeValue(context, value.ptr) } }
            for (i in values.size until ptrs.size) {
                if (ptrs[i] != 0L) freeValue(context, ptrs[i])
            }
            throw e
        }
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
        if (value is JsValue) return value

        val ptr = when (value) {
            is String -> jsNewString(context, value)
            is Char -> jsNewString(context, value.toString())
            is Boolean -> jsNewBoolean(context, value)
            is Number -> jsNewNumber(context, value.toDouble())
            is UByte -> jsNewNumber(context, value.toDouble())
            is UShort -> jsNewNumber(context, value.toDouble())
            is UInt -> jsNewNumber(context, value.toDouble())
            is ULong -> jsNewNumber(context, value.toDouble())
            else -> throw IllegalArgumentException(
                "unsupported Kotlin value type: ${value::class}"
            )
        }
        if (ptr == 0L) {
            throw IllegalStateException("failed to create JS value")
        }
        return parseJsValue(ptr) ?: run {
            freeValue(context, ptr)
            throw IllegalStateException("created JS value is not representable")
        }
    }

    fun callJsFunction(function: JsValue.JsFunction, thisObj: JsValue?, args: Array<JsValue?>): JsValue? {
        val argPtrs = LongArray(args.size) { args[it]?.ptr ?: 0L }
        val result = callJsFunction(context, thisObj?.ptr ?: 0L, function.ptr, argPtrs)
        return parseOwnedJsValue(result)
    }


    companion object {

        init {
            JsPlatform.loadLibrary()
        }

        private val contextMapLock = Mutex()

        private val contextMap = mutableMapOf<Long, JSContext>()

        private val contextIdGenerator = AtomicLong(0)

        const val PROMISE_STATE_PENDING = 0
        const val PROMISE_STATE_FULFILLED = 1
        const val PROMISE_STATE_REJECTED = 2

        internal fun onAsyncJsFunctionCall(contextId: Long, functionIndex: Int, args: Array<Long>): Long {
            val ctx = contextMap[contextId] ?: return 0L
            var parseStarted = false
            var scheduled = false
            var actualArgs: Array<JsValue?>? = null
            try {
                val f =
                    ctx.userAsyncFunctions.getOrNull(functionIndex) ?: throw IllegalStateException("function is null")
                if (args.size < 2) {
                    throw IllegalArgumentException("async bridge requires resolve and reject")
                }
                val actualArgPtrs = args.slice(0 until args.size - 2).toTypedArray()
                parseStarted = true
                actualArgs = ctx.parseJsValues(actualArgPtrs)
                val callbackArgs = actualArgs ?: error("failed to parse async function arguments")
                val resolve = args[args.size - 2]
                val reject = args[args.size - 1]
                ctx.scope.launch(ctx.dispatcher) {
                    try {
                        val value = f.invoke(callbackArgs)
                        val ownedResult = if (value is JsValue) {
                            dupValue(ctx.context, value.ptr)
                        } else if (value == null || value === Unit) {
                            0L
                        } else {
                            ctx.convertValueToJsValue(value).ptr
                        }
                        try {
                            val callResult = callJsFunction(
                                ctx.context,
                                0L,
                                resolve,
                                longArrayOf(ownedResult)
                            )
                            if (callResult != 0L) {
                                freeValue(ctx.context, callResult)
                            }
                        } finally {
                            if (ownedResult != 0L) {
                                freeValue(ctx.context, ownedResult)
                            }
                        }
                    } catch (e: Throwable) {
                        val err = jsNewError(ctx.context, e.message ?: "")
                        try {
                            val callResult = callJsFunction(ctx.context, 0L, reject, longArrayOf(err))
                            if (callResult != 0L) {
                                freeValue(ctx.context, callResult)
                            }
                        } finally {
                            freeValue(ctx.context, err)
                        }
                    } finally {
                        callbackArgs.forEach { it?.let { value -> freeValue(ctx.context, value.ptr) } }
                        freeValue(ctx.context, resolve)
                        freeValue(ctx.context, reject)
                    }
                }
                scheduled = true
                return 0
            } catch (e: Throwable) {
                if (!scheduled) {
                    if (!parseStarted) {
                        args.dropLast(2).forEach { if (it != 0L) freeValue(ctx.context, it) }
                    }
                    actualArgs?.forEach { it?.let { value -> freeValue(ctx.context, value.ptr) } }
                    args.takeLast(2).forEach { if (it != 0L) freeValue(ctx.context, it) }
                }
                return jsNewError(ctx.context, e.message ?: "")
            }
        }

        internal fun onJsFunctionCall(contextId: Long, functionIndex: Int, args: Array<Long>): Long {
            try {
                val ctx = contextMap[contextId] ?: throw IllegalStateException("context is null")
                val f =
                    ctx.userFunctions.getOrNull(functionIndex) ?: throw IllegalStateException("function is null")
                val actualArgs = ctx.parseJsValues(args)
                try {
                    val result = f.invoke(actualArgs)
                    if (result == null || result === Unit) {
                        return 0L
                    }
                    if (result is JsValue) {
                        return dupValue(ctx.context, result.ptr)
                    }
                    return ctx.convertValueToJsValue(result).ptr
                } finally {
                    actualArgs.forEach { it?.let { value -> freeValue(ctx.context, value.ptr) } }
                }
            } catch (e: Throwable) {
                val ctx = contextMap[contextId]
                return ctx?.let { jsNewError(it.context, e.message ?: "") } ?: 0L
            }
        }

    }
}

fun interface UnhandledExceptionHandler {
    fun handleException(e: JSException)
}
