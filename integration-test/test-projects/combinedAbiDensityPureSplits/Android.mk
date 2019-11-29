LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := hello-jni
LOCAL_SRC_FILES := src/main/jni/hello-jni.c \

include $(BUILD_SHARED_LIBRARY)
