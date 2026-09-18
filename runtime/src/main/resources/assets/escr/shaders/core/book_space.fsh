#version 330
#extension GL_ARB_separate_shader_objects : require
#define ECR_RENDER_PIPELINE

layout(location = 0) in vec2 texCoord0;
layout(location = 0) out vec4 fragColor;

#include <escr:background_compat.glsl>

float hash21(vec2 point) {
    point = fract(point * vec2(123.34, 456.21));
    point += dot(point, point + 45.32);
    return fract(point.x * point.y);
}

float starTwinkle(vec2 cell, int layer, float random, float gameTime) {
    float layerF = float(layer);

    float twinkleChance = 0.34 + layerF * 0.12;
    float animated = step(1.0 - twinkleChance, hash21(cell + vec2(73.1, 19.7 + layerF * 31.0)));

    float speed = mix(0.8, 2.8, hash21(cell + vec2(12.4 + layerF, 88.8)));
    float phase = hash21(cell + vec2(41.0, 9.0 + layerF)) * 6.2831853;
    float depth = mix(0.12, 0.75, hash21(cell + vec2(5.0 + layerF, 67.0)));

    float wave = sin(gameTime * speed + phase) * 0.5 + 0.5;
    float softPulse = smoothstep(0.08, 1.0, wave);
    float pulse = mix(1.0 - depth, 1.0, softPulse);

    float staticBrightness = mix(0.72, 1.0, random);
    return mix(staticBrightness, pulse, animated);
}

void main() {
    vec2 offset = (scrollOffset - 0.5) * 0.75;
    float rawZ = (zoom - 4.0) / 24.0;
    float localZoom = 0.65 + rawZ * 1.8;

    vec2 resolution = size;
    if (resolution.y < 1.0) {
        resolution.y = 1080.0;
    }

    vec2 uv = gl_FragCoord.xy / resolution.y;
    uv = uv / localZoom + offset;

    vec3 color = vec3(0.008, 0.012, 0.035);
    float gameTime = time;

    for (int layer = 0; layer < 3; layer++) {
        float scale = (26.0 + float(layer) * 22.0) * starDensity;
        vec2 cell = floor(uv * scale);

        vec2 shift = vec2(hash21(cell + 11.1), hash21(cell + 22.2)) - 0.5;
        vec2 local = fract(uv * scale) - 0.5 - shift * 0.7;

        float random = hash21(cell + float(layer) * 19.7);
        float radius = mix(0.025, 0.14, random * random) * starSize;
        float star = smoothstep(radius, 0.0, length(local));
        float twinkle = starTwinkle(cell, layer, random, gameTime);
        vec3 tint = mix(vec3(0.45, 0.65, 1.0), vec3(1.0, 0.65, 0.45), random);
        color += tint * star * twinkle * (0.35 + float(layer) * 0.18);
    }

    float nebula = sin(uv.x * 3.1 + gameTime * 2.0) * cos(uv.y * 2.7 - gameTime * 1.4);
    color += vec3(0.035, 0.015, 0.08) * (nebula * 0.5 + 0.5);

    fragColor = vec4(color, 1.0);
}
