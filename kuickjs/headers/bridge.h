#ifndef QUICKJS_BRIDGE_KT_H
#define QUICKJS_BRIDGE_KT_H

#include "quickjs.h"
#include "stdlib.h"

typedef struct JSValueHandle {
    JSValue value;
} JSValueHandle;

static inline JSValueHandle *wrap_js_value(JSValue value) {
    JSValueHandle *handle = malloc(sizeof(JSValueHandle));
    handle->value = value;
    return handle;
}

static inline int JS_VALUE_GET_TAG_Kt(JSValueHandle *value) {
    return JS_VALUE_GET_TAG(value->value);
}

static inline int JS_VALUE_GET_NORM_TAG_Kt(JSValueHandle *value) {
    return JS_VALUE_GET_NORM_TAG(value->value);
}

static inline int JS_VALUE_GET_INT_Kt(JSValueHandle *value) {
    return JS_VALUE_GET_INT(value->value);
}

static inline bool JS_VALUE_GET_BOOL_Kt(JSValueHandle *value) {
    return JS_VALUE_GET_BOOL(value->value);
}

static inline double JS_VALUE_GET_FLOAT64_Kt(JSValueHandle *value) {
    return JS_VALUE_GET_FLOAT64(value->value);
}

static inline int JS_VALUE_GET_SHORT_BIG_INT_Kt(JSValueHandle *value) {
    return JS_VALUE_GET_SHORT_BIG_INT(value->value);
}

static inline bool JS_IsNumber_Kt(JSValueHandle *v) {
    return JS_IsNumber(v->value);
}

static inline bool JS_IsBigInt_Kt(JSValueHandle *v) {
    return JS_IsBigInt(v->value);
}

static inline bool JS_IsBool_Kt(JSValueHandle *v) {
    return JS_IsBool(v->value);
}

static inline bool JS_IsNull_Kt(JSValueHandle *v) {
    return JS_IsNull(v->value);
}

static inline bool JS_IsUndefined_Kt(JSValueHandle *v) {
    return JS_IsUndefined(v->value);
}

static inline bool JS_IsError_Kt(JSValueHandle *val) {
    return JS_IsError(val->value);
}

static inline bool JS_IsException_Kt(JSValueHandle *v) {
    return JS_IsException(v->value);
}

static inline bool JS_IsUninitialized_Kt(JSValueHandle *v) {
    return JS_IsUninitialized(v->value);
}

static inline bool JS_IsString_Kt(JSValueHandle *v) {
    return JS_IsString(v->value);
}

static inline bool JS_IsSymbol_Kt(JSValueHandle *v) {
    return JS_IsSymbol(v->value);
}

static inline bool JS_IsObject_Kt(JSValueHandle *v) {
    return JS_IsObject(v->value);
}

static inline bool JS_IsModule_Kt(JSValueHandle *v) {
    return JS_IsModule(v->value);
}

static inline bool JS_IsFunction_Kt(JSContext *ctx, JSValueHandle *val) {
    return JS_IsFunction(ctx, val->value);
}

static inline bool JS_IsAsyncFunction_Kt(JSValueHandle *val) {
    return JS_IsAsyncFunction(val->value);
}

static inline bool JS_IsConstructor_Kt(JSContext *ctx, JSValueHandle *val) {
    return JS_IsConstructor(ctx, val->value);
}

static inline bool JS_IsRegExp_Kt(JSValueHandle *val) {
    return JS_IsRegExp(val->value);
}

static inline bool JS_IsMap_Kt(JSValueHandle *val) {
    return JS_IsMap(val->value);
}

static inline bool JS_IsSet_Kt(JSValueHandle *val) {
    return JS_IsSet(val->value);
}

static inline bool JS_IsWeakRef_Kt(JSValueHandle *val) {
    return JS_IsWeakRef(val->value);
}

static inline bool JS_IsWeakSet_Kt(JSValueHandle *val) {
    return JS_IsWeakSet(val->value);
}

static inline bool JS_IsWeakMap_Kt(JSValueHandle *val) {
    return JS_IsWeakMap(val->value);
}

static inline bool JS_IsDataView_Kt(JSValueHandle *val) {
    return JS_IsDataView(val->value);
}


