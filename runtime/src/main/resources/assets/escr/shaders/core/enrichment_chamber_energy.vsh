#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec3 effectPosition;
layout(location = 1) out vec3 viewPosition;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) flat out float overflowState;

void main() {
    vec4 viewSpace = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewSpace;

    effectPosition = Position;
    viewPosition = viewSpace.xyz;
    vertexColor = Color;
    overflowState = step(Color.r, Color.g);
}
