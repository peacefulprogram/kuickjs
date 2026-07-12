package io.github.peacefulprogram.kuickjs

sealed class JsValue(internal val ptr: Long, internal val context: JSContext) {
    class JsNumber internal constructor(ptr: Long, context: JSContext, val value: Double) : JsValue(ptr, context)
    class JsBoolean internal constructor(ptr: Long, context: JSContext, val value: Boolean) : JsValue(ptr, context)
    open class JsObject internal constructor(ptr: Long, context: JSContext) : JsValue(ptr, context) {
        fun toJsonString(): String {
            return context.jsonStringify(this)
        }

        fun getProperty(name: String): JsValue? {
            return context.getObjectProperty(this.ptr, name)
        }

        fun keys(): List<String> {
            val keyList = mutableListOf<String>()
            context.getGlobalObject().use { global ->
                global.getProperty("Object")?.use { obj ->
                    val objectConstructor = obj as? JsFunction
                        ?: error("global Object is not a JavaScript function")
                    objectConstructor.getProperty("keys")?.use { keysValue ->
                        val keysFunction = keysValue as? JsFunction
                            ?: error("Object.keys is not a JavaScript function")
                        keysFunction.invoke(arrayOf(this))?.use { result ->
                            val arr = result as? JsArray
                                ?: error("Object.keys did not return an array")
                            for (i in 0 until arr.length) {
                                arr[i]?.use { key ->
                                    keyList.add(key.toString())
                                }
                            }
                        }
                    }
                }
            }
            return keyList
        }
    }

    class JsArray internal constructor(ptr: Long, context: JSContext, val length: Int) : JsObject(ptr, context) {
        operator fun get(index: Int): JsValue? {
            return context.getArrayValue(index, this)
        }

        operator fun iterator(): JsArrayIterator {
            return JsArrayIterator(this)
        }
    }

    class JsError internal constructor(ptr: Long, context: JSContext, val message: String, val stack: String) :
        JsObject(ptr, context)

    class JsPromise internal constructor(ptr: Long, context: JSContext) :
        JsValue(ptr, context) {
        suspend fun await(): JsValue? {
            return context.awaitPromise(ptr)
        }
    }

    class JsFunction internal constructor(ptr: Long, context: JSContext) : JsValue(ptr, context) {
        fun invoke(args: Array<JsValue?>, thisObj: JsValue? = null): JsValue? {
            return context.callJsFunction(function = this, thisObj = thisObj, args = args)
        }

        fun getProperty(name: String): JsValue? {
            return context.getObjectProperty(this.ptr, name)
        }
    }

    class JsString internal constructor(ptr: Long, context: JSContext) : JsValue(ptr, context)

    fun free() {
        context.freeValue(this)
    }

    override fun toString(): String {
        return context.jsValueToString(this)
    }


}

fun <T : JsValue, R> T.use(block: (T) -> R): R {
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

    operator fun next(): JsValue? {
        return array[nextIndex++]
    }

}
