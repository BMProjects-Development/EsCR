#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform VfxConfig {
    float Strength;
    float EffectTime;
    float Radius;
    float Width;
    float Opacity;
    float CenterX;
    float CenterY;
    float Mode;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec3 result = scene.rgb;
    if (Mode < 0.5) {
        float gray = dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722));
        result = mix(scene.rgb, vec3(gray), clamp(Strength, 0.0, 1.0));
    } else if (Mode < 1.5) {
        vec2 direction = texCoord - vec2(CenterX, CenterY);
        vec2 offset = direction * Strength * 0.012;
        result = vec3(texture(InSampler, texCoord + offset).r, scene.g, texture(InSampler, texCoord - offset).b);
    } else if (Mode < 2.5) {
        vec2 fromCenter = texCoord - vec2(CenterX, CenterY);
        float distance = length(fromCenter);
        float ring = exp(-pow((distance - max(Radius, 0.0)) / max(Width, 0.001), 2.0));
        vec2 offset = normalize(fromCenter + vec2(0.00001)) * ring * Strength * 0.02;
        result = texture(InSampler, texCoord + offset).rgb + vec3(ring * Strength * 0.12);
    } else {
        float depth = texture(DepthSampler, texCoord).r;
        if (depth < 0.00001) {
            float distance = length(texCoord - vec2(CenterX, CenterY));
            float radius = Radius > 0.0 ? Radius : fract(EffectTime * 0.25) * 0.6;
            float ring = exp(-pow((distance - radius) / max(Width, 0.001), 2.0));
            result = mix(scene.rgb, vec3(1.0, 0.72, 0.3), clamp(ring * Strength * Opacity, 0.0, 1.0));
        }
    }
    fragColor = vec4(result, scene.a);
}
