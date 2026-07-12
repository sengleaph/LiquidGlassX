package com.sifu.liquidglass.gl

import android.opengl.GLES20
import java.nio.FloatBuffer

/**
 * Single pass of a separable Gaussian blur — direction chosen by [uTexelStep]. Run twice per
 * frame (horizontal then vertical) into ping-pong FBOs to blur the backdrop.
 */
internal class GaussianBlurProgram {

    private val program: Int =
        GlUtil.buildProgram(GlShaders.PASSTHROUGH_VERTEX, GlShaders.BLUR_FRAGMENT)

    private val aPos = GLES20.glGetAttribLocation(program, "aPos")
    private val aTex = GLES20.glGetAttribLocation(program, "aTex")
    private val uSrc = GLES20.glGetUniformLocation(program, "uSrc")
    private val uTexelStep = GLES20.glGetUniformLocation(program, "uTexelStep")
    private val uSigma = GLES20.glGetUniformLocation(program, "uSigma")

    // Non-Y-flipped tex coords — FBO ping-pong stays in native GL orientation. The final
    // composite (PassthroughProgram) handles the flip when rendering to the window.
    private val vertexBuf: FloatBuffer = GlUtil.floatBuffer(
        floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )
    )

    fun draw(srcTex: Int, texelStepX: Float, texelStepY: Float, sigma: Float) {
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
        GLES20.glUniform2f(uTexelStep, texelStepX, texelStepY)
        GLES20.glUniform1f(uSigma, sigma)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    fun release() {
        GLES20.glDeleteProgram(program)
    }
}
