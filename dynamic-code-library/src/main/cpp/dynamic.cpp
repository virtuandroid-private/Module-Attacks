#include <jni.h>
#include <android/log.h>

#define LOG_TAG "NativeLogic"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT void JNICALL
Java_com_virtualxposed_codeloader_SampleNative_initNative(JNIEnv *env, jobject thiz, jobject context) {
    if (env == nullptr || context == nullptr) {
        LOGE("Invalid JNI environment or context.");
        return;
    }

    // Print package name
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPackageNameMethod = env->GetMethodID(
            contextClass, "getPackageName", "()Ljava/lang/String;"
    );

    auto packageNameStr = (jstring) env->CallObjectMethod(context, getPackageNameMethod);
    const char *packageNameCStr = env->GetStringUTFChars(packageNameStr, nullptr);

    LOGI("Running dynamic code in %s!", packageNameCStr);

    env->ReleaseStringUTFChars(packageNameStr, packageNameCStr);
    env->DeleteLocalRef(packageNameStr);

    // Create toast

    jclass looperClass = env->FindClass("android/os/Looper");
    jmethodID getMainLooperMethod = env->GetStaticMethodID(
            looperClass, "getMainLooper", "()Landroid/os/Looper;"
    );
    jobject mainLooper = env->CallStaticObjectMethod(looperClass, getMainLooperMethod);

    jclass handlerClass = env->FindClass("android/os/Handler");
    jmethodID handlerConstructor = env->GetMethodID(
            handlerClass, "<init>", "(Landroid/os/Looper;)V"
    );
    jobject handler = env->NewObject(handlerClass, handlerConstructor, mainLooper);

    jclass toastClass = env->FindClass("android/widget/Toast");
    jmethodID makeTextMethod = env->GetStaticMethodID(
            toastClass,
            "makeText",
            "(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;"
    );

    jstring toastText = env->NewStringUTF("Toast created from dynamic cpp code!");
    jint durationLong = 1; // Toast.LENGTH_LONG = 1

    // Toast.makeText(context, "...", Toast.LENGTH_LONG)
    jobject toastObj = env->CallStaticObjectMethod(
            toastClass, makeTextMethod, context, toastText, durationLong
    );

    // toast.show()
    jmethodID showMethod = env->GetMethodID(toastClass, "show", "()V");
    env->CallVoidMethod(toastObj, showMethod);

    // Cleanup Local References
    env->DeleteLocalRef(toastText);
    env->DeleteLocalRef(toastObj);
    env->DeleteLocalRef(toastClass);
    env->DeleteLocalRef(handler);
    env->DeleteLocalRef(handlerClass);
    env->DeleteLocalRef(mainLooper);
    env->DeleteLocalRef(looperClass);
    env->DeleteLocalRef(contextClass);
}