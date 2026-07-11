package io.github.peacefulprogram.kuickjs

import com.dshatz.kni.load.BundledLibLoader

internal actual object JsPlatform {
    @JvmStatic
    internal actual fun onJsBridgeFunctionCall(
        ktContextId: Long,
        functionIndex: Int,
        async: Boolean,
        args: Array<Long>
    ): Long {
        if (async) {
            JSContext.onAsyncJsFunctionCall(contextId = ktContextId, functionIndex = functionIndex, args = args)
            return 0
        }
        return JSContext.onJsFunctionCall(
            contextId = ktContextId,
            functionIndex = functionIndex,
            args = args
        )
    }

    @JvmStatic
    @JvmName("initializeJsFunctionBridge")
    internal actual external fun initializeJsFunctionBridge(
        context: Long,
        ktContextId: Long,
        bridgeFunctionName: String
    )

    @JvmStatic
    @JvmName("freeContext")
    internal actual external fun freeContext(context: Long)

    @JvmStatic
    private fun onJsBridgeFunctionCallJni(
        ktContextId: Long,
        functionIndex: Int,
        async: Boolean,
        args: LongArray
    ): Long {
        return onJsBridgeFunctionCall(
            ktContextId = ktContextId,
            functionIndex = functionIndex,
            async = async,
            args = args.toTypedArray()
        )
    }

    @JvmStatic
    internal actual fun loadLibrary() {
        BundledLibLoader.loadBundledLibrary("kuickjs")
    }
}
