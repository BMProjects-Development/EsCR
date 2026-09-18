#ifdef ECR_RENDER_PIPELINE
#ifndef ECR_STAR_DENSITY
#define ECR_STAR_DENSITY 1.0
#endif

#ifndef ECR_STAR_SIZE
#define ECR_STAR_SIZE 1.0
#endif

#include <minecraft:globals.glsl>

layout(location = 1) in vec4 vertexColor;

vec2 ecrLocalSize() {
    float width = abs(dFdx(texCoord0.x)) > 0.000001 ? 1.0 / abs(dFdx(texCoord0.x)) : ScreenSize.x;
    float height = abs(dFdy(texCoord0.y)) > 0.000001 ? 1.0 / abs(dFdy(texCoord0.y)) : ScreenSize.y;
    return vec2(width, height);
}

vec2 ecrScrollOffset() {
    float packedAlpha = floor(vertexColor.a * 255.0 + 0.5);
    float packedBlue = floor(vertexColor.b * 255.0 + 0.5);
    float alphaData = mod(packedAlpha, 128.0);
    vec2 highBits = floor(vertexColor.rg * 255.0 + 0.5) * 16.0;
    vec2 lowBits = vec2(floor(alphaData / 8.0), mod(alphaData, 8.0) + floor(packedBlue / 128.0) * 8.0);
    return (highBits + lowBits) / 4095.0;
}

float ecrZoom() {
    return mod(floor(vertexColor.b * 255.0 + 0.5), 128.0) / 127.0;
}

#define size ecrLocalSize()
#define scrollOffset ecrScrollOffset()
#define scrollSize vec2(1.0)
#define time (GameTime * 1200.0)
#define zoom (4.0 + ecrZoom() * 24.0)
#define starDensity ECR_STAR_DENSITY
#define starSize ECR_STAR_SIZE
#else
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 size;
uniform vec2 scrollOffset;
uniform vec2 scrollSize;
uniform float time;
uniform float zoom;
#define starDensity 1.0
#define starSize 1.0
#endif
