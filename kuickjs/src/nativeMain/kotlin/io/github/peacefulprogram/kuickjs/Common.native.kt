package io.github.peacefulprogram.kuickjs

import com.dshatz.kni.annotations.JniCall
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toLong
import platform.posix.free
import kotlin.native.concurrent.Worker
import quickjs.JSContext
import quickjs.JSRuntime
import quickjs.JSValueHandle
import quickjs.JS_Call_Kt
import quickjs.JS_DupValue_Kt
import quickjs.JS_Eval_Kt
import quickjs.JS_ExecutePendingJob
import quickjs.JS_FreeCString
import quickjs.JS_FreeContext
import quickjs.JS_FreeRuntime
import quickjs.JS_FreeValue_Kt
import quickjs.JS_GetException_Kt
import quickjs.JS_GetGlobalObject_Kt
import quickjs.JS_GetPropertyStr_Kt
import quickjs.JS_GetPropertyUint32_Kt
import quickjs.JS_IsArray_Kt
import quickjs.JS_IsAsyncFunction_Kt
import quickjs.JS_IsBigInt_Kt
import quickjs.JS_IsBool_Kt
import quickjs.JS_IsConstructor_Kt
import quickjs.JS_IsDataView_Kt
import quickjs.JS_IsDate_Kt
import quickjs.JS_IsError_Kt
import quickjs.JS_IsException_Kt
import quickjs.JS_IsFunction_Kt
import quickjs.JS_IsJobPending
import quickjs.JS_IsMap_Kt
import quickjs.JS_IsModule_Kt
import quickjs.JS_IsNull_Kt
import quickjs.JS_IsNumber_Kt
import quickjs.JS_IsObject_Kt
import quickjs.JS_IsPromise_Kt
import quickjs.JS_IsRegExp_Kt
import quickjs.JS_IsSet_Kt
import quickjs.JS_IsString_Kt
import quickjs.JS_IsSymbol_Kt
import quickjs.JS_IsUndefined_Kt
import quickjs.JS_IsUninitialized_Kt
import quickjs.JS_IsWeakMap_Kt
import quickjs.JS_IsWeakRef_Kt
import quickjs.JS_IsWeakSet_Kt
import quickjs.JS_JSONStringify_Kt
import quickjs.JS_NULL_Kt
import quickjs.JS_NewArray_Kt
import quickjs.JS_NewContext
import quickjs.JS_NewError_Kt
import quickjs.JS_NewBoolean_Kt
import quickjs.JS_NewNumber_Kt
import quickjs.JS_NewObject_Kt
import quickjs.JS_NewRuntime
import quickjs.JS_NewString_Kt
import quickjs.JS_NewUint8ArrayCopy_Kt
import quickjs.JS_ParseJSON_Kt
import quickjs.JS_PromiseResult_Kt
import quickjs.JS_PromiseState_Kt
import quickjs.JS_SetPropertyStr_Kt
import quickjs.JS_TAG_INT
import quickjs.JS_ToCString_Kt
import quickjs.JS_ToString_Kt
import quickjs.JS_VALUE_GET_BOOL_Kt
import quickjs.JS_VALUE_GET_FLOAT64_Kt
import quickjs.JS_VALUE_GET_INT_Kt
import quickjs.JS_VALUE_GET_TAG_Kt

internal typealias JsFunctionBridgeCallback = (CPointer<JSContext>?, Array<Long>) -> Long

typealias JSValuePtr = CPointer<JSValueHandle>

fun Long.toJSRuntime(): CPointer<JSRuntime>? {
    if (this == 0L) return null
    return toCPointer()
}

fun Long.toJSContext(): CPointer<JSContext>? {
    if (this == 0L) return null
    return toCPointer()
}

private fun Long.toJsValue(): JSValuePtr? {
    if (this == 0L) return null
    return toCPointer()
}

fun <R> JSValuePtr.use(ctx: CPointer<JSContext>, block: (JSValuePtr) -> R): R {
    try {
        return block(this)
    } finally {
        JS_FreeValue_Kt(ctx, this)
    }
}

fun JSValuePtr.toKotlinString(context: CPointer<JSContext>): String {
    val cs = JS_ToCString_Kt(context, this)
    val txt = cs?.toKString()
    JS_FreeCString(context, cs)
    return txt ?: ""
}

@JniCall
actual fun newRuntime(): Long {
    return JS_NewRuntime().toLong()
}

