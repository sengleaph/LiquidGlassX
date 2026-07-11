package com.sifu.liquidglass

import android.content.Context
import android.util.TypedValue

/**
 * Immutable snapshot of every [GlassView] appearance property. Pass to [GlassView.applyStyle]
 * to reconfigure a view in one call, hold as a design-system token to share the same look
 * across many views, or `.copy(...)` an existing preset to derive a variant.
 *
 * See [GlassStyles] for ready-to-use presets.
 *
 * ### Property units
 * - [frost] and [cornerRadius] are in **pixels**. Use [GlassStyle.Companion.dp] to convert from dp.
 * - [tintColor] is an RGB int (`0xRRGGBB`); its alpha channel is ignored — use [tintAlpha].
 * - [tintAlpha], [edge], [refraction], [dispersion] are in `0f..1f`.
 * - [contrast], [saturation], [curve] are unbounded, `1f` = neutral.
 * - [lightAngle] is in degrees: `0` = light from right, `90` = top, `135` = top-left (default),
 *   `180` = left, `270` = bottom.
 */
data class GlassStyle(
    val frost: Float,
    val tintColor: Int,
    val tintAlpha: Float,
    val cornerRadius: Float,
    val edge: Float,
    val contrast: Float,
    val saturation: Float,
    val refraction: Float,
    val curve: Float,
    val dispersion: Float,
    val lightAngle: Float = 135f,
) {
    companion object {
        /** Convert a dp value to px against a [Context]'s current display metrics. */
        fun dp(context: Context, value: Float): Float = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        )
    }
}
