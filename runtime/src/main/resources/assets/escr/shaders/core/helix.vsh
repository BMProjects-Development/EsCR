#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;

out vec2 ballUv;
out float ballSeed;
out float ballAlpha;

void main() {
    vec4 viewSpace = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewSpace;
    
    ballUv = Color.rg;
    ballSeed = Color.b;
    ballAlpha = Color.a;
}
