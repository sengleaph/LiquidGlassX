package com.sifu.liquidglass.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import com.sifu.liquidglass.GlassView

/**
 * Captures the visual state behind a [target] view into a Bitmap. Walks up to the target's
 * parent [ViewGroup], draws the parent's background, then draws each sibling that renders
 * behind [target] (child index below [target]'s), translated into [target]'s local space.
 *
 * Phase 1 uses a plain [Bitmap] round-trip — a real UI-thread hit but simple and correct.
 * Phase 2 will replace this with a `RenderNode` + `HardwareBuffer` path to keep the GPU
 * upload on the render thread.
 */
internal class BackdropCapturer(private val target: View) {

    private var scratchBitmap: Bitmap? = null

    fun capture(scaledWidth: Int, scaledHeight: Int, scale: Float): Bitmap? {
        val parentGroup = target.parent as? ViewGroup ?: return null
        if (scaledWidth <= 0 || scaledHeight <= 0) return null

        val bmp = ensureBitmap(scaledWidth, scaledHeight)
        // Lock the bitmap for the whole erase+draw so the render thread's texImage2D can't
        // read while we're mid-way through repainting siblings into it.
        synchronized(bmp) {
            bmp.eraseColor(0)
            val canvas = Canvas(bmp)
            canvas.scale(scale, scale)

            val myLoc = IntArray(2).also { target.getLocationInWindow(it) }
            val parentLoc = IntArray(2).also { parentGroup.getLocationInWindow(it) }
            canvas.translate(
                (parentLoc[0] - myLoc[0]).toFloat(),
                (parentLoc[1] - myLoc[1]).toFloat()
            )

            parentGroup.background?.draw(canvas)
            val myIndex = parentGroup.indexOfChild(target)
            for (i in 0 until myIndex) {
                val sibling = parentGroup.getChildAt(i)
                if (sibling.visibility != View.VISIBLE) continue
                // Skip other glass views — their `TextureView` doesn't render correctly through
                // a software canvas, which produces black or garbled patches under this glass.
                if (sibling is GlassView) continue
                canvas.save()
                canvas.translate(sibling.x, sibling.y)
                sibling.draw(canvas)
                canvas.restore()
            }
        }
        return bmp
    }

    private fun ensureBitmap(w: Int, h: Int): Bitmap {
        val existing = scratchBitmap
        if (existing != null && existing.width == w && existing.height == h) return existing
        existing?.recycle()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { scratchBitmap = it }
    }

    fun release() {
        scratchBitmap?.recycle()
        scratchBitmap = null
    }
}
