package com.algorithmlx.ecr.client.book.renderer

import com.algorithmlx.ecr.api.client.research.BookElementRenderContext
import com.algorithmlx.ecr.api.client.research.BookElementRenderers
import com.algorithmlx.ecr.api.research.content.GroupBookElement
import com.algorithmlx.ecr.client.book.BookGroupLayout
import kotlin.math.roundToInt

object BookGroupRenderer {
    fun render(
        context: BookElementRenderContext,
        element: GroupBookElement
    ) {
        BookGroupLayout.layout(element, context.width, context.research).elements.forEachIndexed { index, placement ->
            if (placement.y >= context.height || placement.x >= context.width) return@forEachIndexed
            val width = minOf(placement.width, context.width - placement.x).coerceAtLeast(0)
            val height = minOf(placement.height, context.height - placement.y).coerceAtLeast(0)
            if (width == 0 && height == 0) return@forEachIndexed

            val screenX = context.screenX + (placement.x * context.scale).roundToInt()
            val screenY = context.screenY + (placement.y * context.scale).roundToInt()
            val screenRight = context.screenX + ((placement.x + width) * context.scale).roundToInt()
            val screenBottom = context.screenY + ((placement.y + height) * context.scale).roundToInt()

            BookElementRenderers.render(
                placement.element.content.type,
                BookElementRenderContext(
                    graphics = context.graphics,
                    x = context.x + placement.x,
                    y = context.y + placement.y,
                    width = width,
                    height = height,
                    mouseX = context.mouseX,
                    mouseY = context.mouseY,
                    partialTick = context.partialTick,
                    screenX = screenX,
                    screenY = screenY,
                    screenWidth = (screenRight - screenX).coerceAtLeast(1),
                    screenHeight = (screenBottom - screenY).coerceAtLeast(1),
                    scale = context.scale,
                    interactionKey = context.interactionKey?.let { "$it|group_$index" },
                    research = context.research,
                    scissorArea = context.scissorArea
                ),
                placement.element.content
            )
        }
    }
}
