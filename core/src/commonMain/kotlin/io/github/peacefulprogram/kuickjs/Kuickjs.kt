package io.github.peacefulprogram.kuickjs

sealed class JsValue(internal val ptr: Long, internal val context: JSContext) {
    class JsNumber internal constructor(ptr: Long, context: JSContext, val value: Double) : JsValue(ptr, context)
    class JsBoolean internal constructor(ptr: Long, context: JSContext, val value: Boolean) : JsValue(ptr, context)
    open class JsObject internal constructor(ptr: Long, context: JSContext) : JsValue(ptr, context) {
        suspend fun toJsonString(): String {
            return context.stringify(this)
        }
    }

    class JsArray internal constructor(ptr: Long, context: JSContext, val length: Int) : JsObject(ptr, context) {
        suspend operator fun get(index: Int): JsValue? {
            return context.getArrayValue(index, this)
        }

        operator fun iterator(): JsArrayIterator {
            return JsArrayIterator(this)
        }
    }

    class JsError internal constructor(ptr: Long, context: JSContext, val message: String, val stack: String) :
        JsObject(ptr, context)

    class JsPromise internal constructor(ptr: Long, context: JSContext, internal val holder: PromiseHolder) :
        JsValue(ptr, context)

    class JsFunction internal constructor(ptr: Long, context: JSContext) : JsValue(ptr, context)
    class JsString internal constructor(ptr: Long, context: JSContext) : JsValue(ptr, context)

    suspend fun free() {
        context.freeValue(this)
    }

}

suspend fun <T : JsValue, R> T.use(block: suspend (T) -> R): R {
    try {
        return block(this)
    } finally {
        free()
    }
}


class JsArrayIterator internal constructor(private val array: JsValue.JsArray) {
    private var nextIndex = 0
    operator fun hasNext(): Boolean {
        return nextIndex < array.length
    }

    suspend operator fun next(): JsValue? {
        return array[nextIndex++]
    }

}
