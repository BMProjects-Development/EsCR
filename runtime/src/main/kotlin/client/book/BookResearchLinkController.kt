package com.algorithmlx.ecr.client.book

import com.algorithmlx.ecr.api.client.research.BookElementRenderContext
import com.algorithmlx.ecr.api.research.content.BookResearchLink
import com.mojang.blaze3d.platform.cursor.CursorTypes

object BookResearchLinkController {
    private var hovered: BookResearchLink? = null

    fun beginFrame() {
        hovered = null
    }

    fun hovered(): BookResearchLink? = hovered

    fun hover(
        context: BookElementRenderContext,
        target: BookResearchLink,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Boolean {
        if (width <= 0 || height <= 0) return false
        if (context.mouseX !in x ..< x + width || context.mouseY !in y ..< y + height) return false
        hovered = target
        context.graphics.requestCursor(CursorTypes.POINTING_HAND)
        return true
    }
}
