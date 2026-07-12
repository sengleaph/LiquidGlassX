package com.sifu.liquidglass.gl

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Owns the EGL context, render thread, and shader programs. Frames arrive from
 * [BackdropCapturer] via [scheduleFrame]; each frame is uploaded, blurred through ping-pong
 * FBOs, then composited through the liquid-glass shader.
 */
internal class GlassRenderer : TextureView.SurfaceTextureListener {

    // Style inputs mirror GlassView's public API. Setters may be called from any thread.
    @Volatile var tintColor: Int = 0xFFFFFF
    @Volatile var tintAlpha: Float = 0.15f
    @Volatile var frost: Float = 24f
    @Volatile var downsampleFactor: Float = 0.5f
    @Volatile var cornerRadius: Float = 28f
    @Volatile var refraction: Float = 0.7f   // → drives normal strength + glass thickness
    @Volatile var dispersion: Float = 0.15f  // → chromatic aberration
    @Volatile var edge: Float = 0.6f         // → rim strength
    @Volatile var curve: Float = 1.4f        // → shininess exponent scale
    @Volatile var lightAngleDeg: Float = 135f
    @Volatile var brightness: Float = 1f

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var eglCore: EglCore? = null
    private var eglSurface: EGLSurface? = null
    private var composite: GlassCompositeProgram? = null
    private var blur: GaussianBlurProgram? = null
    private var downsample: DownsampleProgram? = null
    private var fboChain: FboChain? = null
    private var backdropTex: Int = 0
    private var backdropWidth: Int = 0
    private var backdropHeight: Int = 0

    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    private var pendingBackdrop: Bitmap? = null
    private val lock = Any()

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        val t = HandlerThread("GlassRenderer").also { it.start() }
        thread = t
        val h = Handler(t.looper).also { handler = it }
        h.post {
            val core = EglCore()
            eglCore = core
            eglSurface = core.createWindowSurface(surface)
            core.makeCurrent(eglSurface!!)
            composite = GlassCompositeProgram()
            blur = GaussianBlurProgram()
            downsample = DownsampleProgram()
            fboChain = FboChain()
            backdropTex = GlUtil.genTexture()
            paint()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        handler?.post { paint() }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val h = handler
        val t = thread
        handler = null
        thread = null
        if (h != null) {
            h.post {
                composite?.release()
                composite = null
                blur?.release()
                blur = null
                downsample?.release()
                downsample = null
                fboChain?.release()
                fboChain = null
                if (backdropTex != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(backdropTex), 0)
                    backdropTex = 0
                }
                eglSurface?.let { eglCore?.releaseSurface(it) }
                eglSurface = null
                eglCore?.release()
                eglCore = null
            }
        }
        t?.quitSafely()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    fun scheduleFrame(backdrop: Bitmap) {
        val h = handler ?: return
        synchronized(lock) { pendingBackdrop = backdrop }
        h.removeCallbacks(paintRunnable)
        h.post(paintRunnable)
    }

    private val paintRunnable = Runnable {
        val bmp: Bitmap? = synchronized(lock) {
            pendingBackdrop.also { pendingBackdrop = null }
        }
        if (bmp != null && !bmp.isRecycled) {
            uploadBackdrop(bmp)
        }
        paint()
    }

