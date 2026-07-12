package com.sifu.liquidglass.gl

internal object GlShaders {

    const val PASSTHROUGH_VERTEX = """
        attribute vec2 aPos;
        attribute vec2 aTex;
        varying vec2 vTex;
        varying vec2 vShape;
        void main() {
            gl_Position = vec4(aPos, 0.0, 1.0);
            vTex = aTex;
            // Shape coord in [-0.5, 0.5] with +Y down (screen convention).
            vShape = vec2(aPos.x, -aPos.y) * 0.5;
        }
    """

    /**
     * Pass-through fragment used for the dual-filter downsample chain. When rendered into
     * a target half the size of the source, `GL_LINEAR` on `uSrc` gives a 2×2 box average
     * for free — that's the whole trick behind cheap "Kawase-style" large blurs.
     */
    const val DOWNSAMPLE_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uSrc;
        void main() {
            gl_FragColor = texture2D(uSrc, vTex);
        }
    """

    /**
     * 15-tap separable Gaussian blur — run twice per frame with `uTexelStep = (1/w, 0)` then
     * `(0, 1/h)`. Weights are computed live from [uSigma]. `uSigma <= 0.5` short-circuits.
     */
    const val BLUR_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uSrc;
        uniform vec2 uTexelStep;
        uniform float uSigma;
        void main() {
            if (uSigma <= 0.5) {
                gl_FragColor = texture2D(uSrc, vTex);
                return;
            }
            float invTwoSigmaSq = 1.0 / (2.0 * uSigma * uSigma);
            vec4 sum = vec4(0.0);
            float weightSum = 0.0;
            for (int i = -7; i <= 7; i++) {
                float fi = float(i);
                float w = exp(-fi * fi * invTwoSigmaSq);
                sum += texture2D(uSrc, vTex + fi * uTexelStep) * w;
                weightSum += w;
            }
            gl_FragColor = sum / weightSum;
        }
    """

