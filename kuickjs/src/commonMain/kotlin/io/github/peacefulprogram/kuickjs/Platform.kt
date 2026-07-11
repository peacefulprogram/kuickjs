package io.github.peacefulprogram.kuickjs

internal expect object JsPlatform {
    internal fun onJsBridgeFunctionCall(
        ktContextId: Long,
        functionIndex: Int,
        async: Boolean,
        args: Array<Long>
    ): Long

    internal fun initializeJsFunctionBridge(context: Long, ktContextId: Long, bridgeFunctionName: String)

    internal fun freeContext(context: Long)
    internal fun loadLibrary()
}


