#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D PatternSampler;
layout(std140) uniform FilterConfig {
    float Strength;
    vec2 Scale;
    vec3 Tint;
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec3 filterColor = texture(PatternSampler, texCoord * Scale).rgb * Tint;
    fragColor = vec4(mix(scene.rgb, scene.rgb * filterColor, clamp(Strength, 0.0, 1.0)), scene.a);
}
