#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
layout(std140) uniform VfxBlur {
    vec2 Direction;
    vec2 Size;
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 step = Direction / Size;
    vec3 result = texture(InSampler, texCoord).rgb * 0.2270270270;
    result += texture(InSampler, texCoord + step * 1.3846153846).rgb * 0.3162162162;
    result += texture(InSampler, texCoord - step * 1.3846153846).rgb * 0.3162162162;
    result += texture(InSampler, texCoord + step * 3.2307692308).rgb * 0.0702702703;
    result += texture(InSampler, texCoord - step * 3.2307692308).rgb * 0.0702702703;
    fragColor = vec4(result, 0.0);
}
