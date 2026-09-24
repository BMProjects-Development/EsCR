package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.client.MRULineAnimation
import com.algorithmlx.ecr.api.client.drawMRULine
import com.algorithmlx.ecr.api.mru.MRUType
import com.algorithmlx.ecr.api.mru.storage.MRUStorage
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.HeatGeneratorMenu
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import kotlin.math.ceil

class HeatGeneratorScreen(menu: HeatGeneratorMenu, inventory: Inventory, title: Component): AbstractContainerScreen<HeatGeneratorMenu>(menu, inventory, title) {
    private val mruAnimation = MRULineAnimation()
    private val displayedStorage = object : MRUStorage {
        override val mru: Int get() = menu.mru
        override val mruCapacity: Int get() = menu.mruCapacity
        override val mruType: MRUType get() = MRUTypeRegistry.radiationUnit
    }
    private lateinit var unitButton: Button

    override fun init() {
        super.init()
        this.unitButton = Button.builder(Component.literal(this.menu.temperatureUnit.symbol)) {
            val player = this.minecraft.player ?: return@builder
            if (!this.menu.clickMenuButton(player, HeatGeneratorMenu.CYCLE_TEMPERATURE_UNIT_BUTTON)) return@builder
            this.minecraft.gameMode?.handleInventoryButtonClick(this.menu.containerId, HeatGeneratorMenu.CYCLE_TEMPERATURE_UNIT_BUTTON)
        }.bounds(this.leftPos + UNIT_BUTTON_X, this.topPos + UNIT_BUTTON_Y, UNIT_BUTTON_WIDTH, UNIT_BUTTON_HEIGHT).build()
        this.unitButton.visible = this.menu.isUpgraded
        this.addRenderableWidget(this.unitButton)
    }

    override fun containerTick() {
        super.containerTick()
        this.unitButton.message = Component.literal(this.menu.temperatureUnit.symbol)
        this.unitButton.active = this.menu.isUpgraded
        this.unitButton.visible = this.menu.isUpgraded
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.extractBackground(graphics, mouseX, mouseY, deltaTicks)
        graphics.blit(
            RenderPipelines.GUI_TEXTURED, TEXTURE,
            this.leftPos, this.topPos,
            0F, 0F,
            this.imageWidth, this.imageHeight,
            256, 256
        )

        drawMRULine(
            graphics, this.displayedStorage,
            26, 28,
            this.leftPos, this.topPos,
            124, 8,
            mouseX, mouseY,
            animation = this.mruAnimation,
            deltaTicks = deltaTicks
        )

        val mode = Component.translatable(
            if (this.menu.isUpgraded) "screen.$ModId.heat_generator.mode.ultra"
            else "screen.$ModId.heat_generator.mode.normal"
        )
        val temperature = if (this.menu.isUpgraded) Component.translatable(
            "screen.$ModId.heat_generator.temperature",
            this.menu.temperatureUnit.formatCelsius(this.menu.temperatureCelsius)
        ) else mode

        val generation = Component.translatable("screen.$ModId.heat_generator.generation", this.menu.generation)
        val burnHeight = if (this.menu.maxBurnTime <= 0) 0
        else ceil(this.menu.burnTimeRemaining * FIRE_PROGRESS_HEIGHT.toDouble() / this.menu.maxBurnTime)
            .toInt()
            .coerceIn(0, FIRE_PROGRESS_HEIGHT)

        graphics.text(this.font, temperature, this.leftPos + 8, this.topPos + 8, TEXT_COLOR, false)
        graphics.text(this.font, generation, this.leftPos + 8, this.topPos + 18, TEXT_COLOR, false)
        if (burnHeight > 0) graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            FIRE_SPRITE,
            FIRE_SIZE, FIRE_SIZE,
            0, FIRE_SIZE - burnHeight,
            this.leftPos + FIRE_X, this.topPos + HeatGeneratorMenu.MACHINE_SLOT_Y + FIRE_SIZE - burnHeight,
            FIRE_SIZE, burnHeight
        )
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) = Unit

    companion object {
        private val TEXTURE = ECRModIDs.guiLocation(ECRModIDs.MAGICAL_TELEPORTER)
        private val FIRE_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress")
        private const val UNIT_BUTTON_X = 150
        private const val UNIT_BUTTON_Y = 5
        private const val UNIT_BUTTON_WIDTH = 16
        private const val UNIT_BUTTON_HEIGHT = 16
        private const val FIRE_X = 81
        private const val FIRE_SIZE = 14
        private const val FIRE_PROGRESS_HEIGHT = 13
        private const val TEXT_COLOR = 0xFF404040.toInt()
    }
}
