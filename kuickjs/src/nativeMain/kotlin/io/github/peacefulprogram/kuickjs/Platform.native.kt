package io.github.peacefulprogram.kuickjs

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
import platform.posix.stat
import quickjs.JSContext
import quickjs.JS_FreeContext
import quickjs.JS_GetContextOpaque
import quickjs.JS_GetRuntime
import quickjs.JS_SetContextOpaque
import quickjs.JS_SetRuntimeOpaque
import quickjs.JS_ToInt64_Kt
import quickjs.JS_VALUE_GET_BOOL_Kt
import quickjs.JS_VALUE_GET_INT_Kt

internal actual object JsPlatform {
    internal actual fun onJsBridgeFunctionCall(
        ktContextId: Long,
        functionIndex: Int,
        async: Boolean,
        args: Array<Long>
    ): Long {
        if (async) {
            return io.github.peacefulprogram.kuickjs.JSContext.onAsyncJsFunctionCall(
                contextId = ktContextId,
                functionIndex = functionIndex,
                args = args
            )
        }
        return io.github.peacefulprogram.kuickjs.JSContext.onJsFunctionCall(
            contextId = ktContextId,
            functionIndex = functionIndex,
            args = args
        )
    }

    internal actual fun initializeJsFunctionBridge(context: Long, ktContextId: Long, bridgeFunctionName: String) {
        val state = ContextEnvNative(
            id = ktContextId,
            callbackRef = StableRef.create(::onJsFunCallNative)
        )
        JS_SetContextOpaque(context.toCPointer(), StableRef.create(state).asCPointer())
        initializeJsFunctionBridge(context, bridgeFunctionName, state.callbackRef)
    }

    internal actual fun freeContext(context: Long) {
        val ctx = context.toJSContext() ?: return
        JS_GetContextOpaque(ctx)?.let { opaque ->
            val ref = opaque.asStableRef<ContextEnvNative>()
            JS_SetContextOpaque(ctx, null)
            JS_SetRuntimeOpaque(JS_GetRuntime(ctx), null)
            ref.get().callbackRef.dispose()
            ref.dispose()
        }
        JS_FreeContext(ctx)
    }

    internal actual fun loadLibrary() {
    }

}


private data class ContextEnvNative(
    val id: Long,
    val callbackRef: StableRef<JsFunctionBridgeCallback>,
)


private fun onJsFunCallNative(ctx: CPointer<JSContext>?, args: Array<Long>): Long {
    val contextId = JS_GetContextOpaque(ctx)!!.asStableRef<ContextEnvNative>().get().id
    val functionIndex = JS_VALUE_GET_INT_Kt(args[0].toCPointer())
    val async = JS_VALUE_GET_BOOL_Kt(args[1].toCPointer())
    return JsPlatform.onJsBridgeFunctionCall(
        ktContextId = contextId,
        functionIndex = functionIndex,
        async = async,
        args = args.slice(2 until args.size).toTypedArray()
    )
}
