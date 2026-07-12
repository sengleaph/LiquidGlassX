package com.sifu.liquidglass.gl

import android.opengl.GLES20

/**
 * Two RGBA color-attached framebuffers of matching size, used as ping-pong targets for the
 * separable Gaussian blur. Reallocated on size change.
 */
internal class FboPair {

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    private val textures = IntArray(2)
    private val framebuffers = IntArray(2)

    fun ensureSize(w: Int, h: Int) {
        if (w == width && h == height && framebuffers[0] != 0) return
        release()
        width = w
        height = h

        GLES20.glGenTextures(2, textures, 0)
        GLES20.glGenFramebuffers(2, framebuffers, 0)

        for (i in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[i])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, textures[i], 0
            )
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun texture(index: Int) = textures[index]

    fun framebuffer(index: Int) = framebuffers[index]

    fun release() {
        if (framebuffers[0] != 0) GLES20.glDeleteFramebuffers(2, framebuffers, 0)
        if (textures[0] != 0) GLES20.glDeleteTextures(2, textures, 0)
        framebuffers[0] = 0; framebuffers[1] = 0
        textures[0] = 0; textures[1] = 0
        width = 0; height = 0
    }
}