@JniCall
actual fun newContext(rt: Long): Long {
    return JS_NewContext(rt.toJSRuntime()).toLong()
}

@JniCall
actual fun evalJs(context: Long, code: String, fileName: String): Long {
    val ccode = code.cstr
    return JS_Eval_Kt(context.toJSContext(), ccode, ccode.size - 1, fileName.cstr, 0).toLong()
}


@JniCall
internal actual fun freeRuntime(runtime: Long) {
    runtime.toJSRuntime()?.let {
        JS_FreeRuntime(it)
    }
}

@JniCall
internal actual fun getProperty(context: Long, obj: Long, name: String): Long {
    val ctx = context.toJSContext() ?: return 0
    val v = obj.toJsValue() ?: return 0
    return JS_GetPropertyStr_Kt(ctx, v, name).toLong()
}

@JniCall
internal actual fun getException(context: Long): Long {
    return JS_GetException_Kt(context.toJSContext()).toLong()
}

@JniCall
internal actual fun jsValueToString(context: Long, value: Long): String {
    val ctx = context.toJSContext() ?: return ""
    val v = value.toJsValue() ?: return ""
    if (JS_IsNull_Kt(v) || JS_IsUndefined_Kt(v)) {
        return ""
    }
    return JS_ToString_Kt(ctx, v)?.use(ctx) {
        it.toKotlinString(ctx)
    }?:""
}

@JniCall
internal actual fun getJsValueTag(value: Long): Int {
    return JS_VALUE_GET_TAG_Kt(value.toJsValue())
}

@JniCall
internal actual fun getNumberValue(value: Long): Double {
    if (getJsValueTag(value) == JS_TAG_INT) {
        return JS_VALUE_GET_INT_Kt(value.toJsValue()).toDouble()
    }
    return JS_VALUE_GET_FLOAT64_Kt(value.toJsValue())
}

