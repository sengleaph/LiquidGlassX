package com.sifu.liquidglass.gl

import android.opengl.GLES20
import java.nio.FloatBuffer

/**
 * Pass-through blit — copies `srcTex` into the currently-bound FBO. Combined with rendering
 * into a half-size target and `GL_LINEAR` filtering, one draw call = one 2×2 downsample step.
 */
internal class DownsampleProgram {

    private val program: Int =
        GlUtil.buildProgram(GlShaders.PASSTHROUGH_VERTEX, GlShaders.DOWNSAMPLE_FRAGMENT)

    private val aPos = GLES20.glGetAttribLocation(program, "aPos")
    private val aTex = GLES20.glGetAttribLocation(program, "aTex")
    private val uSrc = GLES20.glGetUniformLocation(program, "uSrc")

    // Non-Y-flipped tex coords — chain stays in native GL orientation, matching the blur pass.
    private val vertexBuf: FloatBuffer = GlUtil.floatBuffer(
        floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )
    )

    fun draw(srcTex: Int) {
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

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    fun release() {
        GLES20.glDeleteProgram(program)
    }
}
