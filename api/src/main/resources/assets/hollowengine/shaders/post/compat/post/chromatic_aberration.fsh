#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D SceneColorSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;
layout(std140) uniform HollowConfig {
    vec2 ScreenSize;
    float Strength;
};

void main() {
    vec2 fromCenter = texCoord - 0.5;
    vec2 shift = fromCenter * Strength * 12.0 / 1080.0 * vec2(ScreenSize.y / ScreenSize.x, 1.0) * 2.0;
    float red = texture(SceneColorSampler, texCoord + shift).r;
    float green = texture(SceneColorSampler, texCoord).g;
    float blue = texture(SceneColorSampler, texCoord - shift).b;
    fragColor = vec4(red, green, blue, 1.0);
}
