package io.github.peacefulprogram.kuickjs

import com.dshatz.kni.annotations.JniCall

@JniCall
internal expect fun newRuntime(): Long

@JniCall
internal expect fun newContext(rt: Long): Long

@JniCall
internal expect fun evalJs(context: Long, code: String, fileName: String): Long

@JniCall
internal expect fun freeRuntime(runtime: Long)

@JniCall
internal expect fun freeValue(context: Long, value: Long)

@JniCall
internal expect fun dupValue(context: Long, value: Long): Long

@JniCall
internal expect fun freeValueHandle(ptr: Long)

@JniCall
internal expect fun getArrayValue(context: Long, array: Long, index: Int): Long

@JniCall
internal expect fun getProperty(context: Long, obj: Long, name: String): Long

@JniCall
internal expect fun getException(context: Long): Long
@JniCall
internal expect fun setProperty(context: Long, obj: Long, name: String, value: Long)

@JniCall
internal expect fun jsValueToString(context: Long, value: Long): String

@JniCall
internal expect fun getJsValueTag(value: Long): Int

@JniCall
internal expect fun getNumberValue(value: Long): Double

@JniCall
internal expect fun getBoolValue(value: Long): Boolean

@JniCall
internal expect fun jsonStringify(context: Long, value: Long): String

@JniCall
internal expect fun parseJson(context: Long, json: String): Long

@JniCall
internal expect fun jsValueIsNumber(value: Long): Boolean

@JniCall
internal expect fun jsValueIsBigInt(value: Long): Boolean

@JniCall
internal expect fun jsValueIsBool(value: Long): Boolean

@JniCall
internal expect fun jsValueIsNull(value: Long): Boolean

@JniCall
internal expect fun jsValueIsUndefined(value: Long): Boolean

@JniCall
internal expect fun jsValueIsPromise(value: Long): Boolean

@JniCall
internal expect fun getPromiseState(context: Long, promise: Long): Int

@JniCall
internal expect fun getPromiseResult(context: Long, promise: Long): Long

@JniCall
internal expect fun jsValueIsError(value: Long): Boolean

@JniCall
internal expect fun jsValueIsException(value: Long): Boolean

@JniCall
internal expect fun jsValueIsUninitialized(value: Long): Boolean

@JniCall
internal expect fun jsValueIsString(value: Long): Boolean

@JniCall
internal expect fun jsValueIsSymbol(value: Long): Boolean

@JniCall
internal expect fun jsValueIsObject(value: Long): Boolean

@JniCall
internal expect fun jsValueIsModule(value: Long): Boolean

@JniCall
internal expect fun jsValueIsFunction(context: Long, value: Long): Boolean

@JniCall
internal expect fun jsValueIsAsyncFunction(value: Long): Boolean

@JniCall
internal expect fun jsValueIsConstructor(context: Long, value: Long): Boolean

@JniCall
internal expect fun jsValueIsRegExp(value: Long): Boolean

@JniCall
internal expect fun jsValueIsMap(value: Long): Boolean

@JniCall
internal expect fun jsValueIsSet(value: Long): Boolean

@JniCall
internal expect fun jsValueIsWeakRef(value: Long): Boolean

@JniCall
internal expect fun jsValueIsWeakSet(value: Long): Boolean

@JniCall
internal expect fun jsValueIsWeakMap(value: Long): Boolean

@JniCall
internal expect fun jsValueIsDataView(value: Long): Boolean

@JniCall
internal expect fun jsValueIsArray(value: Long): Boolean

@JniCall
internal expect fun jsValueIsDate(value: Long): Boolean

@JniCall
internal expect fun callJsFunction(context: Long, thisObj: Long, function: Long, args: LongArray): Long

/**
 * @return 0 if there is no exception, or ptr of js error
 */
@JniCall
internal expect fun executeJsPendingJob(runtime: Long): Long

@JniCall
internal expect fun runtimeHasPendingJob(runtime: Long): Boolean


@JniCall
internal expect fun jsNewError(context:Long, message: String): Long

@JniCall
internal expect fun jsNewString(context: Long, value: String): Long

@JniCall
internal expect fun jsNewNumber(context: Long, value: Double): Long

@JniCall
internal expect fun jsNewObject(context: Long): Long

@JniCall
internal expect fun jsNewArray(context: Long): Long

@JniCall
internal expect fun jsNewBoolean(context: Long, value: Boolean): Long

@JniCall
internal expect fun jsNewNull(): Long

@JniCall
internal expect fun getGlobalObject(context: Long): Long
