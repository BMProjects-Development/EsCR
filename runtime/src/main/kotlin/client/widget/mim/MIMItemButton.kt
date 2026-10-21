package com.algorithmlx.ecr.client.widget.mim

import com.algorithmlx.ecr.client.utils.MIMGuiTextures
import com.algorithmlx.ecr.common.menu.MIMTerminalLayout
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Item

internal class MIMItemButton(private var icon: ItemStack, label: Component, action: () -> Unit) : Button(
    0, 0, MIMTerminalLayout.CRAFT_BUTTON_WIDTH, MIMTerminalLayout.CRAFT_BUTTON_HEIGHT,
    label, { action() }, DEFAULT_NARRATION
) {
    init {
        setTooltip(Tooltip.create(label))
    }

    fun update(item: Item, label: Component) {
        if (!icon.`is`(item)) icon = ItemStack(item)
        if (message == label) return
        message = label
        setTooltip(Tooltip.create(label))
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        MIMGuiTextures.craftingButton(graphics, x, y)
        graphics.fakeItem(icon, x + (width - 16) / 2 - 1, y + (height - 16) / 2)
    }
}