    private fun uploadBackdrop(bmp: Bitmap) {
        if (backdropTex == 0) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backdropTex)
        // Lock while GLUtils.texImage2D reads pixels so the UI thread can't erase & repaint
        // the bitmap mid-copy. Same monitor as BackdropCapturer.capture.
        synchronized(bmp) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        }
        backdropWidth = bmp.width
        backdropHeight = bmp.height
    }

    private fun paint() {
        val core = eglCore ?: return
        val surface = eglSurface ?: return
        val comp = composite ?: return
        val blur = blur ?: return
        val downsample = downsample ?: return
        val chain = fboChain ?: return
        core.makeCurrent(surface)

        val sourceTex = if (backdropWidth > 0 && backdropHeight > 0) {
            runBlurPasses(blur, downsample, chain)
        } else {
            backdropTex
        }

        // Composite to the window surface.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Map GlassStyle sliders → shader uniforms.
        val tintR = Color.red(tintColor) / 255f
        val tintG = Color.green(tintColor) / 255f
        val tintB = Color.blue(tintColor) / 255f
        val tintA = tintAlpha.coerceIn(0f, 1f)

        val angleRad = Math.toRadians(lightAngleDeg.toDouble())
        val lightDir = floatArrayOf(cos(angleRad).toFloat(), sin(angleRad).toFloat())

        val normalStrength = refraction * 6.0f
        val glassThickness = 40f + refraction * 80f
        val chromaticAberration = dispersion * 12f
        val rimStrength = edge * 2.4f
        val shininess = (4f + curve * 24f)
        val specular = 3.2f

        val minDim = minOf(surfaceWidth, surfaceHeight).toFloat()
        val transitionWidth = (minDim * 0.28f).coerceAtLeast(4f)

        comp.draw(
            srcTex = sourceTex,
            resolutionPx = floatArrayOf(
                (if (backdropWidth > 0) backdropWidth else surfaceWidth).toFloat(),
                (if (backdropHeight > 0) backdropHeight else surfaceHeight).toFloat()
            ),
            glassSizePx = floatArrayOf(surfaceWidth.toFloat(), surfaceHeight.toFloat()),
            cornerRadiusPx = cornerRadius,
            ior = 1.42f,
            normalStrength = normalStrength,
            glassThickness = glassThickness,
            transitionWidthPx = transitionWidth,
            chromaticAberration = chromaticAberration,
            lightDir = lightDir,
            specular = specular,
            shininess = shininess,
            rimStrength = rimStrength,
            tintRgba = floatArrayOf(tintR, tintG, tintB, tintA),
            brightness = brightness,
        )

        core.swapBuffers(surface)
    }

    /**
     * Dual-filter multi-level blur. For a target screen-space σ, we pick the smallest FBO
     * level that keeps σ under the 15-tap kernel's honest limit ([SIGMA_CAP]), downsample
     * the backdrop through the chain to that level, then run a single H+V Gaussian there.
     * The composite pass' `GL_LINEAR` upsampling smooths the result back to screen size,
     * so the visible blur grows by 2× per level for effectively free.
     */
    private fun runBlurPasses(
        blur: GaussianBlurProgram,
        downsample: DownsampleProgram,
        chain: FboChain,
    ): Int {
        val screenSigma = frost * downsampleFactor.coerceIn(0.1f, 1f)
        if (screenSigma <= 0.5f) return backdropTex

        val level = if (screenSigma <= SIGMA_CAP) 0
                    else ceil(ln(screenSigma / SIGMA_CAP) / LN2).toInt().coerceAtMost(MAX_LEVELS)
        val sigmaAtLevel = (screenSigma / (1 shl level)).coerceIn(0f, SIGMA_CAP)

        chain.ensureSize(backdropWidth, backdropHeight, level + 1)

        // Downsample chain: backdropTex → pair(1).slot(0) → … → pair(level).slot(0).
        var src = backdropTex
        for (i in 1..level) {
            val pair = chain.pair(i)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, pair.framebuffer(0))
            GLES20.glViewport(0, 0, pair.width, pair.height)
            downsample.draw(src)
            src = pair.texture(0)
        }

        if (sigmaAtLevel <= 0.5f) return src

        // H+V ping-pong Gaussian at the smallest level: src → slot(1) → slot(0).
        val finalPair = chain.pair(level)
        val w = finalPair.width
        val h = finalPair.height

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, finalPair.framebuffer(1))
        GLES20.glViewport(0, 0, w, h)
        blur.draw(src, 1f / w, 0f, sigmaAtLevel)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, finalPair.framebuffer(0))
        GLES20.glViewport(0, 0, w, h)
        blur.draw(finalPair.texture(1), 0f, 1f / h, sigmaAtLevel)

        return finalPair.texture(0)
    }

    private companion object {
        // 15-tap kernel covers about ±3σ honestly; capping at 8 keeps the tails from being lost.
        const val SIGMA_CAP = 8f
        // Each level halves both dimensions, so 5 = 32× downsample = σ up to ~256 pixels.
        const val MAX_LEVELS = 5
        val LN2 = ln(2.0).toFloat()
    }
}
