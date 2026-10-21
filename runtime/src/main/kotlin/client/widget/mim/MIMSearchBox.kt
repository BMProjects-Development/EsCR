package com.algorithmlx.ecr.client.widget.mim

import com.algorithmlx.ecr.common.menu.MIMMenu
import com.algorithmlx.ecr.common.menu.MIMTerminalLayout
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.network.chat.Component

class MIMSearchBox(font: Font) : EditBox(
    font, 0, 0, MIMTerminalLayout.SEARCH_WIDTH, TEXT_HEIGHT, Component.literal(">_")
) {
    init {
        isBordered = false
        setMaxLength(MIMMenu.MAX_SEARCH)
        setTextColor(-1)
        setHint(Component.literal(">_").withStyle(ChatFormatting.GRAY))
    }

    fun layout(left: Int, top: Int) {
        x = left + MIMTerminalLayout.SEARCH_X
        y = top + MIMTerminalLayout.SEARCH_Y + VERTICAL_PADDING
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean =
        visible && mouseX >= x && mouseX < x + width && mouseY >= y - VERTICAL_PADDING &&
            mouseY < y - VERTICAL_PADDING + MIMTerminalLayout.SEARCH_HEIGHT

    override fun getRectangle(): ScreenRectangle =
        ScreenRectangle(x, y - VERTICAL_PADDING, width, MIMTerminalLayout.SEARCH_HEIGHT)

    companion object {
        private const val TEXT_HEIGHT = 8
        private const val VERTICAL_PADDING = (MIMTerminalLayout.SEARCH_HEIGHT - TEXT_HEIGHT) / 2
    }
}
