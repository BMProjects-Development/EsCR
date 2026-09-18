#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec2 ballUv;
layout(location = 1) out float ballSeed;
layout(location = 2) out float ballAlpha;

void main() {
    vec4 viewSpace = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewSpace;
    
    ballUv = Color.rg;
    ballSeed = Color.b;
    ballAlpha = Color.a;
}
