#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    vec4 clipPosition = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position = clipPosition;

    texCoord0 = clipPosition.xy / clipPosition.w * 0.5 + 0.5;
    vertexColor = Color;
}