static inline bool JS_IsArray_Kt(JSValueHandle *val) {
    return JS_IsArray(val->value);
}

static inline bool JS_IsDate_Kt(JSValueHandle *val) {
    return JS_IsDate(val->value);
}

static inline void JS_FreeValue_Kt(JSContext *ctx, JSValueHandle *value) {
    if (value != NULL) {
        JS_FreeValue(ctx, value->value);
        free(value);
    }
}

static inline JSValueHandle *JS_DupValue_Kt(JSContext *ctx, JSValueHandle *v) {
    return wrap_js_value(JS_DupValue(ctx, v->value));
}

static inline JSValueHandle *JS_NewError_Kt(JSContext *ctx) {
    return wrap_js_value(JS_NewError(ctx));
}

static inline JSValueHandle *JS_Throw_Kt(JSContext *ctx, JSValueHandle *obj) {
    return wrap_js_value(JS_Throw(ctx, obj->value));
}

static inline JSValueHandle *JS_GetException_Kt(JSContext *ctx) {
    return wrap_js_value(JS_GetException(ctx));
}

static inline JSValueHandle *JS_NewArray_Kt(JSContext *ctx) {
    return wrap_js_value(JS_NewArray(ctx));
}

static inline JSValueHandle *JS_NewArrayFrom_Kt(JSContext *ctx, int count,
        const JSValueHandle **values) {
    JSValue *vs = malloc(sizeof(JSValue) * count);
    for (int i = 0; i < count; i++) {
        vs[i] = values[i]->value;
    }
    JSValueHandle *result = wrap_js_value(JS_NewArrayFrom(ctx, count, vs));
    free(vs);
    return result;
}

static inline JSValueHandle *JS_GetProperty_Kt(JSContext *ctx, JSValueHandle *this_obj, JSAtom prop) {
    return wrap_js_value(JS_GetProperty(ctx, this_obj->value, prop));
}

static inline JSValueHandle *JS_GetPropertyStr_Kt(JSContext *ctx, JSValueHandle *this_obj, const char *prop) {
    return wrap_js_value(JS_GetPropertyStr(ctx, this_obj->value, prop));
}

static inline JSValueHandle *JS_GetPropertyUint32_Kt(JSContext *ctx, JSValueHandle *this_obj, uint32_t idx) {
    return wrap_js_value(JS_GetPropertyUint32(ctx, this_obj->value, idx));
}

static inline int JS_SetProperty_Kt(JSContext *ctx, JSValueHandle *this_obj, JSAtom prop, JSValueHandle *val) {
    return JS_SetProperty(ctx, this_obj->value, prop, val->value);
}

static inline int JS_SetPropertyStr_Kt(JSContext *ctx, JSValueHandle *this_obj, const char *prop, JSValueHandle *val) {
    return JS_SetPropertyStr(ctx, this_obj->value, prop, val->value);
}

static int JS_GetOwnPropertyNames_Kt(JSContext *ctx, JSPropertyEnum **ptab,
        uint32_t *plen, JSValueHandle *obj,
        int flags) {
    return JS_GetOwnPropertyNames(ctx, ptab, plen, obj->value, flags);
}

static inline JSValueHandle *JS_Call_Kt(JSContext *ctx, JSValueHandle *func_obj,
        JSValueHandle *this_obj, int argc, JSValueHandle **argv) {
    JSValue args[argc > 0 ? argc : 1];
    for (int i = 0; i < argc; i++) {
        args[i] = argv[i] != NULL ? argv[i]->value : JS_UNDEFINED;
    }
    JSValue result = JS_Call(
            ctx,
            func_obj->value,
            this_obj != NULL ? this_obj->value : JS_UNDEFINED,
            argc,
            args);
    return wrap_js_value(result);
}

static inline JSValueHandle *JS_Eval_Kt(JSContext *ctx, const char *input, int32_t input_len,
        const char *filename, int eval_flags) {
    return wrap_js_value(JS_Eval(ctx, input, input_len, filename, eval_flags));
}

static inline JSValueHandle *JS_GetGlobalObject_Kt(JSContext *ctx) {
    return wrap_js_value(JS_GetGlobalObject(ctx));
}


