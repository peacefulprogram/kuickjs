package io.github.peacefulprogram.kuickjs

import kotlinx.cinterop.*
import platform.posix.free
import platform.posix.int64_tVar
import quickjs.*
import quickjs.JSContext

fun <R> ULong.use(context: CPointer<JSContext>?, block: (ULong) -> R): R {
    try {
        return block(this)
    } finally {
        JS_FreeValue(context, this)
    }
}

internal actual fun initializeJsFunctionBridge(
    context: Long,
    bridgeFunctionName: String,
    callbackRef: StableRef<JsFunctionBridgeCallback>
) {
    val ctx = context.toJSContext()
    JS_SetRuntimeOpaque(JS_GetRuntime(ctx), callbackRef.asCPointer())
    JS_GetGlobalObject(ctx).use(ctx) { global ->
        val ptr = callbackRef.asCPointer().toLong()
        val data = JS_NewBigInt64(ctx, ptr)
        memScoped {
            val callbackData = allocArray<JSValueVar>(1)
            callbackData[0] = data
            val `fun` = JS_NewCFunctionData(ctx, staticCFunction(::jscFunction), 0, 0, 1, callbackData)
            JS_SetPropertyStr(ctx, global, bridgeFunctionName, `fun`)
        }
        JS_FreeValue(ctx, data)
    }
}


private fun jscFunction(
    ctx: CPointer<JSContext>?,
    thisObj: JSValue,
    argc: Int,
    argv: CPointer<JSValueVar>?,
    magic: Int,
    funcData: CPointer<JSValueVar>?
): JSValue {
    val callback = memScoped {
        val ptr = alloc<int64_tVar>()
        JS_ToBigInt64(ctx, ptr.ptr, funcData!!.pointed.value)
        ptr.value.toCPointer<ByteVar>()!!.asStableRef<JsFunctionBridgeCallback>()
    }.get()
    val async = JS_ToBool(ctx, argv!![1]) == 1
    return memScoped {
        val argsSize = if (async) argc + 2 else argc
        var promise: JSValue? = null
        val args = Array(argsSize) { 0L }
        for (i in 0 until argc) {
            args[i] = wrap_js_value(argv[i]).toLong()
        }
        if (async) {
            val funcs = allocArray<JSValueVar>(2)
            promise = JS_NewPromiseCapability(ctx, funcs)
            // free after async task complete
            args[argc] = wrap_js_value(funcs[0]).toLong()
            args[argc + 1] = wrap_js_value(funcs[1]).toLong()
        }
        val result = try {
            callback(ctx, args)
        } finally {
            for (i in 0 until argc) {
                free(args[i].toCPointer<JSValueHandle>())
            }
        }
        if (async) {
            promise!!
        } else {
            val ptr = result.toCPointer<JSValueHandle>()
            val jsResult = ptr!!.pointed.value
            free(ptr)
            jsResult
        }
    }
}
