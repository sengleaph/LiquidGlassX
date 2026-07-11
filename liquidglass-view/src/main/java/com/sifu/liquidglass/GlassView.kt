package com.sifu.liquidglass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.content.res.use

/**
 * A frosted "liquid glass" surface for the traditional Android View system.
 *
 * [GlassView] captures whatever's drawn behind it (preceding siblings under the same parent, or
 * an explicit [backdropSource]), blurs it with [RenderEffect], and then feeds that blurred image
 * into an AGSL [RuntimeShader] that does per-pixel refraction, chromatic dispersion, tint,
 * Fresnel rim highlight, and directional specular in one pass. Requires API 33+.
 *
 * Sibling ordering: place the [GlassView] *after* the content it should blur under the same
 * parent — Android draws children in declaration order — or set [backdropSource] to a specific
 * view id.
 */
class GlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    /** Blur radius applied to the backdrop, in pixels. */
    var frost: Float = 24f * density
        set(value) {
            field = value
            updateRenderEffect()
        }

    /** RGB color layered over the blurred backdrop; alpha channel ignored — use [tintAlpha]. */
    var tintColor: Int = 0xFFFFFF
        set(value) {
            field = value
            updateRenderEffect()
        }

    /** Opacity of [tintColor] over the blurred backdrop, in `0f..1f`. */
    var tintAlpha: Float = 0.2f
        set(value) {
            field = value
            updateRenderEffect()
        }

    /** Corner radius of the glass shape, in pixels. */
    var cornerRadius: Float = 14f * density
        set(value) {
            field = value
            rebuildClip()
            updateRenderEffect()
        }

    /** Strength of the Fresnel rim highlight, in `0f..1f`. */
    var edge: Float = 0.6f
        set(value) {
            field = value
            updateRenderEffect()
        }

    /** Contrast multiplier applied to the backdrop. `1f` is neutral. */
    var contrast: Float = 1f
        set(value) {
            field = value
            updateRenderEffect()
        }

    /** Saturation multiplier applied to the backdrop. `1f` is neutral. */
    var saturation: Float = 1.15f
        set(value) {
            field = value
            updateRenderEffect()
        }

    /** Optical refraction strength at the rim, in `0f..1f`. Bends the backdrop as if seen through a lens. */
    var refraction: Float = 0.65f
        set(value) {
            field = value
            updateRenderEffect()
        }

    /**
     * Falloff exponent for how quickly the refraction fades from the rim toward the center.
     * `1f` = linear falloff, higher = steeper (bend concentrated at rim), lower = softer (bend
     * extends deeper into the surface).
     */
    var curve: Float = 1.4f
        set(value) {
            field = value
            updateRenderEffect()
        }

    /** Chromatic dispersion at the rim, in `0f..1f`. Splits R/G/B sampling offsets. */
    var dispersion: Float = 0.2f
        set(value) {
            field = value
            updateRenderEffect()
        }

    /**
     * Direction the specular highlight light comes from, in degrees.
     * `0` = right, `90` = top, `135` = top-left (default), `180` = left, `270` = bottom.
     */
    var lightAngle: Float = 135f
        set(value) {
            field = value
            updateRenderEffect()
        }

    /**
     * When `true`, the backdrop is recaptured every frame via a pre-draw listener — needed when
     * the content behind is animating or scrolling.
     */
    var liveBlur: Boolean = false
        set(value) {
            field = value
            if (value) attachPreDrawSync() else detachPreDrawSync()
        }

    /** Explicit view to sample as the backdrop, instead of the auto sibling walk. */
    var backdropSource: View? = null
        set(value) {
            field = value
            invalidate()
        }

    private var backdropSourceId: Int = View.NO_ID
    private val node = RenderNode("glassBackdrop")
    private val clipPath = Path()
    private val runtimeShader = RuntimeShader(SHADER_SOURCE)
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    /** When true, setter-triggered [updateRenderEffect] calls are suppressed; used by [applyStyle]. */
    private var batching = false

    init {
        setWillNotDraw(false)
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
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (backdropSourceId != View.NO_ID && backdropSource == null) {
            backdropSource = rootView.findViewById(backdropSourceId)
        }
        if (liveBlur) attachPreDrawSync()
    }

    override fun onDetachedFromWindow() {
        detachPreDrawSync()
        node.discardDisplayList()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        node.setPosition(0, 0, w, h)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, w, h, cornerRadius)
            }
        }
        clipToOutline = true
        rebuildClip()
        updateRenderEffect()
    }

    private fun rebuildClip() {
        clipPath.reset()
        if (width <= 0 || height <= 0) return
        clipPath.addRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            cornerRadius, cornerRadius, Path.Direction.CW
        )
    }

    private fun updateRenderEffect() {
        if (batching) return
        if (width <= 0 || height <= 0) return

        val safeCorner = cornerRadius.coerceAtMost(minOf(width, height) / 2f - 0.5f)
        runtimeShader.apply {
            setFloatUniform("size", width.toFloat(), height.toFloat())
            setFloatUniform("cornerRadius", safeCorner)
            setFloatUniform("refraction", refraction.coerceIn(0f, 1f))
            setFloatUniform("curve", curve.coerceAtLeast(0.1f))
            setFloatUniform("dispersion", dispersion.coerceIn(0f, 1f))
            setFloatUniform("edge", edge.coerceIn(0f, 1f))
            setFloatUniform("contrast", contrast.coerceAtLeast(0f))
            setFloatUniform("saturation", saturation.coerceAtLeast(0f))
            setFloatUniform("tintAlpha", tintAlpha.coerceIn(0f, 1f))
            setFloatUniform("lightAngle", lightAngle)
            setFloatUniform(
                "tintRGB",
                ((tintColor shr 16) and 0xFF) / 255f,
                ((tintColor shr 8) and 0xFF) / 255f,
                (tintColor and 0xFF) / 255f
            )
        }

        // Two-pass Gaussian: composes to sqrt(r1^2 + r2^2) but with a visibly softer taper
        // than a single big blur. Splitting the frost radius into 0.7×0.7 (~sqrt(0.98)) keeps
        // the visual radius unchanged while trading nothing for extra smoothness.
        val f = frost.coerceAtLeast(0.01f)
        val r1 = f * 0.7f
        val r2 = f * 0.7f
        val blurA = RenderEffect.createBlurEffect(r1, r1, Shader.TileMode.CLAMP)
        val blurB = RenderEffect.createBlurEffect(r2, r2, Shader.TileMode.CLAMP)
        val doubleBlur = RenderEffect.createChainEffect(blurB, blurA)
        val shaderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "backdrop")
        val chain = RenderEffect.createChainEffect(shaderEffect, doubleBlur)
        node.setRenderEffect(chain)
        invalidate()
    }

    private fun captureBackdrop() {
        val parentGroup = parent as? ViewGroup ?: return
        val canvas = node.beginRecording()
        try {
            val myLoc = IntArray(2).also { getLocationInWindow(it) }
            val parentLoc = IntArray(2).also { parentGroup.getLocationInWindow(it) }
            canvas.translate(
                (parentLoc[0] - myLoc[0]).toFloat(),
                (parentLoc[1] - myLoc[1]).toFloat()
            )
            parentGroup.background?.draw(canvas)

            val source = backdropSource
            if (source != null) {
                source.draw(canvas)
            } else {
                val myIndex = parentGroup.indexOfChild(this)
                for (i in 0 until myIndex) {
                    parentGroup.getChildAt(i).draw(canvas)
                }
            }
        } finally {
            node.endRecording()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (canvas.isHardwareAccelerated && width > 0 && height > 0) {
            captureBackdrop()
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawRenderNode(node)
            canvas.restore()
        }
        super.onDraw(canvas)
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

    /**
     * Apply a full [GlassStyle] snapshot in one call. All setter-triggered render-effect
     * rebuilds are batched into a single rebuild at the end, so this is dramatically cheaper
     * than assigning each property individually.
     */
    fun applyStyle(style: GlassStyle) {
        batching = true
        try {
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
        } finally {
            batching = false
        }
        updateRenderEffect()
    }

    /** Snapshot the current appearance as a [GlassStyle]. */
    fun getStyle(): GlassStyle = GlassStyle(
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

    companion object {
        /**
         * AGSL fragment shader — takes the already-blurred backdrop and produces the final glass
         * surface. Everything Apple's Liquid Glass does per-pixel happens here:
         *
         *  - Rounded-rect signed distance field → know how far every pixel is from the edge
         *  - SDF gradient → outward surface normal
         *  - Refraction: sample the backdrop displaced outward at the rim (light bending inward)
         *  - Chromatic dispersion: R/G/B channels sampled with slightly different displacements
         *  - Contrast + saturation applied to the sampled color
         *  - Tint layered over
         *  - Fresnel rim highlight — bright at grazing angles, dark at normal
         *  - Directional specular — light from top-left, phong-ish exponent, concentrated at rim
         */
        private val SHADER_SOURCE = """
            uniform shader backdrop;
            uniform float2 size;
            uniform float cornerRadius;
            uniform float refraction;
            uniform float curve;
            uniform float dispersion;
            uniform float edge;
            uniform float contrast;
            uniform float saturation;
            uniform float tintAlpha;
            uniform float lightAngle;
            uniform float3 tintRGB;

            float sdRoundedBox(float2 p, float2 halfSize, float r) {
                float2 q = abs(p) - halfSize + float2(r);
                return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
            }

            half3 adjust(half3 c) {
                half gray = dot(c, half3(0.2126, 0.7152, 0.0722));
                half3 sat = mix(half3(gray), c, half(saturation));
                return (sat - half3(0.5)) * half(contrast) + half3(0.5);
            }

            // Interleaved Gradient Noise (Jimenez '14) — spatially smoother than white noise
            // at the same amplitude, so the dither breaks banding without visible grain.
            float ign(float2 p) {
                return fract(52.9829189 * fract(0.06711056 * p.x + 0.00583715 * p.y));
            }

            half4 main(float2 fragCoord) {
                float2 halfSize = size * 0.5;
                float2 p = fragCoord - halfSize;

                float d = sdRoundedBox(p, halfSize, cornerRadius);

                // 2.5-pixel AA — softer corners than 1.5px, still not visibly blurry.
                float coverage = smoothstep(1.25, -1.25, d);
                if (coverage <= 0.0) {
                    return half4(0.0);
                }

                // Outward normal from a 2-pixel SDF gradient.
                float eps = 2.0;
                float dxp = sdRoundedBox(p + float2(eps, 0.0), halfSize, cornerRadius);
                float dxn = sdRoundedBox(p - float2(eps, 0.0), halfSize, cornerRadius);
                float dyp = sdRoundedBox(p + float2(0.0, eps), halfSize, cornerRadius);
                float dyn = sdRoundedBox(p - float2(0.0, eps), halfSize, cornerRadius);
                float2 grad = float2(dxp - dxn, dyp - dyn);
                float glen = length(grad);
                float2 outward = glen > 0.0001 ? grad / glen : float2(0.0);
                // Tangent (perpendicular to outward) — used for smoothing refraction samples.
                float2 tangent = float2(-outward.y, outward.x);

                // Smoothstep the raw rim before the curve exponent — soft falloff throughout.
                float rimDepth = max(cornerRadius, 8.0) * 1.8;
                float rimRaw = clamp(1.0 - abs(d) / rimDepth, 0.0, 1.0);
                float rimSmooth = smoothstep(0.0, 1.0, rimRaw);
                float rim = pow(rimSmooth, max(curve, 0.2));

                // Refraction — displace outward at the rim, controlled by curve exponent.
                float bendMax = max(cornerRadius, 8.0) * 0.85;
                float2 disp = outward * (rim * refraction * bendMax);
                float dispScale = dispersion * rim * 12.0;

                // 3-tap Gaussian sampling per channel, spread along the surface tangent —
                // averages out sample-boundary artifacts in the refracted content. Center
                // weight 0.5, sides 0.25 each. Costs 9 backdrop.eval() calls per pixel total.
                float2 tOff = tangent * 1.4;
                float2 uvR = fragCoord + disp + outward * dispScale;
                float2 uvG = fragCoord + disp;
                float2 uvB = fragCoord + disp - outward * dispScale;
                half r_c = backdrop.eval(uvR + tOff).r * 0.25
                         + backdrop.eval(uvR).r * 0.5
                         + backdrop.eval(uvR - tOff).r * 0.25;
                half g_c = backdrop.eval(uvG + tOff).g * 0.25
                         + backdrop.eval(uvG).g * 0.5
                         + backdrop.eval(uvG - tOff).g * 0.25;
                half b_c = backdrop.eval(uvB + tOff).b * 0.25
                         + backdrop.eval(uvB).b * 0.5
                         + backdrop.eval(uvB - tOff).b * 0.25;

                half3 col = adjust(half3(r_c, g_c, b_c));

                // Tint layer.
                half3 tinted = mix(col, half3(tintRGB), half(tintAlpha));

                // Fresnel via double-smoothstep — no visible inflection like pow() has.
                float fresnel = smoothstep(0.0, 0.7, rimSmooth) *
                                smoothstep(0.0, 1.0, rimSmooth) * edge;
                half3 result = mix(tinted, half3(1.0), half(fresnel * 0.7));

                // Two-tap specular: tight phong core + soft halo → bloomy, Apple-style highlight.
                // Light direction from lightAngle: 0° = right, 90° = top, 135° = top-left.
                float lightRad = radians(lightAngle);
                float2 lightDir = float2(cos(lightRad), -sin(lightRad));
                float ndotl = max(dot(outward, lightDir), 0.0);
                float coreDot = pow(ndotl, 10.0);
                float haloDot = pow(ndotl, 3.5);
                float specular = (coreDot * 1.1 + haloDot * 0.35) *
                                 (0.4 + refraction * 0.6) * rimSmooth;
                result += half3(specular * 0.85);

                // Sub-perceptual dither via IGN — smoother spatial distribution than white noise.
                float dither = (ign(fragCoord) - 0.5) * (2.0 / 255.0);
                result += half3(dither);

                result = clamp(result, half3(0.0), half3(1.0));
                // Premultiplied output so the AA edge blends cleanly.
                return half4(result * coverage, coverage);
            }
        """.trimIndent()
    }
}
