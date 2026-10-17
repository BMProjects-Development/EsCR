#include <minecraft:projection.glsl>

uniform sampler2D SceneDepth;

layout(std140) uniform VfxMaterial {
    float Softness;
    float Glow;
    float GlowPass;
    float EffectTime;
};

float escr_vfx_softness() {
    if (Softness <= 0.0) return 1.0;
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(SceneDepth, 0));
    float scene = texture(SceneDepth, uv).r;
    float sceneDistance = abs(ProjMat[3][2] / (scene + ProjMat[2][2]));
    float particleDistance = abs(ProjMat[3][2] / (gl_FragCoord.z + ProjMat[2][2]));
    return clamp((sceneDistance - particleDistance) / Softness, 0.0, 1.0);
}

vec4 escr_vfx_material(vec4 color) {
    color.a *= escr_vfx_softness();
    if (GlowPass > 0.5) {
#ifdef VFX_MULTIPLY
        discard;
#endif
        if (Glow <= 0.0) discard;
        return vec4(color.rgb * color.a * Glow, 0.0);
    }
#ifdef VFX_MULTIPLY
    return vec4(mix(vec3(1.0), color.rgb, color.a), 1.0);
#else
    return color;
#endif
}
