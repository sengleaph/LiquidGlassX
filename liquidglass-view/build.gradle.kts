plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.sifu.liquidglass"
    compileSdk = 36

    defaultConfig {
        minSdk = 31

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}
kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.sengleaph"
                artifactId = "liquidglass-view"
                // JitPack passes the tag as -PVERSION; the jitpack.yml `before_install` sets
                // the env var. Support both, plus a local fallback.
                version = (project.findProperty("VERSION") as String?)
                    ?: System.getenv("VERSION")
                    ?: "1.3.0"
                pom {
                    name.set("Liquid Glass View")
                    description.set(
                        "OpenGL ES 2.0 liquid-glass surface for the traditional Android View " +
                            "system. Snell refraction, chromatic dispersion, Fresnel rim, and " +
                            "specular via a single fragment shader."
                    )
                    url.set("https://github.com/sengleaph/MyLiquidGlassX")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("sengleaph")
                            name.set("seang sengleaph")
                        }
                    }
                    scm {
                        url.set("https://github.com/sengleaph/MyLiquidGlassX")
                    }
                }
            }
        }
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
}
