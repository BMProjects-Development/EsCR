#version 330
#extension GL_ARB_separate_shader_objects : require
#define ECR_RENDER_PIPELINE

layout(location = 0) in vec2 texCoord0;
layout(location = 0) out vec4 fragColor;

#include <escr:background_compat.glsl>

const float TAU = 6.28318530718;

float hash21(vec2 point) {
    point = fract(point * vec2(123.34, 456.21));
    point += dot(point, point + 45.32);
    return fract(point.x * point.y);
}

float valueNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);

    float a = hash21(cell);
    float b = hash21(cell + vec2(1.0, 0.0));
    float c = hash21(cell + vec2(0.0, 1.0));
    float d = hash21(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, local.x), mix(c, d, local.x), local.y);
}

float fbm(vec2 point) {
    float result = 0.0;
    float weight = 0.5;
    for (int octave = 0; octave < 3; octave++) {
        result += valueNoise(point) * weight;
        point = point * 2.03 + vec2(17.17, 9.23);
        weight *= 0.5;
    }
    return result;
}

mat2 rotate2d(float angle) {
    float sine = sin(angle);
    float cosine = cos(angle);
    return mat2(cosine, -sine, sine, cosine);
}

float polarAngle(vec2 point) {
    return dot(point, point) > 0.00000001 ? atan(point.y, point.x) : 0.0;
}

float gaussian(float distanceValue, float width) {
    float normalized = distanceValue / max(width, 0.00001);
    return exp(-normalized * normalized);
}

float softBloom(float distanceValue, float width) {
    float normalized = distanceValue / max(width, 0.00001);
    return 1.0 / (1.0 + normalized * normalized);
}

vec4 orbitalMaterial(float radius, float angle) {
    vec2 orbit = vec2(cos(angle), sin(angle));
    vec2 samplePoint = orbit * (2.0 + radius * 1.6) +
        vec2(radius * 4.3, -radius * 3.7);
    float coarseNoise = valueNoise(samplePoint);
    float mediumNoise = valueNoise(samplePoint * 2.03 + vec2(11.7, 4.2));
    float fineNoise = valueNoise(samplePoint * 4.11 + vec2(31.1, 18.6));
    float coarse = mix(coarseNoise, mediumNoise, 0.34);
    float field = mix(coarse, fineNoise, 0.16);
    float antialiasing = clamp(fwidth(field) * 1.5, 0.018, 0.14);

    float density = smoothstep(0.34 - antialiasing, 0.62 + antialiasing, field);
    float hotClumps = smoothstep(
        0.58 - antialiasing,
        0.78 + antialiasing,
        coarse * 0.82 + fineNoise * 0.18
    );
    float debris = smoothstep(
        0.72 - antialiasing,
        0.88 + antialiasing,
        fineNoise
    ) * smoothstep(0.42, 0.70, coarse);

    float phase = radius * 110.0 - angle * 4.0 +
        (coarse - 0.5) * 6.0 + (fineNoise - 0.5) * 1.5;
    float phaseFootprint = 110.0 * fwidth(radius) +
        4.0 * length(fwidth(orbit)) + 6.0 * fwidth(coarse) +
        1.5 * fwidth(fineNoise);
    float phaseVisibility = 1.0 - smoothstep(0.45, 1.25, phaseFootprint);
    float wave = 0.5 + 0.5 * sin(phase) * phaseVisibility;
    float fibers = smoothstep(0.58, 0.86, wave) *
        smoothstep(0.20, 0.65, density);
    return vec4(density, hotClumps, fibers, debris);
}

float shapeMask(float signedDistance) {
    float antialiasing = max(fwidth(signedDistance) * 1.25, 0.00045);
    return 1.0 - smoothstep(-antialiasing, antialiasing, signedDistance);
}

vec3 thermalColor(float heat, float brightSide) {
    vec3 ember = vec3(0.46, 0.045, 0.008);
    vec3 orange = vec3(1.0, 0.34, 0.055);
    vec3 gold = vec3(1.0, 0.72, 0.32);
    vec3 whiteHot = vec3(1.0, 0.985, 0.94);

    vec3 color = mix(ember, orange, smoothstep(0.0, 0.4, heat));
    color = mix(color, gold, smoothstep(0.3, 0.7, heat));
    color = mix(color, whiteHot, smoothstep(0.64, 1.0, heat));
    return mix(color, vec3(0.88, 0.93, 1.0), brightSide * pow(heat, 2.2) * 0.3);
}

