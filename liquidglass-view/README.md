# liquidglass-view

A "Liquid Glass" frosted-glass surface for the traditional Android View system (Kotlin + XML) —
no Jetpack Compose involved.

**Requires API 31+.** The blur is implemented with `RenderNode` + `RenderEffect`; there is no
fallback rendering path for older devices.

## Usage

`GlassView` must be placed **after** the content it should blur, as a later sibling under the
same parent `ViewGroup` — Android draws children in declaration order, so "preceding siblings" is
what visually sits behind the glass.

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- backdrop content -->
    <ImageView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:src="@drawable/bg_demo_gradient" />

    <com.sifu.liquidglass.GlassView
        android:layout_width="240dp"
        android:layout_height="140dp"
        android:layout_gravity="center"
        app:frost="24dp"
        app:tintColor="#FFFFFF"
        app:tintAlpha="0.2"
        app:cornerRadius="28dp"
        app:edge="0.6"
        app:contrast="1.0"
        app:saturation="1.15"
        app:refraction="0.5"
        app:curve="1.0"
        app:dispersion="0.15"
        app:liveBlur="false" />

</FrameLayout>
```

## Attributes

| Attribute | Format | Kotlin property | Default |
|---|---|---|---|
| `frost` | dimension | `frost` | 24dp |
| `tintColor` | color (RGB only — alpha ignored) | `tintColor` | `#FFFFFF` |
| `tintAlpha` | float `0f..1f` | `tintAlpha` | 0.2 |
| `cornerRadius` | dimension | `cornerRadius` | 28dp |
| `edge` | float `0f..1f` | `edge` | 0.6 |
| `contrast` | float | `contrast` | 1.0 |
| `saturation` | float | `saturation` | 1.15 |
| `refraction` | float `0f..1f` | `refraction` | 0.5 |
| `curve` | float | `curve` | 1.0 |
| `dispersion` | float `0f..1f` (0.1-0.3 typical) | `dispersion` | 0.15 |
| `liveBlur` | boolean | `liveBlur` | false |
| `backdropSource` | reference | `backdropSource` | auto (preceding siblings) |

All properties are live-configurable from Kotlin (`glassView.frost = ...`), not just at inflation
time.

## How refraction, curve, and dispersion are implemented

None of these are backed by a real optical shader — that would need an AGSL `RuntimeShader`
(API 33+), which this library intentionally avoids to stay on API 31+. Instead:

- **`refraction` / `curve`** draw a second pass of the same blurred backdrop `RenderNode`, zoomed
  in by a factor derived from `curve` and clipped to a thin ring band along the rim, blended at
  `refraction` opacity. This reads as a soft "lens bulge" at the edge without any per-pixel
  distortion math.
- **`dispersion`** draws the rim highlight stroke three times — once each tinted red, green, and
  blue — offset by a few pixels, interpolating from white (`dispersion = 0`, no visible fringe)
  toward full color separation (`dispersion = 1`). This reads as a cheap chromatic-fringe look at
  the rim.

Both are visual approximations, not physically-based rendering. If you need the real thing, that
requires porting this to an AGSL `RuntimeShader` pipeline (API 33+) — left as a future extension.

`liveBlur = true` re-captures and re-blurs the backdrop every frame via a pre-draw listener; use
it only when the backdrop is actually animating or scrolling, since it's meaningfully more
expensive than the default static capture.
