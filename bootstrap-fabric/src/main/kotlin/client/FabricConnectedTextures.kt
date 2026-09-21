package com.algorithmlx.ecr.fabric.client

import com.algorithmlx.ecr.api.client.texture.ConnectedTexture
import com.algorithmlx.ecr.api.client.texture.ConnectedTextureMask
import com.algorithmlx.ecr.api.client.texture.ConnectedTextureRegion
import com.algorithmlx.ecr.api.client.texture.ConnectedTextureRotation
import com.algorithmlx.ecr.api.client.texture.ConnectedTextures
import com.mojang.blaze3d.platform.Transparency
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.ModelDebugName
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.client.resources.model.sprite.Material
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import java.util.function.Predicate

object FabricConnectedTextures {
    fun init() {
        ModelLoadingPlugin.register { pluginContext ->
            pluginContext.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE) { model, context ->
                val texture = ConnectedTextures.get(context.state().block)
                    ?: return@register model
                val debugName = ModelDebugName { "connected texture for ${context.state().block}" }
                val variants = Array(ConnectedTextureMask.VARIANT_COUNT) { mask ->
                    val variant = texture.variants[mask] ?: return@Array null
                    val material = context.baker().materials().get(Material(variant.sprite), debugName)
                    if (material.sprite().contents().name() != variant.sprite) {
                        null
                    } else {
                        val region = resolveRegion(material.sprite(), variant.region)
                            ?: return@Array null
                        ResolvedVariant(material, variant.rotation, region)
                    }
                }

                FabricConnectedTextureModel(
                    delegate = model,
                    texture = texture,
                    variants = variants,
                    spriteFinder = context.baker().materials().spriteFinder(QuadAtlas.BLOCK)
                )
            }
        }
    }
}

private class FabricConnectedTextureModel(
    private val delegate: BlockStateModel,
    private val texture: ConnectedTexture,
    private val variants: Array<ResolvedVariant?>,
    private val spriteFinder: SpriteFinder
): WrapperBlockStateModel(delegate) {
    private val variantFlags = IntArray(variants.size) { variants[it]?.let(::materialFlags) ?: 0 }
    private val allVariantFlags = variantFlags.fold(0, Int::or)

    override fun emitQuads(
        emitter: QuadEmitter,
        level: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        cullTest: Predicate<Direction?>
    ) {
        val packedMask = texture.packedMask(level, pos, state)

        emitter.pushTransform { quad ->
            transformQuad(quad, packedMask)
            true
        }
        try {
            delegate.emitQuads(emitter, level, pos, state, random, cullTest)
        } finally {
            emitter.popTransform()
        }
    }

    override fun createGeometryKey(
        level: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource
    ): Any? {
        val delegateKey = delegate.createGeometryKey(level, pos, state, random) ?: return null
        return GeometryKey(texture, delegateKey, texture.packedMask(level, pos, state))
    }

    override fun materialFlags(): Int = delegate.materialFlags() or allVariantFlags

    override fun materialFlags(
        level: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource
    ): Int {
        val packedMask = texture.packedMask(level, pos, state)
        var flags = delegate.materialFlags(level, pos, state, random)
        for (face in texture.faces) {
            flags = flags or variantFlags[ConnectedTextureMask.unpack(packedMask, face)]
        }
        return flags
    }

    override fun hasMaterialFlag(
        level: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        flag: Int
    ): Boolean = materialFlags(level, pos, state, random) and flag != 0

    private fun transformQuad(quad: MutableQuadView, packedMask: Int) {
        val face = quad.lightFace()
        if (face !in texture.faces) return

        val sourceSprite = spriteFinder.find(quad)
        if (sourceSprite.contents().name() != texture.source) return

        val mask = ConnectedTextureMask.unpack(packedMask, face)
        val target = variants[mask] ?: return
        if (
            target.material.sprite() === sourceSprite &&
            target.rotation == ConnectedTextureRotation.NONE &&
            target.region == ResolvedRegion.FULL
        ) {
            return
        }

        moveUvs(quad, sourceSprite, target)
    }

    private data class GeometryKey(
        val texture: ConnectedTexture,
        val delegate: Any,
        val packedMask: Int
    )

    companion object {
        private fun moveUvs(
            quad: MutableQuadView,
            source: TextureAtlasSprite,
            target: ResolvedVariant
        ) {
            val sourceWidth = source.u1 - source.u0
            val sourceHeight = source.v1 - source.v0
            val targetSprite = target.material.sprite()

            for (vertex in 0..<BakedQuad.VERTEX_COUNT) {
                val u = (quad.u(vertex) - source.u0) / sourceWidth
                val v = (quad.v(vertex) - source.v0) / sourceHeight
                val rotatedU = rotatedU(target.rotation, u, v)
                val rotatedV = rotatedV(target.rotation, u, v)
                quad.uv(
                    vertex,
                    targetSprite.getU(target.region.u + rotatedU * target.region.width),
                    targetSprite.getV(target.region.v + rotatedV * target.region.height)
                )
            }
            quad.postMaterialBake(target.material)
        }

        private fun materialFlags(variant: ResolvedVariant): Int {
            val material = variant.material
            var flags = 0
            val transparency = if (material.forceTranslucent()) {
                Transparency.TRANSLUCENT
            } else {
                material.sprite().contents().transparency()
            }
            if (ChunkSectionLayer.byTransparency(transparency).translucent()) {
                flags = flags or BakedQuad.FLAG_TRANSLUCENT
            }
            if (material.sprite().isAnimated) {
                flags = flags or BakedQuad.FLAG_ANIMATED
            }
            return flags
        }

        private fun rotatedU(rotation: ConnectedTextureRotation, u: Float, v: Float): Float = when (rotation) {
            ConnectedTextureRotation.NONE -> u
            ConnectedTextureRotation.CLOCKWISE_90 -> v
            ConnectedTextureRotation.CLOCKWISE_180 -> 1f - u
            ConnectedTextureRotation.CLOCKWISE_270 -> 1f - v
        }

        private fun rotatedV(rotation: ConnectedTextureRotation, u: Float, v: Float): Float = when (rotation) {
            ConnectedTextureRotation.NONE -> v
            ConnectedTextureRotation.CLOCKWISE_90 -> 1f - u
            ConnectedTextureRotation.CLOCKWISE_180 -> 1f - v
            ConnectedTextureRotation.CLOCKWISE_270 -> u
        }
    }
}

private data class ResolvedVariant(
    val material: Material.Baked,
    val rotation: ConnectedTextureRotation,
    val region: ResolvedRegion
)

private data class ResolvedRegion(
    val u: Float,
    val v: Float,
    val width: Float,
    val height: Float
) {
    companion object {
        val FULL = ResolvedRegion(0f, 0f, 1f, 1f)
    }
}

private fun resolveRegion(
    sprite: TextureAtlasSprite,
    region: ConnectedTextureRegion?
): ResolvedRegion? {
    if (region == null) return ResolvedRegion.FULL

    val spriteWidth = sprite.contents().width()
    val spriteHeight = sprite.contents().height()
    if (
        region.width > spriteWidth ||
        region.height > spriteHeight ||
        region.x > spriteWidth - region.width ||
        region.y > spriteHeight - region.height
    ) {
        return null
    }

    return ResolvedRegion(
        u = region.x.toFloat() / spriteWidth,
        v = region.y.toFloat() / spriteHeight,
        width = region.width.toFloat() / spriteWidth,
        height = region.height.toFloat() / spriteHeight
    )
}
