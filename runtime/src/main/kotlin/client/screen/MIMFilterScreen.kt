package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.client.utils.MIMGuiTextures
import com.algorithmlx.ecr.common.menu.MIMFilterMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class MIMFilterScreen(menu: MIMFilterMenu, inventory: Inventory, title: Component) : AbstractContainerScreen<MIMFilterMenu>(
    menu, inventory, title, 176, MIMFilterMenu.HEIGHT
) {
    private lateinit var blacklist: Button
    private lateinit var components: Button
    private lateinit var damage: Button
    private lateinit var tags: Button

    override fun init() {
        super.init()
        blacklist = button(8, 60, 160, MIMFilterMenu.BLACKLIST)
        components = button(8, 82, 160, MIMFilterMenu.COMPONENTS)
        damage = button(8, 104, 78, MIMFilterMenu.DAMAGE)
        tags = button(90, 104, 78, MIMFilterMenu.TAGS)
        addRenderableWidget(Button.builder(Component.translatable("screen.escr.mim.filter.clear")) { send(MIMFilterMenu.CLEAR) }
            .bounds(leftPos + 8, topPos + 126, 160, 18).build())
        updateButtons()
    }

    private fun button(x: Int, y: Int, width: Int, id: Int): Button =
        addRenderableWidget(Button.builder(Component.empty()) { send(id) }.bounds(leftPos + x, topPos + y, width, 20).build())

    private fun send(id: Int) {
        minecraft.gameMode?.handleInventoryButtonClick(menu.containerId, id)
    }

    override fun containerTick() {
        super.containerTick()
        updateButtons()
    }

    private fun updateButtons() {
        blacklist.message = Component.translatable(if (menu.blacklist) "screen.escr.mim.filter.blacklist" else "screen.escr.mim.filter.whitelist")
        components.message = Component.translatable("screen.escr.mim.filter.components", Component.translatable(if (menu.ignoreComponents) "options.on" else "options.off"))
        damage.message = Component.translatable("screen.escr.mim.filter.damage", Component.translatable(if (menu.ignoreDamage) "screen.escr.mim.filter.damage.any" else "screen.escr.mim.filter.damage.exact"))
        tags.message = Component.translatable("screen.escr.mim.filter.tags", Component.translatable(if (menu.commonTags) "options.on" else "options.off"))
        components.active = menu.advanced
        damage.active = menu.advanced && !menu.ignoreComponents
        tags.active = menu.advanced
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.extractBackground(graphics, mouseX, mouseY, deltaTicks)
        MIMGuiTextures.editor(graphics, leftPos, topPos, imageWidth, imageHeight)
        menu.slots.forEach { slot ->
            MIMGuiTextures.slot(graphics, leftPos + slot.x, topPos + slot.y)
        }
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        graphics.text(font, font.plainSubstrByWidth(title.string, 160), 8, 8, TEXT, false)
        graphics.text(font, Component.translatable("screen.escr.mim.filter.samples"), 8, 22, TEXT, false)
        graphics.text(font, playerInventoryTitle, 8, MIMFilterMenu.PLAYER_Y - 10, TEXT, false)
    }

    companion object {
        private const val TEXT = MIMGuiTextures.TEXT
    }
}
