package io.github.peacefulprogram.kuickjs

import kotlinx.cinterop.*
import platform.posix.free
import platform.posix.int64_tVar
import quickjs.*
import quickjs.JSContext

fun <R> CValue<JSValue>.use(context: CPointer<JSContext>?, block: (CValue<JSValue>) -> R): R {
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
            val callbackData = allocArray<JSValue>(1)
            data.place(callbackData[0].ptr)
            val `fun` = JS_NewCFunctionData(ctx, staticCFunction(::jscFunction), 0, 0, 1, callbackData)
            JS_SetPropertyStr(ctx, global, bridgeFunctionName, `fun`)
        }
        JS_FreeValue(ctx, data)
    }
}


private fun jscFunction(
    ctx: CPointer<JSContext>?,
    thisObj: CValue<JSValue>,
    argc: Int,
    argv: CPointer<JSValue>?,
    magic: Int,
    funcData: CPointer<JSValue>?
): CValue<JSValue> {
    if (argc < 2 || argv == null || funcData == null) {
        return JS_EXCEPTION_Kt()
    }

    val callback = memScoped {
        val ptr = alloc<int64_tVar>()
        val dataValue = funcData.pointed.readValue()
        if (JS_ToBigInt64(ctx, ptr.ptr, dataValue) != 0) {
            null
        } else {
            ptr.value.toCPointer<ByteVar>()!!
                .asStableRef<JsFunctionBridgeCallback>()
                .get()
        }
    } ?: return JS_EXCEPTION_Kt()

    val asyncValue = JS_ToBool(ctx, argv[1].readValue())
    if (asyncValue < 0) {
        return JS_EXCEPTION_Kt()
    }
    val async = asyncValue != 0
    return memScoped {
        val argsSize = if (async) argc + 2 else argc
        var promise: CValue<JSValue>? = null
        val args = Array(argsSize) { 0L }
        for (i in 0 until argc) {
            args[i] = wrap_js_value(argv[i].readValue()).toLong()
        }
        if (async) {
            val funcs = allocArray<JSValue>(2)
            promise = JS_NewPromiseCapability(ctx, funcs)
            // free after async task complete
            args[argc] = wrap_js_value(funcs[0].readValue()).toLong()
            args[argc + 1] = wrap_js_value(funcs[1].readValue()).toLong()
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
            val ptr = result.toCPointer<JSValueHandle>() ?: return@memScoped JS_EXCEPTION_Kt()
            val jsResult = ptr.pointed.value.readValue()
            free(ptr)
            jsResult
        }
    }
}
