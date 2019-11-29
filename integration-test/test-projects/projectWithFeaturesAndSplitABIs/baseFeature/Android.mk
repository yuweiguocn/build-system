LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := base-jni
LOCAL_SRC_FILES := src/main/jni/base-jni.c \

include $(BUILD_SHARED_LIBRARY)
