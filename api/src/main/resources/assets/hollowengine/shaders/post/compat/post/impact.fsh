#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D SceneColorSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;
layout(std140) uniform HollowConfig {
    float Strength;
    float Threshold;
    float Invert;
};

void main() {
    vec3 color = texture(SceneColorSampler, texCoord).rgb;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float ink = step(Threshold, luma);
    ink = mix(ink, 1.0 - ink, step(0.5, Invert));
    fragColor = vec4(mix(color, vec3(ink), clamp(Strength, 0.0, 1.0)), 1.0);
}
