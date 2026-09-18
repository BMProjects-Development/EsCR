package com.algorithmlx.ecr.api.geo

import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import java.util.WeakHashMap

data class GeoModel(
    val geometry: String,
    val texture: Identifier,
    val renderType: GeoRenderType = GeoRenderType.CUTOUT,
    val scale: Float = 1F,
    val shadowRadius: Float = 0F,
    val geometryResource: Identifier? = null,
    val lightMode: GeoLightMode = GeoLightMode.WORLD,
    val blockRotation: GeoBlockRotation = GeoBlockRotation.NONE
) {
    constructor(
        geometry: Identifier,
        texture: Identifier,
        renderType: GeoRenderType = GeoRenderType.CUTOUT,
        scale: Float = 1F,
        shadowRadius: Float = 0F,
        lightMode: GeoLightMode = GeoLightMode.WORLD,
        blockRotation: GeoBlockRotation = GeoBlockRotation.NONE
    ) : this(geometry.toString(), texture, renderType, scale, shadowRadius, geometry, lightMode, blockRotation)

    init {
        require(geometry.isNotBlank()) { "GEO geometry identifier must not be blank" }
        require(scale > 0F && scale.isFinite()) { "GEO model scale must be positive and finite" }
        require(shadowRadius >= 0F && shadowRadius.isFinite()) {
            "GEO model shadow radius must be non-negative and finite"
        }
    }
}

data class GeoBlockRotation(
    val enabled: Boolean = true,
    val opposite: Boolean = false
) {
    companion object {
        @JvmField
        val NONE = GeoBlockRotation(enabled = false)

        @JvmField
        val FACING = GeoBlockRotation()

        @JvmField
        val OPPOSITE_FACING = GeoBlockRotation(opposite = true)
    }
}

enum class GeoRenderType {
    SOLID,
    CUTOUT,
    TRANSLUCENT,
    ADDITIVE
}

enum class GeoLightMode {
    WORLD,
    FULL_BRIGHT
}

interface GeoAnimatable {
    val geoModel: GeoModel
    val geoAnimationState: GeoAnimationState

    fun geoMolangContext(partialTick: Float): MolangContext = MolangContext.EMPTY
}

interface GeoItemProvider {
    fun geoModel(stack: ItemStack): GeoModel

    fun geoAnimationState(stack: ItemStack): GeoAnimationState = GeoItemAnimationStates[stack]

    fun geoMolangContext(stack: ItemStack, partialTick: Float): MolangContext =
        MolangContext.EMPTY
}

object GeoItemAnimationStates {
    private val states = WeakHashMap<ItemStack, GeoAnimationState>()

    @Synchronized
    operator fun get(stack: ItemStack): GeoAnimationState =
        states.getOrPut(stack) { GeoAnimationState() }
}
