package io.github.peacefulprogram.kuickjs

import com.dshatz.kni.JNIEnvVar
import com.dshatz.kni.JavaVMVar
import com.dshatz.kni.binding.JNI_EDETACHED
import com.dshatz.kni.binding.JNI_OK
import com.dshatz.kni.binding.JNI_VERSION_1_6
import com.dshatz.kni.binding.jlong
import com.dshatz.kni.binding.jlongVar
import com.dshatz.kni.binding.jmethodID
import com.dshatz.kni.binding.jobject
import com.dshatz.kni.binding.jstring
import com.dshatz.kni.jvalue
import com.dshatz.kni.l
import com.dshatz.kni.pointedCommon
import com.dshatz.kni.utils.AttachCurrentThread
import com.dshatz.kni.utils.DeleteGlobalRef
import com.dshatz.kni.utils.DeleteLocalRef
import com.dshatz.kni.utils.GetStaticMethodID
import com.dshatz.kni.utils.NewGlobalRef
import com.dshatz.kni.utils.getJavaVM
import com.dshatz.kni.utils.newLongArray
import com.dshatz.kni.utils.toKString
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
import quickjs.JSContext
import quickjs.JS_FreeContext
import quickjs.JS_FreeRuntime
import quickjs.JS_GetContextOpaque
import quickjs.JS_GetRuntime
import quickjs.JS_GetRuntimeOpaque
import quickjs.JS_SetContextOpaque
import quickjs.JS_SetRuntimeOpaque
import quickjs.JS_ToInt64_Kt
import quickjs.JS_VALUE_GET_BOOL_Kt
import kotlin.experimental.ExperimentalNativeApi

private class ContextEnv(
    val jvm: CPointer<JavaVMVar>,
    val contextId: Long,
    val platformClass: jobject?,
    val bridgeMethodId: jmethodID?,
    val callbackRef: StableRef<JsFunctionBridgeCallback>
)

@OptIn(ExperimentalNativeApi::class)
@CName("Java_io_github_peacefulprogram_kuickjs_JsPlatform_initializeJsFunctionBridge")
fun _initializeJsFunctionBridgeJni(
    env: CPointer<JNIEnvVar>,
    clazz: jobject,
    context: jlong,
    ktContextId: jlong,
    bridgeFunctionName: jstring
) {
    val ctx = context.toJSContext() ?: return
    val platformClass = env.NewGlobalRef(clazz) ?: return
    val bridgeMethodId = env.GetStaticMethodID(clazz, "onJsBridgeFunctionCallJni", "(JIZ[J)J")
    if (bridgeMethodId == null) {
        env.DeleteGlobalRef(platformClass)
        return
    }
    val state = ContextEnv(
        jvm = env.getJavaVM(),
        contextId = ktContextId,
        platformClass = platformClass,
        bridgeMethodId = bridgeMethodId,
        callbackRef = StableRef.create(::onJsFunCall)
    )
    val stateRef = StableRef.create(state)
    val statePtr = stateRef.asCPointer()
    JS_SetContextOpaque(ctx, statePtr)
    initializeJsFunctionBridge(context, bridgeFunctionName.toKString(env)!!, state.callbackRef)
}

fun onJsFunCall(ctx: CPointer<JSContext>?, args: Array<Long>): Long {
    if (args.size < 2) return 0L
    val env = JS_GetContextOpaque(ctx)?.asStableRef<ContextEnv>()?.get() ?: return 0L
    return env.jvm.withEnv { jniEnv ->
        val javaArgs = allocArray<jvalue>(4)
        javaArgs[0].j = env.contextId
        val functionIndex = alloc<jlongVar>()
        if (JS_ToInt64_Kt(ctx, functionIndex.ptr, args[0].toCPointer()) != 0) {
            return@withEnv 0L
        }
        javaArgs[1].j = functionIndex.value
        javaArgs[2].z = if (JS_VALUE_GET_BOOL_Kt(args[1].toCPointer())) 1.toUByte() else 0.toUByte()
        val otherArgCnt = args.size - 2
        val otherArgs = jniEnv.newLongArray(otherArgCnt) ?: return@withEnv 0L
        try {
            if (otherArgCnt > 0) {
                val vars = allocArray<jlongVar>(otherArgCnt)
                for (i in 0 until otherArgCnt) {
                    vars[i] = args[i + 2]
                }
                jniEnv.pointed.pointedCommon!!.SetLongArrayRegion!!.invoke(
                    jniEnv,
                    otherArgs,
                    0,
                    otherArgCnt,
                    vars
                )
            }
            javaArgs[3].l = otherArgs.reinterpret()
            jniEnv.pointed.pointedCommon!!.CallStaticLongMethodA!!.invoke(
                jniEnv,
                env.platformClass,
                env.bridgeMethodId,
                javaArgs
            )
        } finally {
            jniEnv.DeleteLocalRef(otherArgs)
        }
    }
}

@OptIn(ExperimentalNativeApi::class)
@CName("Java_io_github_peacefulprogram_kuickjs_JsPlatform_freeContext")
fun _freeContext(
    env: CPointer<JNIEnvVar>,
    clazz: jobject,
    context: jlong
) {
    val ctx = context.toJSContext() ?: return
    JS_GetContextOpaque(ctx)?.let { opaque ->
        val stateRef = opaque.asStableRef<ContextEnv>()
        val state = stateRef.get()
        JS_SetContextOpaque(ctx, null)
        JS_SetRuntimeOpaque(JS_GetRuntime(ctx), null)
        state.callbackRef.dispose()
        state.platformClass?.let(env::DeleteGlobalRef)
        stateRef.dispose()
    }
    JS_FreeContext(ctx)
}

private fun <R> CPointer<JavaVMVar>.withEnv(block: MemScope.(CPointer<JNIEnvVar>) -> R): R {
    val vm = this
    return memScoped {
        val envVar = alloc<CPointerVar<JNIEnvVar>>()
        val envPtrPtr = envVar.ptr.reinterpret<CPointerVarOf<CPointer<out CPointed>>>()
        var attached = false
        when (pointed.pointed?.GetEnv?.invoke(vm, envPtrPtr, JNI_VERSION_1_6)) {
            JNI_OK -> Unit
            JNI_EDETACHED -> {
                attached = true
                if (vm.AttachCurrentThread(envVar) != JNI_OK) {
                    throw IllegalStateException("Failed to AttachCurrentThread")
                }
            }
            else -> throw IllegalStateException("Failed to get JNIEnv")
        }
        try {
            block(envVar.value!!)
        } finally {
            if (attached) {
                vm.pointed.pointed?.DetachCurrentThread?.invoke(vm)
            }
        }
    }
}
