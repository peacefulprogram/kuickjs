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
            val value = if (i < 2) argv[i].readValue() else JS_DupValue(ctx, argv[i].readValue())
            args[i] = wrap_js_value(value).toLong()
        }
        if (async) {
            val funcs = allocArray<JSValue>(2)
            promise = JS_NewPromiseCapability(ctx, funcs)
            if (JS_IsException(promise!!)) {
                for (i in 2 until argc) {
                    val handle = args[i].toCPointer<JSValueHandle>()!!
                    JS_FreeValue(ctx, handle.pointed.value.readValue())
                    free(handle)
                }
                free(args[0].toCPointer<JSValueHandle>())
                free(args[1].toCPointer<JSValueHandle>())
                return@memScoped JS_EXCEPTION_Kt()
            }
            args[argc] = wrap_js_value(funcs[0].readValue()).toLong()
            args[argc + 1] = wrap_js_value(funcs[1].readValue()).toLong()
        }
        val result = try {
            callback(ctx, args)
        } finally {
            // The bridge callback owns user arguments and releases them after parsing.
            free(args[0].toCPointer<JSValueHandle>())
            free(args[1].toCPointer<JSValueHandle>())
        }
        if (async) {
            if (result == 0L) {
                promise!!
            } else {
                val error = result.toCPointer<JSValueHandle>()!!
                val jsError = error.pointed.value.readValue()
                free(error)
                JS_FreeValue(ctx, promise!!)
                jsError
            }
        } else {
            if (result == 0L) {
                JS_UNDEFINED_Kt()
            } else {
                val ptr = result.toCPointer<JSValueHandle>()!!
                val jsResult = ptr.pointed.value.readValue()
                free(ptr)
                jsResult
            }
        }
    }
}
