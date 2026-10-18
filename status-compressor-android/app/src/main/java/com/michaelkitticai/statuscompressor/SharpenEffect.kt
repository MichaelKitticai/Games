package com.michaelkitticai.statuscompressor

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Light unsharp mask, run after the final resize. Downscaling softens edges a little; this puts
 * the crispness back so it survives WhatsApp's re-encode. Small differences (noise, grain) are
 * left alone so they don't turn into more detail for the encoder to fight.
 */
@OptIn(UnstableApi::class)
class SharpenEffect(private val amount: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        SharpenShaderProgram(amount, useHdr)
}

@OptIn(UnstableApi::class)
private class SharpenShaderProgram(private val amount: Float, useHdr: Boolean) :
    BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1) {

    private val program = GlProgram(VERTEX, FRAGMENT)
    private var width = 1
    private var height = 1

    init {
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
        program.setFloatsUniform("uTexelSize", floatArrayOf(1f / width, 1f / height))
        program.setFloatUniform("uAmount", amount)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    override fun release() {
        super.release()
        program.delete()
    }

    companion object {
        private const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vTexCoords;
            void main() {
              gl_Position = aFramePosition;
              vTexCoords = aFramePosition.xy * 0.5 + 0.5;
            }
        """

        private const val FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexelSize;
            uniform float uAmount;
            varying vec2 vTexCoords;
            void main() {
              vec4 c = texture2D(uTexSampler, vTexCoords);
              vec2 d = uTexelSize;
              vec3 blur = (
                  texture2D(uTexSampler, vTexCoords + vec2(-d.x, 0.0)).rgb
                + texture2D(uTexSampler, vTexCoords + vec2( d.x, 0.0)).rgb
                + texture2D(uTexSampler, vTexCoords + vec2(0.0, -d.y)).rgb
                + texture2D(uTexSampler, vTexCoords + vec2(0.0,  d.y)).rgb) * 0.25;
              vec3 diff = c.rgb - blur;
              // Threshold: ignore differences under ~1.5% (sensor noise), ramp in above it.
              float edge = smoothstep(0.012, 0.04, max(abs(diff.r), max(abs(diff.g), abs(diff.b))));
              gl_FragColor = vec4(clamp(c.rgb + diff * uAmount * edge, 0.0, 1.0), c.a);
            }
        """
    }
}
