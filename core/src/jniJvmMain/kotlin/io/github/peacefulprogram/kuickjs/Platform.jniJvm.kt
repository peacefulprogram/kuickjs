package io.github.peacefulprogram.kuickjs

actual object JsPlatform {
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
    internal actual external fun initializeJsFunctionBridge(
        context: Long,
        ktContextId: Long,
        bridgeFunctionName: String
    )

    internal actual external fun freeContext(context: Long)
}
