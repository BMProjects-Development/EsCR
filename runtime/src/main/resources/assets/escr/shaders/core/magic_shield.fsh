#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in vec3 viewPosition;
layout(location = 1) in float waveDistance;
layout(location = 2) in float waveProgress;
layout(location = 3) in float waveStrength;

layout(location = 0) out vec4 fragColor;

void main() {
    float front = waveProgress * 1.08;
    float width = mix(0.025, 0.12, waveProgress);
    float ringDistance = abs(waveDistance - front);
    float ring = 1.0 - smoothstep(width, width + 0.035, ringDistance);

    float wakeStart = max(0.0, front - 0.38);
    float wake = smoothstep(wakeStart, front, waveDistance);

    wake *= 1.0 - smoothstep(front, front + width, waveDistance);

    vec3 dx = dFdx(viewPosition);
    vec3 dy = dFdy(viewPosition);
    vec3 normal = normalize(cross(dx, dy));
    vec3 viewDirection = normalize(-viewPosition);
    float fresnel = pow(1.0 - abs(dot(normal, viewDirection)), 2.4);

    float appear = smoothstep(0.0, 0.055, waveProgress);
    float disappear = 1.0 - smoothstep(0.68, 1.0, waveProgress);
    float lifetime = appear * disappear;
    float pulse = sin(GameTime * 1200.0 * 2.6) * 0.5 + 0.5;

    vec3 baseColor = vec3(0.08, 0.46, 1.0);
    vec3 ringColor = vec3(0.66, 0.93, 1.0);
    vec3 color = mix(baseColor, ringColor, ring);

    color += fresnel * vec3(0.12, 0.38, 0.55);
    color *= 0.92 + pulse + 0.08;

    float opacity = ring * 0.72 + wake * 0.24;
    opacity *= 0.82 + fresnel * 0.18;
    opacity *= lifetime;
    opacity *= mix(0.72, 1.0, waveStrength);

    if (opacity < 0.01) {
        discard;
    }

    fragColor = vec4(color, clamp(opacity, 0.0, 0.88)) * ColorModulator;
}