    /**
     * Liquid-glass composite shader — a faithful subset of Prismal's fragment_shader.glsl.
     *
     *   1. `sdRoundBox` gives the signed distance to the rounded-rect boundary.
     *   2. `heightAt` maps that distance through a circular-arc profile √(2t − t²), so the
     *      surface is highest in the center and rolls off at the edges.
     *   3. Finite differences give ∇h, from which we build a per-pixel surface normal N.
     *   4. `refract()` does the two-pass Snell displacement; the resulting XY becomes a UV
     *      offset for sampling the blurred backdrop.
     *   5. Chromatic dispersion samples R and B with slightly larger/smaller offsets than G.
     *   6. Schlick Fresnel + Blinn-Phong specular add the rim + highlight.
     *   7. Tint and brightness finish the color.
     */
    const val COMPOSITE_FRAGMENT = """
        precision highp float;
        varying vec2 vTex;
        varying vec2 vShape;

        uniform sampler2D uSrc;
        uniform vec2 uResolution;
        uniform vec2 uGlassSize;
        uniform float uCornerRadius;

        uniform float uIor;
        uniform float uNormalStrength;
        uniform float uGlassThickness;
        uniform float uTransitionWidth;
        uniform float uChromaticAberration;

        uniform vec2 uLightDir;
        uniform float uSpecular;
        uniform float uShininess;
        uniform float uRimStrength;

        uniform vec4 uTint;
        uniform float uBrightness;

        float sdRoundBox(vec2 p, vec2 halfSize, float r) {
            vec2 q = abs(p) - halfSize + vec2(r);
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        float heightFromDist(float d, float tw) {
            // d is signed distance; negative inside. t = 0 at edge, 1 deep inside.
            float t = clamp(-d / max(tw, 1.0), 0.0, 1.0);
            return sqrt(max(0.0, 2.0 * t - t * t));
        }

        void main() {
            vec2 halfSize = uGlassSize * 0.5;
            vec2 pPx = vShape * uGlassSize;
            float cr = min(uCornerRadius, min(halfSize.x, halfSize.y));

            float d = sdRoundBox(pPx, halfSize, cr);
            if (d > 1.0) discard;

            float minDim = min(halfSize.x, halfSize.y);
            float tw = clamp(uTransitionWidth, 1.0, minDim * 0.95);
            float h = heightFromDist(d, tw);

            // Finite-difference gradient of h → surface normal. Step is a fraction of the
            // transition width so the gradient magnitude reads properly at any size.
            float e = max(1.5, tw * 0.04);
            float hpx = heightFromDist(sdRoundBox(pPx + vec2(e, 0.0), halfSize, cr), tw);
            float hnx = heightFromDist(sdRoundBox(pPx - vec2(e, 0.0), halfSize, cr), tw);
            float hpy = heightFromDist(sdRoundBox(pPx + vec2(0.0, e), halfSize, cr), tw);
            float hny = heightFromDist(sdRoundBox(pPx - vec2(0.0, e), halfSize, cr), tw);
            vec2 gradH = vec2((hpx - hnx) / (2.0 * e), (hpy - hny) / (2.0 * e));
            vec3 N = normalize(vec3(-gradH * uNormalStrength, 1.0));

            // Snell's law refraction — two passes (in then out).
            vec3 V = vec3(0.0, 0.0, 1.0);
            vec3 refIn = refract(-V, N, 1.0 / uIor);
            vec3 refOut = (dot(refIn, refIn) < 0.001)
                ? vec3(0.0)
                : refract(refIn, -N, uIor);
            vec2 refractUv = refOut.xy * uGlassThickness / uResolution;

            // Chromatic dispersion — offset R + and B - along the refraction direction.
            vec2 caOffset = refractUv * uChromaticAberration * 0.5;
            vec2 baseUv = vTex + refractUv;
            vec3 color;
            if (uChromaticAberration < 0.02) {
                color = texture2D(uSrc, clamp(baseUv, vec2(0.0), vec2(1.0))).rgb;
            } else {
                vec2 uvR = clamp(baseUv + caOffset, vec2(0.0), vec2(1.0));
                vec2 uvG = clamp(baseUv, vec2(0.0), vec2(1.0));
                vec2 uvB = clamp(baseUv - caOffset, vec2(0.0), vec2(1.0));
                color = vec3(
                    texture2D(uSrc, uvR).r,
                    texture2D(uSrc, uvG).g,
                    texture2D(uSrc, uvB).b
                );
            }

            // Tint + brightness.
            color = mix(color, uTint.rgb, uTint.a);
            color *= uBrightness;

            // Blinn-Phong specular from a light coming at (lightDir.xy, +Z).
            vec3 L = normalize(vec3(uLightDir, 1.0));
            vec3 H = normalize(L + V);
            float spec = pow(max(dot(N, H), 0.0), max(uShininess, 1.0)) * uSpecular;
            color += vec3(spec) * (0.5 + 0.5 * h);

            // Schlick Fresnel — brighter grazing edges → rim highlight.
            float cosVN = max(dot(N, V), 0.0);
            float r0 = pow((1.0 - uIor) / (1.0 + uIor), 2.0);
            float F = r0 + (1.0 - r0) * pow(1.0 - cosVN, 5.0);
            float edgeRing = max(4.0, minDim * 0.18);
            float rimBand = 1.0 - smoothstep(-edgeRing, 0.5, d);
            color += vec3(F * uRimStrength * rimBand * 1.4);
            // Extra grazing-angle brightness from the tilted normal at the rim.
            float tiltRim = pow(1.0 - cosVN, 3.5) * rimBand * uRimStrength;
            color += vec3(tiltRim * 0.8);

            // Anti-alias the SDF border.
            float alpha = 1.0 - smoothstep(-1.0, 1.0, d);
            gl_FragColor = vec4(color, alpha);
        }
    """
}
