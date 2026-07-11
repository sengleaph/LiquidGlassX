package com.sifu.myliquidglassx

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
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

        // Switch which layout is active by uncommenting one line:
//        setContentView(R.layout.activity_main)     // sliders playground (legacy)
//        setContentView(R.layout.example_hero)      // single glass card over a photo
//        setContentView(R.layout.example_list)      // scrollable list + fixed glass bar
        setContentView(R.layout.example_customize)   // NEW: full customization studio

        // Each setup fn no-ops if its expected view isn't in the currently-inflated layout,
        // so all three examples can share this Activity.
        setupCustomizeDemo()
        setupListDemo()
    }

    // ---- Customize demo ------------------------------------------------------

    private fun setupCustomizeDemo() {
        val hero = findViewById<GlassView>(R.id.heroCard) ?: return
        val presetButtons = listOf(
            R.id.presetFrosted to { GlassStyles.Frosted(this) },
            R.id.presetVivid to { GlassStyles.Vivid(this) },
            R.id.presetSubtle to { GlassStyles.Subtle(this) },
            R.id.presetDark to { GlassStyles.Dark(this) },
            R.id.presetRainbow to { GlassStyles.Rainbow(this) },
        )
        presetButtons.forEach { (id, styleFactory) ->
            findViewById<Button>(id).setOnClickListener {
                val style = styleFactory()
                hero.applyStyle(style)
                syncSlidersToStyle(style)
            }
        }

        val d = resources.displayMetrics.density
        bindSlider(R.id.frostRow, "Frost radius", 80, 24,
            fmt = { "${it}dp" },
            onSet = { hero.frost = it.toFloat() * d })
        bindSlider(R.id.cornerRow, "Corner radius", 80, 28,
            fmt = { "${it}dp" },
            onSet = { hero.cornerRadius = it.toFloat() * d })
        bindSlider(R.id.refractionRow, "Refraction", 100, 70,
            fmt = { pct(it) },
            onSet = { hero.refraction = it / 100f })
        bindSlider(R.id.curveRow, "Curve", 300, 140,
            fmt = { String.format("%.2f", it / 100f) },
            onSet = { hero.curve = (it / 100f).coerceAtLeast(0.1f) })
        bindSlider(R.id.dispersionRow, "Dispersion", 100, 15,
            fmt = { pct(it) },
            onSet = { hero.dispersion = it / 100f })
        bindSlider(R.id.edgeRow, "Edge", 100, 70,
            fmt = { pct(it) },
            onSet = { hero.edge = it / 100f })
        bindSlider(R.id.tintAlphaRow, "Tint alpha", 100, 15,
            fmt = { pct(it) },
            onSet = { hero.tintAlpha = it / 100f })
//        bindSlider(R.id.lightAngleRow, "Light angle", 360, 135,
//            fmt = { "${it}°" },
//            onSet = { hero.lightAngle = it.toFloat() })

        // Apply Frosted at start so the sliders match a real preset.
        val initial = GlassStyles.Frosted(this)
        hero.applyStyle(initial)
        syncSlidersToStyle(initial)
    }

    private fun bindSlider(
        rowId: Int,
        label: String,
        max: Int,
        initial: Int,
        fmt: (Int) -> String,
        onSet: (Int) -> Unit,
    ) {
        val row = findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.rowLabel).text = label
        val valueView = row.findViewById<TextView>(R.id.rowValue)
        val seek = row.findViewById<SeekBar>(R.id.rowSeek)
        seek.max = max
        seek.progress = initial
        valueView.text = fmt(initial)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                valueView.text = fmt(progress)
                onSet(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    /** Snap each seekbar to reflect a freshly-applied [GlassStyle]. */
    private fun syncSlidersToStyle(style: GlassStyle) {
        val d = resources.displayMetrics.density
        setSeek(R.id.frostRow, (style.frost / d).toInt(), "${(style.frost / d).toInt()}dp")
        setSeek(R.id.cornerRow, (style.cornerRadius / d).toInt(),
            "${(style.cornerRadius / d).toInt()}dp")
        setSeek(R.id.refractionRow, (style.refraction * 100).toInt(),
            pct((style.refraction * 100).toInt()))
        setSeek(R.id.curveRow, (style.curve * 100).toInt(),
            String.format("%.2f", style.curve))
        setSeek(R.id.dispersionRow, (style.dispersion * 100).toInt(),
            pct((style.dispersion * 100).toInt()))
        setSeek(R.id.edgeRow, (style.edge * 100).toInt(),
            pct((style.edge * 100).toInt()))
        setSeek(R.id.tintAlphaRow, (style.tintAlpha * 100).toInt(),
            pct((style.tintAlpha * 100).toInt()))
//        setSeek(R.id.lightAngleRow, style.lightAngle.toInt(),
//            "${style.lightAngle.toInt()}°")
    }

    private fun setSeek(rowId: Int, progress: Int, valueText: String) {
        val row = findViewById<View>(rowId)
        row.findViewById<SeekBar>(R.id.rowSeek).progress = progress
        row.findViewById<TextView>(R.id.rowValue).text = valueText
    }

    private fun pct(value: Int) = "${value}%"

    // ---- List demo (kept working for the commented setContentView option) ---

    private fun setupListDemo() {
        val list = findViewById<RecyclerView>(R.id.list) ?: return
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = ContentAdapter(ContentAdapter.sampleData())
    }
}
