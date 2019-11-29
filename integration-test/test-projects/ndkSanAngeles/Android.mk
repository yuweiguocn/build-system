LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := sanangeles
LOCAL_CFLAGS := -DANDROID_NDK -DDISABLE_IMPORTGL
LOCAL_LDLIBS := \
	-lGLESv1_CM \
	-ldl \
	-llog \

LOCAL_SRC_FILES := \
	src/main/jni/app-android.c \
	src/main/jni/demo.c \
	src/main/jni/importgl.c \

include $(BUILD_SHARED_LIBRARY)
