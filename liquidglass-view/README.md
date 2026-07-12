# liquidglass-view

A **liquid‑glass** surface for the traditional Android View system (Kotlin + XML) — real Snell
refraction, Fresnel rim, chromatic dispersion, and Blinn‑Phong specular rendered on
**OpenGL ES 2.0** through a child `TextureView`.

Requires **API 31+** (Android 12).

## Install

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.sengleaph:liquidglass-view:1.3.0")
}
```

## Usage

`GlassView` must be a **later sibling** than the content it should blur — Android draws children
in declaration order, so preceding siblings are what visually sit behind the glass.

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ImageView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:src="@drawable/bg" />

    <com.sifu.liquidglass.GlassView
        android:layout_width="280dp"
        android:layout_height="160dp"
        android:layout_gravity="center"
        app:frost="40dp"
        app:tintColor="#FFFFFF"
        app:tintAlpha="0.15"
        app:cornerRadius="28dp"
        app:edge="0.7"
        app:refraction="0.7"
        app:dispersion="0.15"
        app:curve="1.4"
        app:lightAngle="135">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:text="Liquid Glass"
            android:textColor="#FFFFFF"
            android:textSize="24sp"
            android:textStyle="bold" />
    </com.sifu.liquidglass.GlassView>
</FrameLayout>
```

Or configure from Kotlin:

```kotlin
val glass = findViewById<GlassView>(R.id.myGlass)
val d = resources.displayMetrics.density

glass.frost = 40f * d
glass.cornerRadius = 24f * d
glass.tintColor = 0xFFFFFF
glass.tintAlpha = 0.2f
glass.edge = 0.8f
glass.refraction = 0.7f
glass.dispersion = 0.2f
glass.curve = 1.6f
glass.lightAngle = 135f
```

Or use one of the bundled presets:

```kotlin
glass.applyStyle(GlassStyles.Frosted(this))
glass.applyStyle(GlassStyles.Vivid(this))
glass.applyStyle(GlassStyles.Subtle(this))
glass.applyStyle(GlassStyles.Dark(this))
glass.applyStyle(GlassStyles.Rainbow(this))
```

## Attributes / properties

| Attribute | XML format | Kotlin property | Default | Range |
|---|---|---|---|---|
| `frost` | dimension | `frost: Float` (px) | `24dp` | 0–~80dp |
| `tintColor` | color (RGB — alpha ignored) | `tintColor: Int` | `#FFFFFF` | RGB |
| `tintAlpha` | float | `tintAlpha: Float` | `0.15` | 0–1 |
| `cornerRadius` | dimension | `cornerRadius: Float` (px) | `28dp` | 0–min(w,h)/2 |
| `edge` | float | `edge: Float` | `0.6` | 0–1 |
| `refraction` | float | `refraction: Float` | `0.65` | 0–1 |
| `curve` | float | `curve: Float` | `1.4` | 0.1–3 |
| `dispersion` | float | `dispersion: Float` | `0.2` | 0–1 |
| `lightAngle` | float (deg) | `lightAngle: Float` | `135` | 0–360 |
| `liveBlur` | boolean | `liveBlur: Boolean` | `false` | — |
| `contrast` | float | `contrast: Float` | `1.0` | 0.1–3 |
| `saturation` | float | `saturation: Float` | `1.15` | 0.1–3 |
| `backdropSource` | reference | (auto sibling walk) | `NO_ID` | — |

Additionally, `downsampleFactor: Float` (default `0.5`, range 0.1–1.0) controls the backdrop
capture resolution — smaller = cheaper CPU, larger visible frost, but more pixelated refraction.

## How it works

Each `GlassView` hosts a private `TextureView` + `HandlerThread`‑backed EGL render loop. On every
frame that needs to update:

1. **Capture** — parent hierarchy (minus this view and any nested `GlassView`s) is drawn into a
   `Bitmap` at `downsampleFactor` of view size.
2. **Upload** — bitmap → GL texture via `GLUtils.texImage2D`.
3. **Blur** — dual‑filter multi‑level downsample chain (up to 5 levels of ½ res each), then one
   H+V separable 15‑tap Gaussian pass at the smallest level. Ceiling ≈ 256 px of visible σ.
4. **Composite** — one fragment shader applies:
   - Rounded‑rect SDF (`sdRoundBox`)
   - Circular‑arc height field `√(2t − t²)`
   - Finite‑difference gradient → surface normal `N`
   - Two‑pass `refract()` for Snell displacement
   - Per‑channel R/B UV offset for chromatic dispersion
   - Schlick Fresnel rim + tilt‑rim brightness
   - Blinn‑Phong specular for the highlight bloom
   - Tint + brightness blend

All effects are real per‑pixel math — no fake gradients, no ring painters.

## Performance

- `liveBlur = false` (default) captures only when the view invalidates. Turn it on **only** when
  the backdrop actually moves (scrolling `RecyclerView`, video, animation).
- Each `GlassView` owns an EGL context + render thread. Keep on‑screen instances ≤ 3–4.
- The blur ceiling scales with `downsampleFactor` — lower it (e.g. `0.25f`) if you want extremely
  heavy frost and can accept a slightly softer backdrop.

## License

Apache 2.0.
