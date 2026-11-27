/* Original SubaruBridge JNI adapter. No vendor SDK or extracted binaries. */
#include <jni.h>
#include <limits.h>
#include "subarubridge_core.h"

#define METHOD(name) Java_org_mlpi_pixelprobe_starlink_RustCore_##name
static jbyteArray bounded_result(JNIEnv *env, CoreBuffer buffer, size_t limit) {
    jbyteArray bytes = NULL;
    if (buffer.len <= limit && buffer.len <= INT_MAX) {
        bytes = (*env)->NewByteArray(env, (jsize)buffer.len);
        if (bytes && buffer.len) (*env)->SetByteArrayRegion(env, bytes, 0, (jsize)buffer.len, (const jbyte *)buffer.data);
    }
    sb_core_buffer_free(buffer);
    return bytes;
}
static jbyteArray result(JNIEnv *env, CoreBuffer buffer) { return bounded_result(env,buffer,1024*1024); }
JNIEXPORT jbyteArray JNICALL Java_org_mlpi_pixelprobe_ownership_OwnershipCore_nativeExecute(JNIEnv *env,jclass cls,jbyteArray input) {
    (void)cls;if(!input)return NULL;
    jsize length=(*env)->GetArrayLength(env,input);if(length>4*1024*1024)return NULL;
    jbyte *data=(*env)->GetByteArrayElements(env,input,NULL);if(!data)return NULL;
    CoreBuffer buffer=sb_ownership_execute((const uint8_t*)data,(size_t)length);
    (*env)->ReleaseByteArrayElements(env,input,data,JNI_ABORT);
    return bounded_result(env,buffer,16*1024*1024);
}
JNIEXPORT jlong JNICALL METHOD(nativeCreate)(JNIEnv *env, jclass cls, jlong seed) {
    (void)env; (void)cls; return (jlong)sb_core_create((uint64_t)seed);
}
JNIEXPORT jbyteArray JNICALL Java_org_mlpi_pixelprobe_starlink_AppleMediaCodec_nativeDecode(JNIEnv *env, jclass cls, jbyteArray input) {
    (void)cls;
    if (!input) return NULL;
    jsize length=(*env)->GetArrayLength(env,input);
    if (length>512) return NULL;
    jbyte *data=(*env)->GetByteArrayElements(env,input,NULL);
    if (!data) return NULL;
    CoreBuffer buffer=sb_ams_decode((const uint8_t *)data,(size_t)length);
    (*env)->ReleaseByteArrayElements(env,input,data,JNI_ABORT);
    return result(env,buffer);
}
JNIEXPORT void JNICALL METHOD(nativeDestroy)(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls; sb_core_destroy((uint64_t)handle);
}
JNIEXPORT jbyteArray JNICALL METHOD(nativeFeed)(JNIEnv *env, jclass cls, jlong handle, jbyteArray input, jlong now) {
    (void)cls;
    if (!input) return NULL;
    jsize length = (*env)->GetArrayLength(env, input);
    if (length > 65536) return NULL;
    jbyte *data = (*env)->GetByteArrayElements(env, input, NULL);
    if (!data) return NULL;
    CoreBuffer buffer = sb_core_feed((uint64_t)handle, (const uint8_t *)data, (size_t)length, (uint64_t)now);
    (*env)->ReleaseByteArrayElements(env, input, data, JNI_ABORT);
    return result(env, buffer);
}
JNIEXPORT jbyteArray JNICALL METHOD(nativeRespond)(JNIEnv *env, jclass cls, jlong handle, jlong connection,
        jbyteArray input, jboolean close, jlong now) {
    (void)cls;
    if (!input) return NULL;
    jsize length = (*env)->GetArrayLength(env, input);
    if (length > 65536) return NULL;
    jbyte *data = (*env)->GetByteArrayElements(env, input, NULL);
    if (!data) return NULL;
    CoreBuffer buffer = sb_core_respond((uint64_t)handle, (uint64_t)connection, (const uint8_t *)data,
                                      (size_t)length, close ? 1 : 0, (uint64_t)now);
    (*env)->ReleaseByteArrayElements(env, input, data, JNI_ABORT);
    return result(env, buffer);
}
JNIEXPORT jbyteArray JNICALL METHOD(nativeTick)(JNIEnv *env, jclass cls, jlong handle, jlong now) {
    (void)cls; return result(env, sb_core_tick((uint64_t)handle, (uint64_t)now));
}