static inline JSValueHandle *JS_ParseJSON_Kt(JSContext *ctx, const char *buf, const int buf_len,
        const char *filename) {
    return wrap_js_value(JS_ParseJSON(ctx, buf, buf_len, filename));
}

static inline JSValueHandle *JS_JSONStringify_Kt(JSContext *ctx, JSValueHandle *obj) {
    return wrap_js_value(JS_JSONStringify(ctx, obj->value, JS_UNDEFINED, JS_UNDEFINED));
}

static inline JSValueHandle *
JS_NewPromiseCapability_Kt(JSContext *ctx, JSValueHandle **resolve, JSValueHandle **reject) {
    JSValue funcs[2];
    JSValue p = JS_NewPromiseCapability(ctx, funcs);
    *resolve = wrap_js_value(funcs[0]);
    *reject = wrap_js_value(funcs[1]);
    return wrap_js_value(p);
}

static inline JSPromiseStateEnum JS_PromiseState_Kt(JSContext *ctx,
        JSValueHandle *promise) {
    return JS_PromiseState(ctx, promise->value);
}

static inline bool JS_IsPromise_Kt(JSValueHandle *val) {
    return JS_IsPromise(val->value);
}

static inline JSValueHandle *JS_PromiseResult_Kt(JSContext *ctx, JSValueHandle *promise) {
    return wrap_js_value(JS_PromiseResult(ctx, promise->value));
}

static inline JSValueHandle *JS_NewCFunction_Kt(JSContext *ctx, JSCFunction *func,
        const char *name, int length) {
    return wrap_js_value(JS_NewCFunction(ctx, func, name, length));
}

static inline JSValueHandle *JS_NewCFunctionData_Kt(JSContext *ctx, JSCFunctionData *func,
        int length, int magic, int data_len,
        JSValueHandle *data) {
    return wrap_js_value(JS_NewCFunctionData(ctx, func, length, magic, data_len, &data->value));
}

static inline JSValueHandle *JS_NewBigInt64_Kt(JSContext *ctx, int64_t value) {
    return wrap_js_value(JS_NewBigInt64(ctx, value));
}

static inline JSValueHandle *JS_NewNumber_Kt(JSContext *ctx, double value) {
    return wrap_js_value(JS_NewFloat64(ctx, value));
}

static inline JSValueHandle *JS_NewBoolean_Kt(JSContext *ctx, bool value) {
    return wrap_js_value(JS_NewBool(ctx, value));
}

static inline int JS_ToBigInt64_Kt(JSContext *ctx, int64_t *num, JSValueHandle *val) {
    return JS_ToBigInt64(ctx, num, val->value);
}

static inline JSValueHandle *JS_ToString_Kt(JSContext *ctx, JSValueHandle *val) {
    return wrap_js_value(JS_ToString(ctx, val->value));
}

static inline const char *JS_ToCString_Kt(JSContext *ctx, JSValueHandle *val1) {
    return JS_ToCString(ctx, val1->value);
}

static inline int JS_ToInt64_Kt(JSContext *ctx, int64_t *num, JSValueHandle *val) {
    return JS_ToInt64(ctx, num, val->value);
}

static inline JSValue JS_EXCEPTION_Kt() {
    return JS_EXCEPTION;
}

static inline JSValue JS_UNDEFINED_Kt() {
    return JS_UNDEFINED;
}

static inline JSValueHandle *JS_NewString_Kt(JSContext *ctx, const char *str) {
    return wrap_js_value(JS_NewString(ctx, str));
}

static inline JSValueHandle *JS_NewObject_Kt(JSContext *ctx) {
    return wrap_js_value(JS_NewObject(ctx));
}

static inline JSValueHandle *JS_NULL_Kt() {
    return wrap_js_value(JS_NULL);
}

static inline JSValueHandle *JS_NewUint8ArrayCopy_Kt(JSContext *ctx, const uint8_t *buf, int len) {
    return wrap_js_value(JS_NewUint8ArrayCopy(ctx, buf, len));
}

#endif //QUICKJS_BRIDGE_KT_H
