package com.sifu.liquidglass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.util.AttributeSet
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.content.res.use
import com.sifu.liquidglass.gl.BackdropCapturer
import com.sifu.liquidglass.gl.GlassRenderer

/**
 * A liquid-glass surface rendered on **OpenGL ES 2.0** via a child [TextureView]. Captures the
 * views behind this container into a Bitmap, uploads it as a GL texture, and paints it with a
 * fragment-shader pipeline that will grow across phases into full Snell refraction, Fresnel
 * rim, Blinn-Phong specular, and chromatic dispersion.
 *
 * **Phase 1** wires up the GL pipeline end-to-end with only pass-through + tint. Sliders for
 * refraction / dispersion / curve are stored but currently no-op until the shader upgrades in
 * later phases.
 *
 * Requires API 31+.
 */
public class GlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    // ---- Public properties (Prismal-style parameter set, API-compatible with Phase 0) ------

    public var frost: Float = 24f * density
        set(value) { field = value; renderer.frost = value; invalidateGlass() }

    public var tintColor: Int = 0xFFFFFF
        set(value) { field = value; renderer.tintColor = value; invalidateGlass() }

    public var tintAlpha: Float = 0.15f
        set(value) { field = value; renderer.tintAlpha = value; invalidateGlass() }

    public var cornerRadius: Float = 28f * density
        set(value) { field = value; renderer.cornerRadius = value; rebuildOutline(); invalidateGlass() }

    public var edge: Float = 0.6f
        set(value) { field = value; renderer.edge = value; invalidateGlass() }

    public var contrast: Float = 1f
        set(value) { field = value; invalidateGlass() }

    public var saturation: Float = 1.15f
        set(value) { field = value; invalidateGlass() }

    public var refraction: Float = 0.65f
        set(value) { field = value; renderer.refraction = value; invalidateGlass() }

    public var curve: Float = 1.4f
        set(value) { field = value; renderer.curve = value; invalidateGlass() }

    public var dispersion: Float = 0.2f
        set(value) { field = value; renderer.dispersion = value; invalidateGlass() }

    public var lightAngle: Float = 135f
        set(value) { field = value; renderer.lightAngleDeg = value; invalidateGlass() }

    /** When true, the backdrop is recaptured every frame from a pre-draw listener. */
    public var liveBlur: Boolean = false
        set(value) {
            field = value
            if (value) attachPreDrawSync() else detachPreDrawSync()
        }

    /** Down-sample factor for the captured backdrop before uploading to GL. `1f` = full-res. */
    public var downsampleFactor: Float = 0.5f
        set(value) {
            field = value.coerceIn(0.1f, 1f)
            renderer.downsampleFactor = field
            invalidateGlass()
        }

    // ---- Internals ----------------------------------------------------------

    private val renderer = GlassRenderer()
    private val capturer = BackdropCapturer(this)
    private val textureView: TextureView = TextureView(context).also {
        it.isOpaque = false
        it.surfaceTextureListener = renderer
        addView(it, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var backdropSourceId: Int = View.NO_ID

    init {
        clipChildren = true
        context.obtainStyledAttributes(attrs, R.styleable.GlassView, defStyleAttr, 0).use { a ->
            frost = a.getDimension(R.styleable.GlassView_frost, frost)
            tintColor = a.getColor(R.styleable.GlassView_tintColor, tintColor)
            tintAlpha = a.getFloat(R.styleable.GlassView_tintAlpha, tintAlpha)
            cornerRadius = a.getDimension(R.styleable.GlassView_cornerRadius, cornerRadius)
            edge = a.getFloat(R.styleable.GlassView_edge, edge)
            contrast = a.getFloat(R.styleable.GlassView_contrast, contrast)
            saturation = a.getFloat(R.styleable.GlassView_saturation, saturation)
            refraction = a.getFloat(R.styleable.GlassView_refraction, refraction)
            curve = a.getFloat(R.styleable.GlassView_curve, curve)
            dispersion = a.getFloat(R.styleable.GlassView_dispersion, dispersion)
            lightAngle = a.getFloat(R.styleable.GlassView_lightAngle, lightAngle)
            liveBlur = a.getBoolean(R.styleable.GlassView_liveBlur, liveBlur)
            backdropSourceId = a.getResourceId(R.styleable.GlassView_backdropSource, View.NO_ID)
        }
        renderer.tintColor = tintColor
        renderer.tintAlpha = tintAlpha
        renderer.edge = edge
        renderer.frost = frost
        renderer.downsampleFactor = downsampleFactor
        renderer.cornerRadius = cornerRadius
        renderer.refraction = refraction
        renderer.curve = curve
        renderer.dispersion = dispersion
        renderer.lightAngleDeg = lightAngle
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (liveBlur) attachPreDrawSync()
    }

    override fun onDetachedFromWindow() {
        detachPreDrawSync()
        capturer.release()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildOutline()
        invalidateGlass()
    }

    override fun dispatchDraw(canvas: Canvas) {
        captureAndSchedule()
        super.dispatchDraw(canvas)
    }

    private fun captureAndSchedule() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        val scale = downsampleFactor.coerceIn(0.1f, 1f)
        val bw = (w * scale).toInt().coerceAtLeast(1)
        val bh = (h * scale).toInt().coerceAtLeast(1)
        val bmp = capturer.capture(bw, bh, scale) ?: return
        renderer.scheduleFrame(bmp)
    }

    private fun invalidateGlass() {
        // dispatchDraw already reschedules a capture on any invalidate; this just kicks it.
        invalidate()
    }

    private fun rebuildOutline() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, w, h, cornerRadius)
            }
        }
        clipToOutline = true
    }

    private fun attachPreDrawSync() {
        if (preDrawListener != null || !isAttachedToWindow) return
        preDrawListener = ViewTreeObserver.OnPreDrawListener {
            invalidate()
            true
        }.also { viewTreeObserver.addOnPreDrawListener(it) }
    }

    private fun detachPreDrawSync() {
        preDrawListener?.let { viewTreeObserver.removeOnPreDrawListener(it) }
        preDrawListener = null
    }

    // ---- Preset / snapshot API ---------------------------------------------

    public fun applyStyle(style: GlassStyle) {
        frost = style.frost
        tintColor = style.tintColor
        tintAlpha = style.tintAlpha
        cornerRadius = style.cornerRadius
        edge = style.edge
        contrast = style.contrast
        saturation = style.saturation
        refraction = style.refraction
        curve = style.curve
        dispersion = style.dispersion
        lightAngle = style.lightAngle
    }

    public fun getStyle(): GlassStyle = GlassStyle(
        frost = frost,
        tintColor = tintColor,
        tintAlpha = tintAlpha,
        cornerRadius = cornerRadius,
        edge = edge,
        contrast = contrast,
        saturation = saturation,
        refraction = refraction,
        curve = curve,
        dispersion = dispersion,
        lightAngle = lightAngle,
    )
}