float starLayer(vec2 point, float scale, float seed, out float randomValue) {
    vec2 scaledPoint = point * scale;
    vec2 cell = floor(scaledPoint);
    vec2 local = fract(scaledPoint) - 0.5;
    randomValue = hash21(cell + seed);
    vec2 jitter = vec2(hash21(cell + seed + 7.3), hash21(cell + seed + 19.1)) - 0.5;
    float radius = mix(0.016, 0.042, randomValue * randomValue) * starSize;
    float star = 1.0 - smoothstep(radius, radius + 0.035, length(local - jitter * 0.7));
    return star * smoothstep(0.984, 0.999, randomValue);
}

void main() {
    vec2 resolution = max(size, vec2(1.0));
    float aspect = resolution.x / resolution.y;

    vec2 point = texCoord0 - vec2(0.725, 0.5);
    point.x *= aspect;

    vec2 pan = (scrollOffset - 0.5) * vec2(aspect, 1.0);
    point += pan * 0.12;
    float zoomAmount = clamp((zoom - 4.0) / 24.0, 0.0, 1.0);
    point /= mix(0.92, 1.08, zoomAmount);

    float animationTime = time;

    const float diskOrbitSpeed = TAU * 18.0 / 1200.0;
    const float lensedOrbitSpeed = TAU * 9.0 / 1200.0;
    float imageRadius = length(point);
    float imageAngle = polarAngle(point);
    vec3 color = vec3(0.012, 0.016, 0.03);

    vec2 starPoint = point + pan * 0.035;
    float randomA;
    float randomB;
    float starsA = starLayer(starPoint, 78.0 * starDensity, 4.0, randomA);
    float starsB = starLayer(starPoint, 126.0 * starDensity, 23.0, randomB);
    color += mix(vec3(0.48, 0.64, 1.0), vec3(1.0, 0.62, 0.34), randomA) *
        starsA * (0.78 + 0.22 * sin(animationTime * 1.1 + randomA * TAU)) * 0.3;
    color += vec3(0.7, 0.77, 1.0) * starsB *
        (0.84 + 0.16 * sin(animationTime * 1.6 + randomB * TAU)) * 0.15;

    float backgroundCloud = fbm(point * 2.0 + vec2(animationTime * 0.005, 0.0));
    float cloudEnvelope = (1.0 - smoothstep(0.45, 1.3, imageRadius)) *
        smoothstep(0.54, 0.84, backgroundCloud);
    color += vec3(0.028, 0.012, 0.01) * cloudEnvelope;

    const float diskTilt = 0.43;
    const float inclination = 0.12;
    const float diskInnerRadius = 0.25;
    const float diskOuterRadius = 0.95;

    vec2 rotatedPoint = rotate2d(diskTilt) * point;
    vec2 diskPoint = vec2(rotatedPoint.x, rotatedPoint.y / inclination);
    float diskRadius = length(diskPoint);
    float diskAngle = polarAngle(diskPoint);
    float diskFlowAngle = diskAngle - animationTime * diskOrbitSpeed;
    float diskSignedDistance = max(
        diskInnerRadius - diskRadius,
        diskRadius - diskOuterRadius
    );
    float disk = shapeMask(diskSignedDistance);

    float sideAA = max(fwidth(rotatedPoint.y) * 1.5, 0.0007);
    float backHalf = smoothstep(-sideAA, sideAA, rotatedPoint.y);

    float heat = clamp(
        1.0 - (diskRadius - diskInnerRadius) / (diskOuterRadius - diskInnerRadius),
        0.0,
        1.0
    );
    heat = pow(heat, 0.58);

    vec4 diskMaterial = orbitalMaterial(diskRadius, diskFlowAngle);
    float diskFlow = 0.55 + diskMaterial.x * 0.16 +
        diskMaterial.y * 0.12 + diskMaterial.z * 0.1 +
        diskMaterial.w * 0.1;

    float brightSide = 1.0 - smoothstep(-0.5, 0.62, rotatedPoint.x);
    float radialFade = mix(0.1, 1.0, pow(heat, 0.62));
    vec3 accretionColor = thermalColor(heat, brightSide);

    float dustSide = smoothstep(-0.28, 0.48, rotatedPoint.x);
    float dustInclination = mix(0.42, 0.34, dustSide);
    float dustOuterRadius = mix(1.12, 0.98, dustSide);
    float dustCenterY = mix(-0.04, -0.015, dustSide);
    vec2 dustPoint = vec2(
        rotatedPoint.x,
        (rotatedPoint.y - dustCenterY) / dustInclination
    );
    float dustRadius = length(dustPoint);
    float dustAngle = polarAngle(dustPoint);
    float dustFlowAngle = dustAngle - animationTime * diskOrbitSpeed;
    vec2 dustOrbit = vec2(cos(dustFlowAngle), sin(dustFlowAngle));
    float dustHeat = pow(
        1.0 - smoothstep(0.25, dustOuterRadius, dustRadius),
        0.58
    );
    vec4 dustMaterial = orbitalMaterial(dustRadius, dustFlowAngle);
    float dustFineA = valueNoise(
        dustOrbit * 11.0 + vec2(dustRadius * 27.0, -dustRadius * 19.0)
    );
    float dustFineB = valueNoise(
        dustOrbit * 29.0 + vec2(dustRadius * 61.0, -dustRadius * 43.0)
    );
    float dustGrain = mix(dustFineA, dustFineB, 0.42);
    float dustRidge = 0.5 + 0.5 * sin(
        dustRadius * 94.0 - dustFlowAngle * 7.0 +
            dustFineA * 5.0 + dustFineB * 1.5
    );
    float dustClumps = smoothstep(
        0.3,
        0.72,
        dustMaterial.x * 0.34 + dustFineA * 0.4 +
            dustFineB * 0.18 + dustRidge * 0.08
    );
    float dustDensity = mix(0.34, 0.94, dustClumps) *
        mix(0.74, 1.14, dustGrain) * mix(0.86, 1.08, dustRidge);

    float dustEdgeWarp = (dustFineA - 0.5) * 0.018 +
        (dustFineB - 0.5) * 0.006;
    float dustGradient = max(
        length(vec2(dustPoint.x, dustPoint.y / dustInclination)) /
            max(dustRadius, 0.0001),
        1.0
    );
    float dustSignedDistance =
        (dustRadius - (dustOuterRadius + dustEdgeWarp)) / dustGradient;
    float dustOuterMask = 1.0 - smoothstep(-0.012, 0.06, dustSignedDistance);
    float dustInnerFade = smoothstep(0.24, 0.38, dustRadius);
    float dustLeft = 1.0 - smoothstep(-0.15, 0.45, rotatedPoint.x);
    float dustGain = mix(0.85, 1.4, dustLeft) *
        (0.16 + 0.74 * dustHeat);
    float dustUpper = smoothstep(-0.07, 0.08, rotatedPoint.y);
    float dustRight = smoothstep(0.0, 0.5, rotatedPoint.x);
    float dustVerticalGain = mix(
        mix(1.25, 0.65, dustRight),
        mix(1.0, 0.86, dustRight),
        dustUpper
    );
    vec3 dustColor = mix(
        vec3(0.31, 0.29, 0.35),
        thermalColor(dustHeat, dustLeft),
        smoothstep(0.04, 0.56, dustHeat)
    );
    float coolLeftTail = dustLeft * (1.0 - dustHeat) *
        smoothstep(0.28, 0.62, dustRadius) * 0.58;
    dustColor = mix(dustColor, vec3(0.48, 0.47, 0.58), coolLeftTail);
    float dustMask = dustOuterMask * dustInnerFade;
    color += dustColor * dustMask * dustDensity * dustGain * dustVerticalGain;
    color += dustColor * dustInnerFade * dustVerticalGain *
        gaussian(max(dustSignedDistance, 0.0), 0.075) * 0.035;

    float ellipseGradient = max(
        length(vec2(diskPoint.x, diskPoint.y / inclination)) /
            max(diskRadius, 0.0001),
        1.0
    );
    float outsideDiskDistance = max(diskSignedDistance, 0.0) / ellipseGradient;
    float diskHalo = gaussian(outsideDiskDistance, 0.038);
    float diskHaze = gaussian(outsideDiskDistance, 0.12);
    color += accretionColor * backHalf * radialFade *
        (diskHaze * 0.09 + diskHalo * 0.24);
    color += accretionColor * disk * backHalf * diskFlow * radialFade * 1.08;

    vec3 farDebris = vec3(0.0);
    vec3 nearDebris = vec3(0.0);
    for (int fragmentIndex = 0; fragmentIndex < 32; fragmentIndex++) {
        float fragmentId = float(fragmentIndex);
        float seedA = hash21(vec2(fragmentId + 3.1, 17.7));
        float seedB = hash21(vec2(fragmentId + 9.6, 41.3));
        float seedC = hash21(vec2(fragmentId + 25.4, 6.8));
        float seedD = hash21(vec2(fragmentId + 51.2, 29.4));

        float lensParticle = step(0.9, seedD);
        float innerParticle = 1.0 - step(0.42, seedA);
        float innerRadius = mix(0.29, 0.52, seedA / 0.42);
        float outerRadius = mix(0.5, 0.96, (seedA - 0.42) / 0.58);
        float diskFragmentRadius = mix(outerRadius, innerRadius, innerParticle);
        float lensRadius = mix(0.29, 0.45, seedA);
        float fragmentRadius = mix(
            diskFragmentRadius,
            lensRadius,
            lensParticle
        );
        float fragmentHeat = pow(
            1.0 - smoothstep(0.29, 0.9, fragmentRadius),
            0.56
        );
        float fragmentSpeed = mix(0.22, 0.62, pow(fragmentHeat, 1.25));
        fragmentSpeed = mix(fragmentSpeed, mix(0.36, 0.46, seedA), lensParticle);
        float loopTurns = floor(fragmentSpeed * 1200.0 / TAU + 0.5);
        fragmentSpeed = loopTurns * TAU / 1200.0;
        float fragmentAngle = seedB * TAU + animationTime * fragmentSpeed;
        float fragmentSine = sin(fragmentAngle);
        float fragmentCosine = cos(fragmentAngle);
        float nearWeight = (1.0 - lensParticle) *
            (1.0 - smoothstep(-0.12, 0.12, fragmentSine));
        float diskParticleInclination = mix(0.09, 0.15, seedD / 0.8);
        float particleInclination = mix(
            diskParticleInclination,
            1.0,
            lensParticle
        );
        vec2 fragmentPosition = vec2(
            fragmentCosine * fragmentRadius,
            fragmentSine * fragmentRadius * particleInclination
        );
        float fragmentDrop = 0.06 *
            (1.0 - smoothstep(0.65, 0.96, abs(fragmentPosition.x))) *
            (1.0 - lensParticle);
        fragmentPosition.y -= fragmentDrop * nearWeight;

        vec2 fragmentDelta = rotatedPoint - fragmentPosition;
        float sizePixels;
        float sizeClass;
        if (seedC < 0.58) {
            sizePixels = mix(0.9, 1.7, seedC / 0.58);
            sizeClass = 0.0;
        } else if (seedC < 0.92) {
            sizePixels = mix(1.8, 3.2, (seedC - 0.58) / 0.34);
            sizeClass = 0.55;
        } else {
            sizePixels = mix(3.6, 5.2, (seedC - 0.92) / 0.08);
            sizeClass = 1.0;
        }
        float rightParticle = smoothstep(-0.02, 0.48, fragmentPosition.x);
        sizePixels *= mix(1.0, mix(0.86, 0.62, sizeClass), rightParticle);
        sizePixels *= mix(1.0, 0.78, lensParticle);
        float fragmentSize = max(sizePixels / resolution.y, 0.0012);
        float fragmentAspect = mix(0.82, 1.24, seedD);
        float fragmentDistance = length(vec2(
            fragmentDelta.x / fragmentAspect,
            fragmentDelta.y
        ));
        float pixelAA = 0.72 / resolution.y;
        float fragmentCore = 1.0 - smoothstep(
            fragmentSize - pixelAA,
            fragmentSize + pixelAA,
            fragmentDistance
        );
        float fragmentGlow = gaussian(fragmentDistance, fragmentSize * 2.55);
        float fragmentBrightSide = 1.0 - smoothstep(
            -0.32,
            0.34,
            fragmentPosition.x
        );
        vec3 fragmentColor = thermalColor(fragmentHeat, fragmentBrightSide);
        fragmentColor = mix(
            fragmentColor,
            vec3(1.0, 0.2, 0.008),
            rightParticle * 0.62
        );
        float fragmentBrightness = mix(0.64, 1.52, sizeClass) *
            mix(0.72, 1.12, fragmentHeat);
        vec3 fragmentLight = fragmentColor *
            (fragmentCore * fragmentBrightness +
            fragmentGlow * mix(0.14, 0.24, sizeClass));
        float lensVisibility = smoothstep(-0.008, 0.028, fragmentPosition.y) *
            gaussian(abs(fragmentRadius - 0.35), 0.05);
        fragmentLight *= mix(1.0, lensVisibility, lensParticle);
        fragmentLight *= mix(1.0, 0.12, rightParticle);
        farDebris += fragmentLight * (1.0 - nearWeight);
        nearDebris += fragmentLight * nearWeight;
    }
    color += farDebris;

    const float horizonRadius = 0.275;
    float ringDistance = imageRadius - horizonRadius;
    vec2 ringDirection = point / max(imageRadius, 0.0001);
    float ringFlowAngle = imageAngle - animationTime * lensedOrbitSpeed;
    vec2 ringMaterialDirection = vec2(cos(ringFlowAngle), sin(ringFlowAngle));
    float ringUpper = 0.5 + 0.5 * ringDirection.y;
    float ringLeft = 0.5 - 0.5 * ringDirection.x;
    float sideCoordinate = clamp(
        abs(rotatedPoint.x) / max(imageRadius, 0.0001),
        0.0,
        1.0
    );
    float shoulders = pow(sideCoordinate, 8.0);

    float ringCoarse = fbm(
        ringMaterialDirection * 2.65 +
            vec2(ringDistance * 8.0, -ringDistance * 5.0)
    );
    float ringFine = valueNoise(
        ringMaterialDirection * 10.5 +
            vec2(ringDistance * 43.0, -ringDistance * 29.0)
    );
    float ringMicro = valueNoise(
        ringMaterialDirection * 27.0 +
            vec2(ringDistance * 96.0, -ringDistance * 71.0)
    );
    float ringWarp = (ringCoarse - 0.5) * 0.0032 +
        (ringFine - 0.5) * 0.0009;

    float lensedWidth = mix(
        0.135,
        0.178,
        smoothstep(0.05, 0.95, ringUpper)
    );
    lensedWidth *= mix(0.95, 1.05, ringCoarse);
    float normalizedRing = max(ringDistance, 0.0) /
        max(lensedWidth, 0.001);
    float ringEnvelope = exp(-pow(normalizedRing, 1.35)) *
        (1.0 - smoothstep(1.0, 1.55, normalizedRing));

    float phaseA = (ringDistance + ringWarp) * 245.0 +
        ringCoarse * 4.0;
    float phaseB = (ringDistance + ringWarp * 0.35) * 610.0 +
        ringFine * 7.0;
    float bandA = pow(0.5 + 0.5 * cos(phaseA), 3.5) *
        (1.0 - smoothstep(0.8, 1.8, fwidth(phaseA)));
    float bandB = pow(0.5 + 0.5 * cos(phaseB), 6.0) *
        (1.0 - smoothstep(0.8, 1.8, fwidth(phaseB)));
    float ringClumps = smoothstep(
        0.34,
        0.73,
        ringCoarse * 0.72 + ringFine * 0.28
    );
    float ringClump = clamp(
        0.58 + ringClumps * 0.24 + bandA * 0.11 + bandB * 0.05,
        0.58,
        1.0
    );
    float ringInnerRise = mix(
        0.08,
        1.0,
        smoothstep(0.004, 0.04, ringDistance)
    );
    float ringOuterFalloff = mix(
        1.0,
        0.22,
        smoothstep(0.055, 0.165, ringDistance)
    );
    float ringTailCut = mix(
        1.0,
        0.4,
        smoothstep(0.07, 0.17, ringDistance)
    );
    float ringMatter = ringEnvelope * (
        0.72 + ringClumps * 0.34 + bandA * 0.24 + bandB * 0.1
    ) * mix(0.86, 1.08, ringMicro) *
        ringInnerRise * ringOuterFalloff * ringTailCut;
    float innerHeat = 1.0 - smoothstep(0.0, lensedWidth, ringDistance);
    vec3 ringColor = mix(
        thermalColor(0.82 + innerHeat * 0.18, brightSide),
        vec3(1.0, 0.965, 0.94),
        0.38 + innerHeat * 0.34
    );
    float outerDirectionGain = clamp(
        mix(0.86, 1.06, ringLeft) * mix(0.92, 1.04, ringUpper),
        0.82,
        1.1
    );
    float ringDirectionalGain = mix(
        1.0,
        outerDirectionGain,
        smoothstep(0.045, 0.09, ringDistance)
    );
    float ringExposure = (1.55 + ringUpper * 3.95 + shoulders * 1.35) *
        ringDirectionalGain;
    color += ringColor * ringMatter * ringExposure;
    color += mix(vec3(0.9, 0.42, 0.12), ringColor, 0.68) *
        softBloom(ringDistance - lensedWidth * 0.28, lensedWidth * 0.9) *
        ringEnvelope * (0.22 + ringUpper * 0.22);

    float farBloom = exp(-pow(
        max(ringDistance - 0.035, 0.0) / 0.23,
        1.35
    )) * (1.0 - smoothstep(0.27, 0.36, ringDistance));
    float farBloomGrain = mix(0.9, 1.08, ringCoarse) *
        mix(0.95, 1.05, ringFine);
    vec3 farBloomColor = mix(
        vec3(0.41, 0.41, 0.48),
        vec3(0.52, 0.47, 0.48),
        smoothstep(0.05, 0.2, ringDistance)
    );
    float farBloomAngle = mix(
        0.18,
        1.0,
        pow(sideCoordinate, 1.5)
    ) * mix(0.86, 1.05, ringLeft);
    color += farBloomColor * farBloom * farBloomGrain *
        mix(0.72, 1.0, ringUpper) * farBloomAngle;

    float shellLimit = smoothstep(-0.002, 0.006, ringDistance) *
        (1.0 - smoothstep(0.21, 0.26, ringDistance));
    float shoulderCore = gaussian(ringDistance - 0.026, 0.024);
    float shoulderBody = gaussian(ringDistance - 0.082, 0.062);
    float shoulderTail = gaussian(ringDistance - 0.17, 0.11);
    vec3 shoulderColor = thermalColor(0.98, brightSide);
    color += shoulderColor * shoulders * shellLimit * (
        shoulderCore * 2.45 + shoulderBody * 1.75 + shoulderTail * 0.5
    );

    float horizonAA = max(fwidth(imageRadius) * 1.15, 0.0008);
    float horizonMask = 1.0 - smoothstep(
        horizonRadius,
        horizonRadius + horizonAA,
        imageRadius
    );
    color = mix(color, vec3(0.0), horizonMask);

    float horizonEdgeBloom = smoothstep(
        horizonRadius * 0.76,
        horizonRadius,
        imageRadius
    );
    float horizonBloomStrength =
        0.68 +
        0.16 * smoothstep(0.0, horizonRadius * 0.76, imageRadius) +
        horizonEdgeBloom * mix(0.25, 0.85, ringUpper);
    color += horizonMask * vec3(0.78, 0.78, 0.9) *
        horizonBloomStrength;

    float photonEdge = ringDistance;
    float ringWidth = max(2.0 / resolution.y, 0.0022);
    float outsideHorizon = smoothstep(
        0.0,
        horizonAA,
        photonEdge
    );
    float rimRipple = 0.9 + 0.2 * (0.5 + 0.5 *
        sin(imageAngle * 13.0 + 0.4) * sin(imageAngle * 7.0 - 1.1));
    float localRingWidth = ringWidth * rimRipple;
    float photonDistance = photonEdge - localRingWidth * 0.58;
    float photonCore = gaussian(photonDistance, localRingWidth * 0.84);
    float photonHalo = gaussian(max(photonEdge, 0.0), 0.017);
    float photonHaze = gaussian(max(photonEdge, 0.0), 0.052);
    float photonTexture = 0.86 + ringClump * 0.14;
    vec3 photonColor = thermalColor(0.88, brightSide);
    color += outsideHorizon * (
        vec3(1.0, 0.28, 0.035) * photonHaze *
            (0.04 + ringClump * 0.07) +
        vec3(1.0, 0.56, 0.12) * photonHalo *
            (0.24 + ringClump * 0.16) +
        photonColor * photonCore * photonTexture *
            (0.72 + shoulders * 0.46)
    );

    float frontHalf = 1.0 - backHalf;
    color += accretionColor * frontHalf * radialFade * outsideHorizon *
        (diskHaze * 0.12 + diskHalo * 0.3);
    color += accretionColor * disk * frontHalf * diskFlow * radialFade *
        outsideHorizon * 1.0;

    float ribbonCurve = clamp(rotatedPoint.x / diskOuterRadius, -1.0, 1.0);
    float ribbonLine = -0.037 + 0.016 * ribbonCurve * ribbonCurve;
    float ribbonEnvelope = 1.0 - smoothstep(0.86, 1.03, abs(rotatedPoint.x));
    float ribbonRadius = length(vec2(rotatedPoint.x, ribbonLine / inclination));
    float ribbonAngle = polarAngle(vec2(
        rotatedPoint.x,
        ribbonLine / inclination
    ));
    float ribbonHeat = pow(
        1.0 - smoothstep(0.24, diskOuterRadius, abs(rotatedPoint.x)),
        0.42
    );
    float ribbonFlowAngle = ribbonAngle - animationTime * diskOrbitSpeed;
    vec4 ribbonMaterial = orbitalMaterial(ribbonRadius, ribbonFlowAngle);
    float ribbonWarp = (ribbonMaterial.x - 0.5) * 0.001 +
        (ribbonMaterial.z - 0.25) * 0.0005;
    float ribbonSignedDistance = rotatedPoint.y - ribbonLine - ribbonWarp;
    float ribbonDistance = abs(ribbonSignedDistance);
    float ribbonFlow = 0.8 + ribbonMaterial.x * 0.1 +
        ribbonMaterial.y * 0.08 + ribbonMaterial.z * 0.1 +
        ribbonMaterial.w * 0.14;
    float ribbonGlowFlow = 0.82 + 0.18 * clamp(
        ribbonMaterial.x * 0.65 + ribbonMaterial.y * 0.35,
        0.0,
        1.0
    );
    float ribbonBrightSide = smoothstep(-0.45, 0.5, rotatedPoint.x);
    vec3 ribbonColor = thermalColor(ribbonHeat, ribbonBrightSide);

    float ribbonCoreWidth = 0.022;
    ribbonCoreWidth = max(ribbonCoreWidth, fwidth(ribbonDistance) * 1.4);
    float ribbonCore = gaussian(ribbonDistance, ribbonCoreWidth);
    float ribbonBody = gaussian(ribbonDistance, 0.05);
    float ribbonHalo = gaussian(ribbonDistance, 0.07);
    float ribbonHaze = softBloom(ribbonDistance, 0.125);
    float ribbonHazeVisible = mix(
        ribbonHaze,
        gaussian(ribbonDistance, 0.075),
        horizonMask
    );
    float ribbonFiberA = gaussian(
        ribbonSignedDistance - 0.035 - (ribbonMaterial.x - 0.5) * 0.006,
        0.013
    );
    float ribbonFiberB = gaussian(
        ribbonSignedDistance + 0.046 + (ribbonMaterial.y - 0.5) * 0.007,
        0.017
    );
    float ribbonParticleMask = ribbonEnvelope *
        gaussian(ribbonDistance, 0.085);
    color += nearDebris * max(outsideHorizon, ribbonParticleMask);
    color += ribbonEnvelope * (
        vec3(1.0, 0.42, 0.12) * ribbonHazeVisible * ribbonGlowFlow * 0.42 +
        vec3(1.0, 0.68, 0.32) * ribbonHalo * ribbonGlowFlow * 1.25 +
        ribbonColor * ribbonBody * ribbonFlow * 1.45 +
        mix(ribbonColor, vec3(1.0, 0.985, 0.94), ribbonHeat * 0.8) *
            ribbonCore * 3.6
    );
    color += ribbonEnvelope * mix(ribbonColor, vec3(1.0, 0.88, 0.68), 0.38) * (
        ribbonFiberA * (0.32 + ribbonMaterial.y * 0.5) +
        ribbonFiberB * (0.24 + ribbonMaterial.w * 0.44)
    );

    float horizonLuminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(
        color,
        horizonLuminance * vec3(0.99, 0.99, 1.04),
        horizonMask * 0.72
    );

    color = vec3(1.0) - exp(-color * 1.08);
    color = pow(color, vec3(0.94));
    fragColor = vec4(color, 1.0);
}
