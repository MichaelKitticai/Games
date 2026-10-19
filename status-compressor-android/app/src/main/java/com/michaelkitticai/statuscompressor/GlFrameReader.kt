package com.michaelkitticai.statuscompressor

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch

/**
 * An input [Surface] whose frames come back as RGBA pixels, top row first.
 *
 * Unlike ImageReader (which drops frames whenever its reader falls behind), this reads through a
 * SurfaceTexture, the way MediaCodec's input surfaces work: the renderer waits for us instead, so
 * every frame arrives. Frames are delivered in order on this reader's own thread.
 */
class GlFrameReader(
    private val width: Int,
    private val height: Int,
    private val onFrame: (rgba: ByteBuffer, ptsUs: Long) -> Unit,
) {
    private val thread = HandlerThread("x264-gl").apply { start() }
    private val handler = Handler(thread.looper)

    private lateinit var display: EGLDisplay
    private lateinit var context: EGLContext
    private lateinit var pbuffer: EGLSurface
    private lateinit var surfaceTexture: SurfaceTexture
    private var program = 0
    private var oesTexture = 0
    private var framebuffer = 0
    private var colorTexture = 0
    private val pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
    private val stMatrix = FloatArray(16)
    var framesRead = 0
        private set
    // Upside-down quad (x, y, u, v), so glReadPixels (which starts at the bottom row) returns the top
    // row first. A field, not a local: GL reads this memory on every draw, so it must not be freed.
    private val quad = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f, -1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f))
        position(0)
    }

    lateinit var surface: Surface
        private set

    init {
        val ready = CountDownLatch(1)
        var failure: Throwable? = null
        handler.post {
            try {
                setUp()
            } catch (t: Throwable) {
                failure = t
            }
            ready.countDown()
        }
        ready.await()
        failure?.let { throw it }
    }

    /** Runs [block] on the reader thread after every frame rendered so far has been delivered. */
    fun afterPendingFrames(block: () -> Unit) {
        // Frame callbacks are queued on this thread's handler as each frame is rendered, so a
        // message posted now runs after all of them.
        handler.post(block)
    }

    fun release() {
        handler.post {
            surface.release()
            surfaceTexture.release()
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteTextures(2, intArrayOf(oesTexture, colorTexture), 0)
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, pbuffer)
            EGL14.eglDestroyContext(display, context)
            // No eglTerminate: the display is shared with Media3's own GL thread in this process.
        }
        thread.quitSafely()
        thread.join(2000) // So no frame is still being converted when the encoder is freed.
    }

    private fun setUp() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        EGL14.eglChooseConfig(
            display,
            intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            ),
            0, configs, 0, 1, count, 0,
        )
        check(count[0] > 0) { "No EGL config" }
        context = EGL14.eglCreateContext(
            display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        pbuffer = EGL14.eglCreatePbufferSurface(
            display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        check(EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)) { "eglMakeCurrent failed" }

        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        oesTexture = textures[0]
        colorTexture = textures[1]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colorTexture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        val fbs = IntArray(1)
        GLES20.glGenFramebuffers(1, fbs, 0)
        framebuffer = fbs[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, colorTexture, 0,
        )
        check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            "Framebuffer incomplete"
        }

        program = buildProgram()
        GLES20.glUseProgram(program)
        val aPos = GLES20.glGetAttribLocation(program, "aPos")
        val aTex = GLES20.glGetAttribLocation(program, "aTex")
        quad.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aPos)
        quad.position(2)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)
        GLES20.glViewport(0, 0, width, height)

        surfaceTexture = SurfaceTexture(oesTexture).apply {
            setDefaultBufferSize(width, height)
            setOnFrameAvailableListener({ drawFrame() }, handler)
        }
        surface = Surface(surfaceTexture)
    }

    private fun drawFrame() {
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(stMatrix)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uSt"), 1, false, stMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        pixels.clear()
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        framesRead++
        if (framesRead == 1 || framesRead % 60 == 0) Log.i(TAG, "x264 input: $framesRead frames read")
        onFrame(pixels, surfaceTexture.timestamp / 1000)
    }

    private fun buildProgram(): Int {
        fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { GLES20.glGetShaderInfoLog(it) }
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, VERTEX))
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
        GLES20.glLinkProgram(p)
        return p
    }

    private companion object {
        const val VERTEX = """
            attribute vec4 aPos;
            attribute vec4 aTex;
            uniform mat4 uSt;
            varying vec2 vTex;
            void main() {
              gl_Position = aPos;
              vTex = (uSt * aTex).xy;
            }
        """
        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTex;
            varying vec2 vTex;
            void main() {
              gl_FragColor = texture2D(uTex, vTex);
            }
        """
    }
}
