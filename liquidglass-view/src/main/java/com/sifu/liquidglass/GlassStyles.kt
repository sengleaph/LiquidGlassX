package com.sifu.liquidglass

import android.content.Context

/**
 * Curated preset [GlassStyle]s covering common visual archetypes. Each preset takes a [Context]
 * so it can convert dp-based dimensions (frost radius, corner radius) into pixels against the
 * current display metrics.
 *
 * ```
 * glassView.applyStyle(GlassStyles.Frosted(context))
 * glassView.applyStyle(GlassStyles.Vivid(context).copy(tintAlpha = 0.25f))  // tweak
 * ```
 */
public object GlassStyles {

    /** Classic Apple-style frosted glass — soft, neutral, high edge highlight. */
    public fun Frosted(context: Context): GlassStyle = GlassStyle(
        frost = dp(context, 24f),
        tintColor = 0xFFFFFF,
        tintAlpha = 0.15f,
        cornerRadius = dp(context, 28f),
        edge = 0.7f,
        contrast = 1.0f,
        saturation = 1.15f,
        refraction = 0.7f,
        curve = 1.4f,
        dispersion = 0.15f,
        lightAngle = 135f,
    )

    /** Vibrant, high-saturation glass — hero cards and media apps. */
    public fun Vivid(context: Context): GlassStyle = GlassStyle(
        frost = dp(context, 30f),
        tintColor = 0xFFFFFF,
        tintAlpha = 0.12f,
        cornerRadius = dp(context, 32f),
        edge = 0.85f,
        contrast = 1.1f,
        saturation = 1.4f,
        refraction = 0.9f,
        curve = 1.6f,
        dispersion = 0.3f,
        lightAngle = 135f,
    )

    /** Barely-there frosted overlay for lightweight UI over busy backgrounds. */
    public fun Subtle(context: Context): GlassStyle = GlassStyle(
        frost = dp(context, 16f),
        tintColor = 0xFFFFFF,
        tintAlpha = 0.08f,
        cornerRadius = dp(context, 20f),
        edge = 0.4f,
        contrast = 1.0f,
        saturation = 1.05f,
        refraction = 0.3f,
        curve = 1.0f,
        dispersion = 0.05f,
        lightAngle = 135f,
    )

    /** Dark, moody glass — bottom bars and toolbars over photos. */
    public fun Dark(context: Context): GlassStyle = GlassStyle(
        frost = dp(context, 32f),
        tintColor = 0x0A0A12,
        tintAlpha = 0.35f,
        cornerRadius = dp(context, 36f),
        edge = 0.5f,
        contrast = 1.05f,
        saturation = 1.1f,
        refraction = 0.7f,
        curve = 1.5f,
        dispersion = 0.1f,
        lightAngle = 135f,
    )

    /** Maximum dispersion — visible rainbow at the rim, for playful UIs. */
    public fun Rainbow(context: Context): GlassStyle = GlassStyle(
        frost = dp(context, 28f),
        tintColor = 0xFFFFFF,
        tintAlpha = 0.1f,
        cornerRadius = dp(context, 40f),
        edge = 0.9f,
        contrast = 1.2f,
        saturation = 1.3f,
        refraction = 1.0f,
        curve = 2.0f,
        dispersion = 0.6f,
        lightAngle = 135f,
    )

    private fun dp(context: Context, value: Float): Float = GlassStyle.dp(context, value)
}
