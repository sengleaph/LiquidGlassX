# MyLiquidGlassX

An Android project containing:

- **`liquidglass-view/`** — a reusable Android library that renders a "liquid glass" surface via
  a real OpenGL ES 2.0 pipeline (Snell refraction, Fresnel rim, chromatic dispersion, specular).
- **`app/`** — a sample app that showcases the library with a scrolling `RecyclerView` behind two
  overlapping glass surfaces (hero card + bottom toolbar), tap‑to‑cycle presets, and a frost
  fade‑in animation.

## Install (consumers)

Add JitPack to your repositories:

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

Depend on the library:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.sengleaph:liquidglass-view:1.3.0")
}
```

Requires `minSdk >= 31`.

See [liquidglass-view/README.md](liquidglass-view/README.md) for the full API, all attributes,
and usage examples.

## Publishing a new version

1. Bump the version in the release branch — this only matters for the fallback default in
   [`liquidglass-view/build.gradle.kts`](liquidglass-view/build.gradle.kts). JitPack itself
   reads the git tag.
2. Commit + push.
3. Create a git tag matching the semver you want:
   ```bash
   git tag 1.4.0
   git push origin 1.4.0
   ```
4. Visit `https://jitpack.io/#sengleaph/MyLiquidGlassX` — JitPack will build the tag on the first
   request and expose it as `com.github.sengleaph:liquidglass-view:1.4.0`.

The build is driven by [`jitpack.yml`](jitpack.yml) which pins JDK 21 (Kotlin 2.4 + AGP 8.11
require it) and passes the resolved tag through as `-PVERSION`, which the library's
`build.gradle.kts` picks up.

## Local publish (testing)

Publish to `~/.m2/` and consume from a sibling project:

```bash
./gradlew -PVERSION=1.3.0-local :liquidglass-view:publishReleasePublicationToMavenLocal
```

In the consumer project add `mavenLocal()` to `repositories { }` and depend on
`com.github.sengleaph:liquidglass-view:1.3.0-local`.

## Running the sample app

```bash
./gradlew :app:installDebug
adb shell am start -n com.sifu.myliquidglassx/.MainActivity
```

## Module layout

```
MyLiquidGlassX/
├── app/                              # Demo application
│   └── src/main/
│       ├── java/…/MainActivity.kt    # Wires up the demo (adapter, animations, presets)
│       └── res/layout/example_testing_glass.xml
├── liquidglass-view/                 # Library module
│   ├── build.gradle.kts              # Publishing config
│   └── src/main/
│       ├── java/com/sifu/liquidglass/
│       │   ├── GlassView.kt          # Public API
│       │   ├── GlassStyle.kt
│       │   ├── GlassStyles.kt
│       │   └── gl/                   # Internal GL implementation
│       │       ├── EglCore.kt
│       │       ├── GlassRenderer.kt
│       │       ├── BackdropCapturer.kt
│       │       ├── FboPair.kt
│       │       ├── FboChain.kt
│       │       ├── GaussianBlurProgram.kt
│       │       ├── DownsampleProgram.kt
│       │       ├── GlassCompositeProgram.kt
│       │       └── GlShaders.kt      # All GLSL source
│       └── res/values/attrs.xml      # XML `styleable` declarations
├── jitpack.yml                       # JitPack build config (JDK 21 + version passthrough)
└── settings.gradle.kts
```

## License

Apache 2.0. See [LICENSE](LICENSE).
