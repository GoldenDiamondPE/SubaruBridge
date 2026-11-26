LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := subarubridge_rust
LOCAL_SRC_FILES := ../../../build/generated/rust/$(TARGET_ARCH_ABI)/libsubarubridge_core.a
include $(PREBUILT_STATIC_LIBRARY)
include $(CLEAR_VARS)
LOCAL_MODULE := subarubridge_core
LOCAL_SRC_FILES := starlink_jni.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../../../rust/include
LOCAL_CFLAGS := -Wall -Wextra -Werror
LOCAL_STATIC_LIBRARIES := subarubridge_rust
LOCAL_LDLIBS := -ldl -lm -llog
include $(BUILD_SHARED_LIBRARY)
