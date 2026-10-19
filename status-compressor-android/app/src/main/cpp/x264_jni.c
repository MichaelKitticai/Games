// JNI bridge to x264: RGBA frames in (from an ImageReader the GPU renders into), H.264 out.
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <x264.h>

typedef struct {
    x264_t *enc;
    x264_picture_t pic;
    int width;
    int height;
} Encoder;


static jbyteArray nals_to_array(JNIEnv *env, x264_nal_t *nals, int count, int size) {
    if (size <= 0) return NULL;
    jbyteArray out = (*env)->NewByteArray(env, size);
    int offset = 0;
    for (int i = 0; i < count; i++) {
        (*env)->SetByteArrayRegion(env, out, offset, nals[i].i_payload, (const jbyte *) nals[i].p_payload);
        offset += nals[i].i_payload;
    }
    return out;
}

static void fill_info(JNIEnv *env, jlongArray info, x264_picture_t *out) {
    jlong v[3] = {out->i_pts, out->i_dts, out->b_keyframe ? 1 : 0};
    (*env)->SetLongArrayRegion(env, info, 0, 3, v);
}

JNIEXPORT jlong JNICALL
Java_com_michaelkitticai_statuscompressor_X264_nativeOpen(
        JNIEnv *env, jclass clazz, jint width, jint height, jint fps,
        jint bitrateKbps, jint maxrateKbps, jint bufsizeKbps, jstring preset, jstring tune) {
    const char *p = (*env)->GetStringUTFChars(env, preset, NULL);
    const char *t = (*env)->GetStringUTFChars(env, tune, NULL);
    x264_param_t param;
    int ok = x264_param_default_preset(&param, p, t[0] ? t : NULL);
    (*env)->ReleaseStringUTFChars(env, preset, p);
    (*env)->ReleaseStringUTFChars(env, tune, t);
    if (ok < 0) return 0;

    param.i_csp = X264_CSP_I420;
    param.i_width = width;
    param.i_height = height;
    param.i_fps_num = fps;
    param.i_fps_den = 1;
    // Timestamps are microseconds, straight from the frame pipeline.
    param.i_timebase_num = 1;
    param.i_timebase_den = 1000000;
    // Rate control from real timestamps, so the bitrate holds whatever frame rate arrives
    // (60 fps sources dropped to 30, 24 fps films...).
    param.b_vfr_input = 1;
    param.i_keyint_max = fps * 4;
    // Shorter lookahead than the preset's default: fewer frames held in memory, tiny quality cost.
    if (param.rc.i_lookahead > 30) param.rc.i_lookahead = 30;
    param.rc.i_rc_method = X264_RC_ABR;
    param.rc.i_bitrate = bitrateKbps;
    param.rc.i_vbv_max_bitrate = maxrateKbps;
    param.rc.i_vbv_buffer_size = bufsizeKbps;
    param.b_annexb = 1;          // Start codes, like MediaCodec output.
    param.b_repeat_headers = 0;  // SPS/PPS go in the container's codec config instead.
    param.i_log_level = X264_LOG_NONE;
    // SDR BT.709, limited range: matches how we convert RGB below.
    param.vui.i_colorprim = 1;
    param.vui.i_transfer = 1;
    param.vui.i_colmatrix = 1;
    param.vui.b_fullrange = 0;
    if (x264_param_apply_profile(&param, "high") < 0) return 0;

    Encoder *e = calloc(1, sizeof(Encoder));
    e->enc = x264_encoder_open(&param);
    if (!e->enc) {
        free(e);
        return 0;
    }
    e->width = width;
    e->height = height;
    x264_picture_init(&e->pic);
    e->pic.img.i_csp = X264_CSP_I420;
    e->pic.img.i_plane = 3;
    e->pic.img.i_stride[0] = width;
    e->pic.img.i_stride[1] = width / 2;
    e->pic.img.i_stride[2] = width / 2;
    return (jlong) (intptr_t) e;
}

/** SPS, PPS and SEI, each with a start code. */
JNIEXPORT jbyteArray JNICALL
Java_com_michaelkitticai_statuscompressor_X264_nativeHeaders(JNIEnv *env, jclass clazz, jlong handle) {
    Encoder *e = (Encoder *) (intptr_t) handle;
    x264_nal_t *nals;
    int count;
    int size = x264_encoder_headers(e->enc, &nals, &count);
    return nals_to_array(env, nals, count, size);
}

/** Most frames x264 can hold before it returns the first encoded one. */
JNIEXPORT jint JNICALL
Java_com_michaelkitticai_statuscompressor_X264_nativeMaxDelayedFrames(JNIEnv *env, jclass clazz, jlong handle) {
    Encoder *e = (Encoder *) (intptr_t) handle;
    return x264_encoder_maximum_delayed_frames(e->enc);
}

