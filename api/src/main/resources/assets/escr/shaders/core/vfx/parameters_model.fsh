#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <escr:vfx_material.glsl>

uniform sampler2D Sampler0;
uniform sampler2D PatternSampler;
layout(std140) uniform VfxUniforms {
    float Charge;
    vec3 Shift;
    vec4 Tint;
};
layout(location = 2) in vec4 vertexColor;
layout(location = 6) in vec2 texCoord0;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * texture(PatternSampler, texCoord0 + Shift.xy) * vertexColor * ColorModulator;
    color *= vec4(Tint.rgb * Charge, Tint.a);
    fragColor = escr_vfx_material(color);
}
