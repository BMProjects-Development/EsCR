package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.geo.GeoItemProvider
import com.algorithmlx.ecr.api.utils.ecRL
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.serialization.MapCodec
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.special.SpecialModelRenderer
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.joml.Vector3f
import org.joml.Vector3fc
import java.util.function.Consumer

data class BedrockGeoItemRenderArgument(val data: BedrockGeoRenderData?)

object BedrockGeoItemRenderer : SpecialModelRenderer<BedrockGeoItemRenderArgument> {
    @JvmField
    val ID: Identifier = "bedrock_geo".ecRL

    override fun extractArgument(stack: ItemStack): BedrockGeoItemRenderArgument {
        val provider = stack.item as? GeoItemProvider ?: return BedrockGeoItemRenderArgument(null)
        val partialTick = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        val now = ClientGeoAnimations.clientTimeSeconds(partialTick)
        val molang = provider.geoMolangContext(stack, partialTick)
        return BedrockGeoItemRenderArgument(
            BedrockGeoRenderEngine.extract(
                provider.geoModel(stack),
                ClientGeoAnimations.snapshot(provider.geoAnimationState(stack), molang, now),
                molang,
                now
            )
        )
    }

    override fun submit(
        data: BedrockGeoItemRenderArgument?,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        packedLight: Int,
        packedOverlay: Int,
        hasFoil: Boolean,
        outlineColor: Int
    ) {
        val geo = data?.data ?: return
        BedrockGeoRenderEngine.submit(geo, poseStack, collector, packedLight, packedOverlay)
    }

    override fun getExtents(output: Consumer<Vector3fc>) {
        output.accept(Vector3f(-0.5F, 0F, -0.5F))
        output.accept(Vector3f(0.5F, 1F, 0.5F))
    }

    object Unbaked : SpecialModelRenderer.Unbaked<BedrockGeoItemRenderArgument> {
        @JvmField
        val CODEC: MapCodec<Unbaked> = MapCodec.unit(Unbaked)

        override fun bake(context: SpecialModelRenderer.BakingContext) = BedrockGeoItemRenderer

        override fun type(): MapCodec<out SpecialModelRenderer.Unbaked<BedrockGeoItemRenderArgument>> = CODEC
    }
}
