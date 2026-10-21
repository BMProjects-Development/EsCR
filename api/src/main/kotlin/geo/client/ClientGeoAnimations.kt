package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.geo.AnimationType
import com.algorithmlx.ecr.api.geo.GeoAnimatable
import com.algorithmlx.ecr.api.geo.GeoAnimationPlayback
import com.algorithmlx.ecr.api.geo.GeoAnimationState
import com.algorithmlx.ecr.api.geo.GeoBlockAnimationPayload
import com.algorithmlx.ecr.api.geo.GeoBlockAnimationStopPayload
import com.algorithmlx.ecr.api.geo.GeoEntityAnimationPayload
import com.algorithmlx.ecr.api.geo.GeoEntityAnimationStopPayload
import com.algorithmlx.ecr.api.geo.GeoItemAnimationPayload
import com.algorithmlx.ecr.api.geo.GeoItemAnimationStopPayload
import com.algorithmlx.ecr.api.geo.GeoItemProvider
import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

object ClientGeoAnimations {
    private const val TICKS_PER_SECOND = 20.0

    @JvmOverloads
    @JvmStatic
    fun play(
        animatable: GeoAnimatable,
        animation: String,
        type: AnimationType = AnimationType.PLAY_ONCE
    ): Boolean {
        if (!BedrockGeoAssets.hasAnimation(animation)) {
            LOGGER.error(
                "Cannot play GEO animation '{}': animation id is not present in loaded .animation.json files",
                animation
            )
            return false
        }
        val model = runCatching(animatable::geoModel).getOrElse { error ->
            LOGGER.error("Cannot play GEO animation '{}': unable to resolve model", animation, error)
            return false
        }
        if (BedrockGeoAssets[model] == null) {
            val geometry = model.geometryResource?.toString() ?: model.geometry
            LOGGER.error(
                "Cannot play GEO animation '{}': geometry '{}' is not loaded",
                animation,
                geometry
            )
            return false
        }

        animatable.geoAnimationState.play(animation, type, clientTimeSeconds())
        return true
    }

    fun stop(animatable: GeoAnimatable): Boolean {
        animatable.geoAnimationState.stop(GeoAnimationState.MAIN_LAYER)
        return true
    }

    @JvmOverloads
    @JvmStatic
    fun play(
        stack: ItemStack,
        animation: String,
        type: AnimationType = AnimationType.PLAY_ONCE
    ): Boolean {
        val provider = stack.item as? GeoItemProvider
        if (provider == null) {
            LOGGER.error("Cannot play GEO animation '{}': item {} is not a GEO item", animation, stack.item)
            return false
        }
        if (!BedrockGeoAssets.hasAnimation(animation)) {
            LOGGER.error(
                "Cannot play GEO animation '{}': animation id is not present in loaded .animation.json files",
                animation
            )
            return false
        }
        val model = runCatching { provider.geoModel(stack) }.getOrElse { error ->
            LOGGER.error("Cannot play GEO animation '{}': unable to resolve item model", animation, error)
            return false
        }
        if (BedrockGeoAssets[model] == null) {
            val geometry = model.geometryResource?.toString() ?: model.geometry
            LOGGER.error(
                "Cannot play GEO animation '{}': geometry '{}' is not loaded",
                animation,
                geometry
            )
            return false
        }

        provider.geoAnimationState(stack).play(animation, type, clientTimeSeconds())
        return true
    }

    fun stop(stack: ItemStack): Boolean {
        val provider = stack.item as? GeoItemProvider
        if (provider == null) {
            LOGGER.error("Cannot stop GEO animation: item {} is not a GEO item", stack.item)
            return false
        }
        provider.geoAnimationState(stack).stop(GeoAnimationState.MAIN_LAYER)
        return true
    }

    @JvmStatic
    fun handle(payload: GeoBlockAnimationPayload): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val animatable = level.getBlockEntity(payload.controllerPos) as? GeoAnimatable
        if (animatable == null) {
            LOGGER.error(
                "Cannot play GEO animation '{}': block entity at {} is not GEO-animatable",
                payload.animation,
                payload.controllerPos
            )
            return false
        }
        return play(animatable, payload.animation, payload.animationType)
    }

    @JvmStatic
    fun handle(payload: GeoEntityAnimationPayload): Boolean {
        val entity = Minecraft.getInstance().level?.getEntity(payload.entityId) as? GeoAnimatable
        if (entity == null) {
            LOGGER.error(
                "Cannot play GEO animation '{}': entity {} is not GEO-animatable",
                payload.animation,
                payload.entityId
            )
            return false
        }
        return play(entity, payload.animation, payload.animationType)
    }

    @JvmStatic
    fun handle(payload: GeoItemAnimationPayload): Boolean {
        val entity = Minecraft.getInstance().level?.getEntity(payload.entityId) as? LivingEntity
        val slot = EquipmentSlot.entries.getOrNull(payload.equipmentSlot)
        val stack = if (entity != null && slot != null) entity.getItemBySlot(slot) else null
        val provider = stack?.item as? GeoItemProvider
        if (entity == null || slot == null || stack == null || provider == null) {
            LOGGER.error(
                "Cannot play GEO animation '{}': entity {} slot {} is not a GEO item",
                payload.animation,
                payload.entityId,
                payload.equipmentSlot
            )
            return false
        }
        return play(stack, payload.animation, payload.animationType)
    }

    fun handle(payload: GeoBlockAnimationStopPayload): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val animatable = level.getBlockEntity(payload.controllerPos) as? GeoAnimatable
        if (animatable == null) {
            LOGGER.error("Cannot stop GEO animation: block entity at {} is not GEO-animatable", payload.controllerPos)
            return false
        }
        return stop(animatable)
    }

    fun handle(payload: GeoEntityAnimationStopPayload): Boolean {
        val entity = Minecraft.getInstance().level?.getEntity(payload.entityId) as? GeoAnimatable
        if (entity == null) {
            LOGGER.error("Cannot stop GEO animation: entity {} is not GEO-animatable", payload.entityId)
            return false
        }
        return stop(entity)
    }

    fun handle(payload: GeoItemAnimationStopPayload): Boolean {
        val entity = Minecraft.getInstance().level?.getEntity(payload.entityId) as? LivingEntity
        val slot = EquipmentSlot.entries.getOrNull(payload.equipmentSlot)
        val stack = if (entity != null && slot != null) entity.getItemBySlot(slot) else null
        if (stack == null) {
            LOGGER.error(
                "Cannot stop GEO animation: entity {} slot {} is not available",
                payload.entityId,
                payload.equipmentSlot
            )
            return false
        }
        return stop(stack)
    }

    fun snapshot(
        state: GeoAnimationState,
        context: MolangContext,
        nowSeconds: Double
    ): List<GeoAnimationPlayback> = state.snapshot(nowSeconds).filterNot { playback ->
        val animation = BedrockGeoAssets.animation(playback.animation) ?: return@filterNot false
        val finished = BedrockGeoAnimator.isFinished(animation, playback, context, nowSeconds)
        if (finished) state.removeIfCurrent(playback)
        finished
    }

    @JvmStatic
    fun onAssetsReload() {
        BedrockGeoRenderEngine.onAssetsReload()
    }

    @JvmStatic
    fun clientTimeSeconds(partialTick: Float = 0F): Double {
        val level = Minecraft.getInstance().level ?: return 0.0
        return (level.gameTime + partialTick) / TICKS_PER_SECOND
    }
}
