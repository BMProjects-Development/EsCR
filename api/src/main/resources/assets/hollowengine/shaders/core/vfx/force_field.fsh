#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <escr:vfx_material.glsl>

uniform sampler2D Sampler0;
layout(std140) uniform VfxUniforms {
    vec4 FieldColor;
    float FresnelPower;
    float IntersectionWidth;
};
#ifdef VFX_MODEL
layout(location = 2) in vec4 vertexColor;
layout(location = 6) in vec2 texCoord0;
#else
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;
#endif
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 screen = gl_FragCoord.xy / vec2(textureSize(SceneDepth, 0));
    vec3 position = escr_vfx_view_position(screen, gl_FragCoord.z);
    vec3 normal = normalize(cross(dFdx(position), dFdy(position)));
    float facing = abs(dot(normal, normalize(-position)));
    float rim = pow(1.0 - facing, max(FresnelPower, 0.001));
    float gap = escr_vfx_distance(texture(SceneDepth, screen).r) - escr_vfx_distance(gl_FragCoord.z);
    float contact = 1.0 - clamp(gap / max(IntersectionWidth, 0.001), 0.0, 1.0);
    float ripple = 0.85 + 0.15 * sin(texCoord0.y * 40.0 - EffectTime * (4000.0 / 1200.0));
    vec4 color = FieldColor * vertexColor * texture(Sampler0, texCoord0) * ColorModulator;
    color.a *= clamp(max(rim * ripple, contact), 0.0, 1.0);
    fragColor = escr_vfx_material(color);
}
