package com.sifu.myliquidglassx

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sifu.liquidglass.GlassStyle
import com.sifu.liquidglass.GlassStyles
import com.sifu.liquidglass.GlassView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.example_testing_glass)

        val list = findViewById<RecyclerView>(R.id.list)
        val hero = findViewById<GlassView>(R.id.heroCard)
        val bottomBar = findViewById<GlassView>(R.id.bottomBar)

        // Scrolling backdrop under the glass.
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = ContentAdapter(ContentAdapter.sampleData())

        // Both cards re-blur every frame so the scroll shows through.
        hero.liveBlur = true
        bottomBar.liveBlur = true

        wireFrostFadeIn(hero)
        wirePresetCycling(hero)
    }

    /** Animate the hero's frost from 0 → its XML value over 600ms so the glass builds in. */
    private fun wireFrostFadeIn(hero: GlassView) {
        val target = hero.frost
        hero.frost = 0f
        ValueAnimator.ofFloat(0f, target).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { hero.frost = it.animatedValue as Float }
            start()
        }
    }

    /** Tapping the hero cycles through the five bundled presets. */
    private fun wirePresetCycling(hero: GlassView) {
        val presets: List<(android.content.Context) -> GlassStyle> = listOf(
            GlassStyles::Frosted,
            GlassStyles::Vivid,
            GlassStyles::Subtle,
            GlassStyles::Dark,
            GlassStyles::Rainbow,
        )
        var index = 0
        hero.setOnClickListener {
            index = (index + 1) % presets.size
            hero.applyStyle(presets[index](this))
        }
    }
}