/** RGBA (as rendered by the GPU) to tightly packed I420, BT.709 limited range. */
JNIEXPORT void JNICALL
Java_com_michaelkitticai_statuscompressor_X264_nativeConvert(
        JNIEnv *env, jclass clazz, jlong handle, jobject rgba, jint rowStride, jobject i420) {
    Encoder *e = (Encoder *) (intptr_t) handle;
    const uint8_t *src = (*env)->GetDirectBufferAddress(env, rgba);
    uint8_t *Y = (*env)->GetDirectBufferAddress(env, i420);
    if (!src || !Y) return;
    int w = e->width, h = e->height;
    uint8_t *U = Y + (size_t) w * h, *V = U + (size_t) (w / 2) * (h / 2);

    for (int y = 0; y < h; y++) {
        const uint8_t *row = src + (size_t) y * rowStride;
        uint8_t *yrow = Y + (size_t) y * w;
        for (int x = 0; x < w; x++) {
            int r = row[4 * x], g = row[4 * x + 1], b = row[4 * x + 2];
            yrow[x] = (uint8_t) (((47 * r + 157 * g + 16 * b + 128) >> 8) + 16);
        }
    }
    for (int y = 0; y < h / 2; y++) {
        const uint8_t *r0 = src + (size_t) (2 * y) * rowStride;
        const uint8_t *r1 = r0 + rowStride;
        uint8_t *urow = U + (size_t) y * (w / 2), *vrow = V + (size_t) y * (w / 2);
        for (int x = 0; x < w / 2; x++) {
            int i = 8 * x;
            int r = (r0[i] + r0[i + 4] + r1[i] + r1[i + 4] + 2) >> 2;
            int g = (r0[i + 1] + r0[i + 5] + r1[i + 1] + r1[i + 5] + 2) >> 2;
            int b = (r0[i + 2] + r0[i + 6] + r1[i + 2] + r1[i + 6] + 2) >> 2;
            urow[x] = (uint8_t) (((-26 * r - 87 * g + 112 * b + 128) >> 8) + 128);
            vrow[x] = (uint8_t) (((112 * r - 102 * g - 10 * b + 128) >> 8) + 128);
        }
    }
}

/**
 * Encodes one I420 frame (from nativeConvert). x264 copies it, so the buffer can be reused
 * straight away. Returns the encoded frame, or null while x264 is still buffering.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_michaelkitticai_statuscompressor_X264_nativeEncode(
        JNIEnv *env, jclass clazz, jlong handle, jobject i420, jlong ptsUs, jlongArray info) {
    Encoder *e = (Encoder *) (intptr_t) handle;
    uint8_t *Y = (*env)->GetDirectBufferAddress(env, i420);
    if (!Y) return NULL;
    int w = e->width, h = e->height;
    e->pic.img.plane[0] = Y;
    e->pic.img.plane[1] = Y + (size_t) w * h;
    e->pic.img.plane[2] = e->pic.img.plane[1] + (size_t) (w / 2) * (h / 2);
    e->pic.i_pts = ptsUs;
    e->pic.i_type = X264_TYPE_AUTO;
    x264_nal_t *nals;
    int count;
    x264_picture_t out;
    int size = x264_encoder_encode(e->enc, &nals, &count, &e->pic, &out);
    if (size < 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), "x264 encode failed");
        return NULL;
    }
    if (size == 0) return NULL;
    fill_info(env, info, &out);
    return nals_to_array(env, nals, count, size);
}

/** Drains buffered frames after the last input. Returns null when nothing is left. */
JNIEXPORT jbyteArray JNICALL
Java_com_michaelkitticai_statuscompressor_X264_nativeFlush(JNIEnv *env, jclass clazz, jlong handle, jlongArray info) {
    Encoder *e = (Encoder *) (intptr_t) handle;
    while (x264_encoder_delayed_frames(e->enc) > 0) {
        x264_nal_t *nals;
        int count;
        x264_picture_t out;
        int size = x264_encoder_encode(e->enc, &nals, &count, NULL, &out);
        if (size < 0) return NULL;
        if (size > 0) {
            fill_info(env, info, &out);
            return nals_to_array(env, nals, count, size);
        }
    }
    return NULL;
}

JNIEXPORT void JNICALL
Java_com_michaelkitticai_statuscompressor_X264_nativeClose(JNIEnv *env, jclass clazz, jlong handle) {
    Encoder *e = (Encoder *) (intptr_t) handle;
    if (!e) return;
    x264_encoder_close(e->enc);
    free(e);
}
