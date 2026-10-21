package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.common.menu.CraftingFrameMenu
import com.algorithmlx.ecr.client.widget.mim.MIMPressedButton
import com.algorithmlx.ecr.init.ECRModIDs
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class CraftingFrameScreen(menu: CraftingFrameMenu, inventory: Inventory, title: Component) : AbstractContainerScreen<CraftingFrameMenu>(
    menu, inventory, title, WIDTH, HEIGHT
) {
    private lateinit var clear: FrameButton
    private lateinit var exact: FrameButton
    private val buttonClicks = mutableSetOf<Int>()

    override fun init() {
        super.init()
        buttonClicks.clear()
        clear = addRenderableWidget(FrameButton(5, Component.translatable("screen.escr.mim.frame.clear"), CraftingFrameMenu.CLEAR_GRID, true))
        exact = addRenderableWidget(FrameButton(33, Component.translatable("screen.escr.mim.frame.exact"), CraftingFrameMenu.TOGGLE_SUBSTITUTION, false))
        updateButtons()
    }

    override fun containerTick() {
        super.containerTick()
        updateButtons()
    }

    private fun updateButtons() {
        exact.setTooltip(Tooltip.create(Component.translatable("screen.escr.mim.frame.exact_state", Component.translatable(if (menu.substituteIngredients) "options.off" else "options.on"))))
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.extractBackground(graphics, mouseX, mouseY, deltaTicks)
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0f, 0f, WIDTH, HEIGHT, 256, 256)
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {}

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val button = listOf(clear, exact).firstOrNull { it.isMouseOver(event.x(), event.y()) }
            ?: return super.mouseClicked(event, doubleClick)
        if (button.mouseClicked(event, doubleClick)) setFocused(button)
        buttonClicks.add(event.button())
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (!buttonClicks.remove(event.button())) return super.mouseReleased(event)
        clear.mouseReleased(event)
        exact.mouseReleased(event)
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean =
        buttonClicks.isNotEmpty() || super.mouseDragged(event, dragX, dragY)

    override fun hasClickedOutside(mouseX: Double, mouseY: Double, left: Int, top: Int): Boolean =
        !clear.isMouseOver(mouseX, mouseY) && !exact.isMouseOver(mouseX, mouseY) && super.hasClickedOutside(mouseX, mouseY, left, top)

    private inner class FrameButton(yOffset: Int, label: Component, id: Int, private val brush: Boolean) : MIMPressedButton(
        leftPos + WIDTH, topPos + yOffset, BUTTON_WIDTH, BUTTON_HEIGHT, label,
        { minecraft.gameMode?.handleInventoryButtonClick(menu.containerId, id) }
    ) {
        init {
            setTooltip(Tooltip.create(label))
        }

        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
            val enabled = !brush && !menu.substituteIngredients
            val u = WIDTH + if (isPressed) BUTTON_WIDTH else 0
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, u.toFloat(), 0f, width, height, 256, 256)
            if (brush)
                graphics.fakeItem(ItemStack(Items.BRUSH), x + (width - 16) / 2, y + (height - 16) / 2)
            else
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, if (enabled) CHECK else CROSS, x + (width - 9) / 2, y + (height - 9) / 2, 9, 9)
        }
    }

    companion object {
        private const val WIDTH = 176
        private const val HEIGHT = 166
        private const val BUTTON_WIDTH = 21
        private const val BUTTON_HEIGHT = 24
        private val TEXTURE = ECRModIDs.guiLocation("mim/crafting_frame")
        private val CHECK = Identifier.withDefaultNamespace("icon/checkmark")
        private val CROSS = Identifier.withDefaultNamespace("widget/cross_button")
    }
}
