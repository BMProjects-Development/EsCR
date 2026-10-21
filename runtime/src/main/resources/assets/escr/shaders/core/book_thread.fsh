#version 330
#extension GL_ARB_separate_shader_objects : require
#define ECR_RENDER_PIPELINE

layout(location = 0) in vec2 texCoord0;
layout(location = 0) out vec4 fragColor;

#include <escr:background_compat.glsl>

void main() {
    float pulse = sin(time * 0.075 + gl_FragCoord.x * 0.13 + gl_FragCoord.y * 0.09) * 0.5 + 0.5;
    float shimmer = 0.78 + pulse * 0.32;
    fragColor = vec4(vertexColor.rgb * shimmer, vertexColor.a);
}
