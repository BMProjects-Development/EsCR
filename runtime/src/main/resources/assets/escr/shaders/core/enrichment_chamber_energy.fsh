#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in vec3 effectPosition;
layout(location = 1) in vec3 viewPosition;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) flat in float overflowState;

layout(location = 0) out vec4 fragColor;

float hash31(vec3 point) {
    point = fract(point * 0.1031);
    point += dot(point, point.yzx + 33.33);
    return fract((point.x + point.y) * point.z);
}

void main() {
    float time = GameTime * 1200.0;
    float speed = mix(1.7, 2.15, overflowState);

    float flowPhase = effectPosition.y * 4.6 - time * speed;
    flowPhase += sin(effectPosition.x * 1.7 + effectPosition.z * 1.3 + time * 0.45);
    float flow = sin(flowPhase) * 0.5 + 0.5;
    float bands = smoothstep(0.66, 0.98, flow);

    float crossWave = sin(
        (effectPosition.x - effectPosition.z) * 3.1 +
        effectPosition.y * 1.4 +
        time * speed * 0.72
    ) * 0.5 + 0.5;
    float shimmer = smoothstep(0.72, 1.0, crossWave);

    vec3 sparkCell = floor(
        effectPosition * mix(2.0, 3.2, overflowState) +
        vec3(0.0, floor(time * speed), 0.0)
    );
    float sparkNoise = hash31(sparkCell);
    float sparks = smoothstep(
        mix(0.99, 0.972, overflowState),
        1.0,
        sparkNoise
    );

    float wavePeriod = mix(6.5, 4.6, overflowState);
    float waveCycle = fract(time / wavePeriod);
    float waveEnvelope = smoothstep(0.02, 0.1, waveCycle) *
        (1.0 - smoothstep(0.62, 0.9, waveCycle));
    float radialPhase = fract(length(effectPosition) * 0.18 - waveCycle * 1.35);
    float radialWave = (
        1.0 - smoothstep(0.025, 0.11, abs(radialPhase - 0.5))
    ) * waveEnvelope;

    float scanPulse = pow(
        max(0.0, sin(time * mix(0.68, 0.92, overflowState))),
        14.0
    );
    float scanPhase = abs(fract(effectPosition.y * 0.125 - time * 0.2) - 0.5);
    float scanLine = (1.0 - smoothstep(0.012, 0.06, scanPhase)) * scanPulse;

    vec3 dx = dFdx(viewPosition);
    vec3 dy = dFdy(viewPosition);
    vec3 normal = normalize(cross(dx, dy));
    vec3 viewDirection = normalize(-viewPosition);
    float fresnel = pow(1.0 - abs(dot(normal, viewDirection)), 2.0);

    float pulse = sin(time * mix(1.8, 2.5, overflowState)) * 0.5 + 0.5;
    vec3 accent = mix(
        vec3(0.74, 0.22, 1.0),
        vec3(0.12, 0.82, 1.0),
        overflowState
    );
    float energy = bands * 0.42 + shimmer * 0.18 + sparks * 0.72;
    energy += radialWave * 0.58 + scanLine * 0.68;
    energy += fresnel * 0.35 + pulse * 0.08;

    vec3 color = mix(vertexColor.rgb, accent, clamp(energy, 0.0, 0.88));
    color *= 0.86 + pulse * 0.2;

    float opacity = vertexColor.a * (
        0.62 +
        bands * 0.18 +
        fresnel * 0.32 +
        sparks * 0.4 +
        radialWave * 0.2 +
        scanLine * 0.3
    );
    opacity = clamp(opacity, 0.0, 0.82);
    if (opacity < 0.01) {
        discard;
    }

    fragColor = vec4(color, opacity) * ColorModulator;
}
