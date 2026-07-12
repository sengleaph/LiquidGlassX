package com.sifu.liquidglass.gl

import android.opengl.GLES20
import java.nio.FloatBuffer

/**
 * Final composite pass — samples the (usually blurred) backdrop through the liquid-glass
 * fragment shader: SDF rounded-rect, arc height field, Snell refraction, chromatic
 * dispersion, Blinn-Phong specular, Schlick Fresnel rim, tint, brightness.
 */
internal class GlassCompositeProgram {

    private val program: Int =
        GlUtil.buildProgram(GlShaders.PASSTHROUGH_VERTEX, GlShaders.COMPOSITE_FRAGMENT)

    private val aPos = GLES20.glGetAttribLocation(program, "aPos")
    private val aTex = GLES20.glGetAttribLocation(program, "aTex")
    private val uSrc = GLES20.glGetUniformLocation(program, "uSrc")
    private val uResolution = GLES20.glGetUniformLocation(program, "uResolution")
    private val uGlassSize = GLES20.glGetUniformLocation(program, "uGlassSize")
    private val uCornerRadius = GLES20.glGetUniformLocation(program, "uCornerRadius")
    private val uIor = GLES20.glGetUniformLocation(program, "uIor")
    private val uNormalStrength = GLES20.glGetUniformLocation(program, "uNormalStrength")
    private val uGlassThickness = GLES20.glGetUniformLocation(program, "uGlassThickness")
    private val uTransitionWidth = GLES20.glGetUniformLocation(program, "uTransitionWidth")
    private val uChromaticAberration = GLES20.glGetUniformLocation(program, "uChromaticAberration")
    private val uLightDir = GLES20.glGetUniformLocation(program, "uLightDir")
    private val uSpecular = GLES20.glGetUniformLocation(program, "uSpecular")
    private val uShininess = GLES20.glGetUniformLocation(program, "uShininess")
    private val uRimStrength = GLES20.glGetUniformLocation(program, "uRimStrength")
    private val uTint = GLES20.glGetUniformLocation(program, "uTint")
    private val uBrightness = GLES20.glGetUniformLocation(program, "uBrightness")

    // Y-flipped tex coords so the backdrop displays right-side-up. Vertex XY is used in the
    // shader to derive `vShape` for the SDF.
    private val vertexBuf: FloatBuffer = GlUtil.floatBuffer(
        floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
        )
    )

    fun draw(
        srcTex: Int,
        resolutionPx: FloatArray,
        glassSizePx: FloatArray,
        cornerRadiusPx: Float,
        ior: Float,
        normalStrength: Float,
        glassThickness: Float,
        transitionWidthPx: Float,
        chromaticAberration: Float,
        lightDir: FloatArray,
        specular: Float,
        shininess: Float,
        rimStrength: Float,
        tintRgba: FloatArray,
        brightness: Float,
    ) {
        GLES20.glUseProgram(program)

        vertexBuf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, vertexBuf)
        vertexBuf.position(2)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, vertexBuf)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
        GLES20.glUniform1i(uSrc, 0)
        GLES20.glUniform2fv(uResolution, 1, resolutionPx, 0)
        GLES20.glUniform2fv(uGlassSize, 1, glassSizePx, 0)
        GLES20.glUniform1f(uCornerRadius, cornerRadiusPx)
        GLES20.glUniform1f(uIor, ior)
        GLES20.glUniform1f(uNormalStrength, normalStrength)
        GLES20.glUniform1f(uGlassThickness, glassThickness)
        GLES20.glUniform1f(uTransitionWidth, transitionWidthPx)
        GLES20.glUniform1f(uChromaticAberration, chromaticAberration)
        GLES20.glUniform2fv(uLightDir, 1, lightDir, 0)
        GLES20.glUniform1f(uSpecular, specular)
        GLES20.glUniform1f(uShininess, shininess)
        GLES20.glUniform1f(uRimStrength, rimStrength)
        GLES20.glUniform4fv(uTint, 1, tintRgba, 0)
        GLES20.glUniform1f(uBrightness, brightness)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisable(GLES20.GL_BLEND)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    fun release() {
        GLES20.glDeleteProgram(program)
    }
}
