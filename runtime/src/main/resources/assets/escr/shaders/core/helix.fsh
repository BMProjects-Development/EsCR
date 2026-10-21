#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in vec2 ballUv;
layout(location = 1) in float ballSeed;
layout(location = 2) in float ballAlpha;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 p = ballUv * 2.0 - 1.0;
    float distanceSquared = dot(p, p);

    if (distanceSquared > 1.0) { discard; }

    float z = sqrt(max(0.0, 1.0 - distanceSquared));

    vec3 normal = normalize(vec3(p.x, -p.y, z));
    vec3 lightDirection = normalize(vec3(-0.35, 0.45, 0.8));

    float diffuse = max(dot(normal, lightDirection), 0.0);
    float fresnel = pow(1.0 - z, 2.2);

    float pulse = sin(GameTime * 900.0 + ballSeed * 6.283185) * 0.5 + 0.5;

    vec3 darkPurple = vec3(0.25, 0.04, 0.48);
    vec3 purple = vec3(0.62, 0.18, 0.92);
    vec3 glowPurple = vec3(0.88, 0.48, 1.0);

    vec3 color = mix(darkPurple, purple, 0.35 + diffuse * 0.65);

    color += glowPurple * fresnel * (0.22 + pulse * 0.12);

    float softEdge = 1.0 - smoothstep(0.72, 1.0, sqrt(distanceSquared));
    float alpha = (softEdge * 0.48 + fresnel * 0.22) * ballAlpha;

    if (alpha < 0.01) { discard; }

    fragColor = vec4(color, min(alpha, 0.68)) * ColorModulator;
}