@JniCall
internal actual fun getBoolValue(value: Long): Boolean {
    return JS_VALUE_GET_BOOL_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsonStringify(context: Long, value: Long): String {
    val ctx = context.toJSContext() ?: return ""
    return JS_JSONStringify_Kt(ctx, value.toJsValue())
        ?.use(ctx) {
            it.toKotlinString(ctx)
        }
        ?: ""
}

@JniCall
internal actual fun parseJson(context: Long, json: String): Long {
    val cs = json.cstr
    return JS_ParseJSON_Kt(context.toJSContext(), cs, cs.size - 1, null).toLong()
}

@JniCall
internal actual fun freeValue(context: Long, value: Long) {
    JS_FreeValue_Kt(context.toJSContext(), value.toJsValue())
}

@JniCall
internal actual fun dupValue(context: Long, value: Long): Long {
    val ctx = context.toJSContext() ?: return 0L
    val v = value.toJsValue() ?: return 0L
    return JS_DupValue_Kt(ctx, v).toLong()
}

@JniCall
internal actual fun getArrayValue(context: Long, array: Long, index: Int): Long {
    return JS_GetPropertyUint32_Kt(context.toJSContext(), array.toJsValue(), index.toUInt()).toLong()
}


@JniCall
internal actual fun jsValueIsNumber(value: Long): Boolean {
    return JS_IsNumber_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsBigInt(value: Long): Boolean {
    return JS_IsBigInt_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsBool(value: Long): Boolean {
    return JS_IsBool_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsNull(value: Long): Boolean {
    return JS_IsNull_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsUndefined(value: Long): Boolean {
    return JS_IsUndefined_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsError(value: Long): Boolean {
    return JS_IsError_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsException(value: Long): Boolean {
    return JS_IsException_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsUninitialized(value: Long): Boolean {
    return JS_IsUninitialized_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsString(value: Long): Boolean {
    return JS_IsString_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsSymbol(value: Long): Boolean {
    return JS_IsSymbol_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsObject(value: Long): Boolean {
    return JS_IsObject_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsModule(value: Long): Boolean {
    return JS_IsModule_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsFunction(context: Long, value: Long): Boolean {
    return JS_IsFunction_Kt(context.toJSContext(), value.toJsValue())
}

@JniCall
internal actual fun jsValueIsAsyncFunction(value: Long): Boolean {
    return JS_IsAsyncFunction_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsConstructor(context: Long, value: Long): Boolean {
    return JS_IsConstructor_Kt(context.toJSContext(), value.toJsValue())
}

@JniCall
internal actual fun jsValueIsRegExp(value: Long): Boolean {
    return JS_IsRegExp_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsMap(value: Long): Boolean {
    return JS_IsMap_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsSet(value: Long): Boolean {
    return JS_IsSet_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsWeakRef(value: Long): Boolean {
    return JS_IsWeakRef_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsWeakSet(value: Long): Boolean {
    return JS_IsWeakSet_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsWeakMap(value: Long): Boolean {
    return JS_IsWeakMap_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsDataView(value: Long): Boolean {
    return JS_IsDataView_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsArray(value: Long): Boolean {
    return JS_IsArray_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsDate(value: Long): Boolean {
    return JS_IsDate_Kt(value.toJsValue())
}

@JniCall
internal actual fun jsValueIsPromise(value: Long): Boolean {
    return JS_IsPromise_Kt(value.toJsValue())
}

@JniCall
internal actual fun callJsFunction(
    context: Long,
    thisObj: Long,
    function: Long,
    args: LongArray
): Long {
    return memScoped {
        val cargs = allocArray<CPointerVar<JSValueHandle>>(args.size)
        args.forEachIndexed { i, arg ->
            cargs[i] = arg.toJsValue()
        }
        JS_Call_Kt(ctx = context.toJSContext(), function.toJsValue(), thisObj.toJsValue(), args.size, cargs).toLong()
    }
}


@JniCall
internal actual fun executeJsPendingJob(runtime: Long): Long {
    val rt = runtime.toJSRuntime() ?: return 0
    return memScoped {
        val pctx = allocArray<CPointerVar<JSContext>>(1)
        val result = JS_ExecutePendingJob(rt, pctx)
        if (result < 0) {
            JS_GetException_Kt(ctx = pctx[0]).toLong()
        } else {
            0
        }
    }
}

@JniCall
internal actual fun runtimeHasPendingJob(runtime: Long): Boolean {
    return JS_IsJobPending(runtime.toJSRuntime())
}

internal expect fun initializeJsFunctionBridge(
    context: Long,
    bridgeFunctionName: String,
    callbackRef: StableRef<JsFunctionBridgeCallback>
)

@JniCall
internal actual fun jsNewError(context:Long, message: String): Long {
    val err = JS_NewError_Kt(ctx = context.toCPointer())
    val text = JS_NewString_Kt(context.toCPointer(), message)
    JS_SetPropertyStr_Kt(
        ctx = context.toCPointer(),
        this_obj = err,
        prop = "message",
        `val` = text
    )
    free(text)
    return err.toLong()
}

@JniCall
internal actual fun jsNewString(context: Long, value: String): Long {
    return JS_NewString_Kt(
        ctx = context.toCPointer(),
        str = value
    ).toLong()
}

@JniCall
internal actual fun jsNewNumber(context: Long, value: Double): Long {
    return JS_NewNumber_Kt(
        ctx = context.toCPointer(),
        value = value
    ).toLong()
}

@JniCall
internal actual fun jsNewBoolean(context: Long, value: Boolean): Long {
    return JS_NewBoolean_Kt(
        ctx = context.toCPointer(),
        value = value
    ).toLong()
}

@JniCall
internal actual fun freeValueHandle(ptr: Long) {
    free(ptr.toJsValue())
}

@JniCall
internal actual fun getPromiseState(context: Long, promise: Long): Int {
    return JS_PromiseState_Kt(context.toCPointer(), promise.toCPointer())
}

@JniCall
internal actual fun getPromiseResult(context: Long, promise: Long): Long {
    return JS_PromiseResult_Kt(context.toCPointer(), promise.toCPointer()).toLong()
}

@JniCall
internal actual fun getGlobalObject(context: Long): Long {
    return JS_GetGlobalObject_Kt(context.toCPointer()).toLong()
}

@JniCall
internal actual fun jsNewObject(context: Long): Long {
    return JS_NewObject_Kt(context.toCPointer()).toLong()
}

@JniCall
internal actual fun jsNewArray(context: Long): Long {
    return JS_NewArray_Kt(context.toCPointer()).toLong()
}

@JniCall
internal actual fun setProperty(context: Long, obj: Long, name: String, value: Long) {
    JS_SetPropertyStr_Kt(context.toCPointer(), obj.toCPointer(), name, value.toCPointer())
}

@JniCall
internal actual fun jsNewNull(): Long {
    return JS_NULL_Kt().toLong()
}
