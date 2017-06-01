/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class ch_cern_eos_XrdClFile */

#ifndef _Included_ch_cern_eos_XrdClFile
#define _Included_ch_cern_eos_XrdClFile
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     ch_cern_eos_XrdClFile
 * Method:    initFile
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_ch_cern_eos_XrdClFile_initFile
  (JNIEnv *, jobject);

/*
 * Class:     ch_cern_eos_XrdClFile
 * Method:    disposeFile
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_ch_cern_eos_XrdClFile_disposeFile
  (JNIEnv *, jobject, jlong);

/*
 * Class:     ch_cern_eos_XrdClFile
 * Method:    openFile
 * Signature: (JLjava/lang/String;II)J
 */
JNIEXPORT jlong JNICALL Java_ch_cern_eos_XrdClFile_openFile
  (JNIEnv *, jobject, jlong, jstring, jint, jint);

/*
 * Class:     ch_cern_eos_XrdClFile
 * Method:    readFile
 * Signature: (JJ[BII)J
 */
JNIEXPORT jlong JNICALL Java_ch_cern_eos_XrdClFile_readFile
  (JNIEnv *, jobject, jlong, jlong, jbyteArray, jint, jint);

/*
 * Class:     ch_cern_eos_XrdClFile
 * Method:    writeFile
 * Signature: (JJ[BII)J
 */
JNIEXPORT jlong JNICALL Java_ch_cern_eos_XrdClFile_writeFile
  (JNIEnv *, jobject, jlong, jlong, jbyteArray, jint, jint);

/*
 * Class:     ch_cern_eos_XrdClFile
 * Method:    syncFile
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_ch_cern_eos_XrdClFile_syncFile
  (JNIEnv *, jobject, jlong);

/*
 * Class:     ch_cern_eos_XrdClFile
 * Method:    closeFile
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_ch_cern_eos_XrdClFile_closeFile
  (JNIEnv *, jobject, jlong);

#ifdef __cplusplus
}
#endif
#endif
