LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := hello-jni
LOCAL_LDFLAGS := -Wl,--build-id
LOCAL_SRC_FILES := ./src/main/jni/hello-jni.c 

LOCAL_C_INCLUDES += ./src/main/jni
LOCAL_C_INCLUDES += ./src/debug/jni

include $(BUILD_SHARED_LIBRARY)
