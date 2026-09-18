#version 330
#extension GL_ARB_separate_shader_objects : require

#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#include <minecraft:light.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec3 Normal;
layout(location = 3) in int BoneIndex;

uniform samplerBuffer GeoMatrices;

layout(std140) uniform GeoInfo {
    ivec4 GeoData;
};

#ifndef NO_OVERLAY
uniform sampler2D Sampler1;
#endif

#ifndef EMISSIVE
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
layout(location = 2) out vec4 vertexPerFaceColorBack;
layout(location = 3) out vec4 vertexPerFaceColorFront;
#else
layout(location = 2) out vec4 vertexColor;
#endif

#ifndef EMISSIVE
layout(location = 4) out vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
layout(location = 5) out vec4 overlayColor;
#endif

layout(location = 6) out vec2 texCoord0;

mat4 readBoneMatrix(int base) {
    return mat4(
        texelFetch(GeoMatrices, base),
        texelFetch(GeoMatrices, base + 1),
        texelFetch(GeoMatrices, base + 2),
        texelFetch(GeoMatrices, base + 3)
    );
}

mat3 readBoneNormalMatrix(int base) {
    return mat3(
        texelFetch(GeoMatrices, base).xyz,
        texelFetch(GeoMatrices, base + 1).xyz,
        texelFetch(GeoMatrices, base + 2).xyz
    );
}

void main() {
    int paletteStride = GeoData.y;
    int instanceBase = gl_InstanceIndex * paletteStride;
    vec4 metadata = texelFetch(GeoMatrices, instanceBase);
    int boneBase = instanceBase + 1 + BoneIndex * 7;
    mat4 boneMatrix = readBoneMatrix(boneBase);
    mat3 normalMatrix = readBoneNormalMatrix(boneBase + 4);
    vec3 skinnedPosition = (boneMatrix * vec4(Position, 1.0)).xyz;
    vec3 skinnedNormal = normalize(normalMatrix * Normal);

    gl_Position = ProjMat * ModelViewMat * vec4(skinnedPosition, 1.0);
    sphericalVertexDistance = fog_spherical_distance(skinnedPosition);
    cylindricalVertexDistance = fog_cylindrical_distance(skinnedPosition);

#ifdef PER_FACE_LIGHTING
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, skinnedNormal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, vec4(1.0));
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, vec4(1.0));
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = vec4(1.0);
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, skinnedNormal, vec4(1.0));
#endif

#ifndef EMISSIVE
    lightMapColor = sample_lightmap(Sampler2, ivec2(round(metadata.xy)));
#endif

#ifndef NO_OVERLAY
    overlayColor = texelFetch(Sampler1, ivec2(round(metadata.zw)), 0);
#endif

    texCoord0 = UV0;
}
