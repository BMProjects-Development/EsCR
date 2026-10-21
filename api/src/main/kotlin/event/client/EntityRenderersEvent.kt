package com.algorithmlx.ecr.api.event.client

import com.algorithmlx.ecr.api.event.engine.Event
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType

sealed class EntityRenderersEvent : Event() {
    class RegisterRenderers(private val registrar: BlockEntityRendererRegistrar): EntityRenderersEvent() {
        fun <T : BlockEntity, S : BlockEntityRenderState> registerBlockEntityRenderer(
            blockEntityType: BlockEntityType<out T>, rendererProvider: BlockEntityRendererProvider<T, S>
        ) = registrar.register(blockEntityType, rendererProvider)
    }

    class RegisterLayerDefinitions(private val registrar: LayerDefinitionRegistrar): EntityRenderersEvent() {
        fun registerLayerDefinition(layerLocation: ModelLayerLocation, supplier: () -> LayerDefinition) = registrar.register(
            layerLocation, supplier
        )
    }

    interface BlockEntityRendererRegistrar {
        fun <T : BlockEntity, S : BlockEntityRenderState> register(
            blockEntityType: BlockEntityType<out T>,
            rendererProvider: BlockEntityRendererProvider<T, S>
        )
    }

    fun interface LayerDefinitionRegistrar {
        fun register(layerLocation: ModelLayerLocation, supplier: () -> LayerDefinition)
    }
}
