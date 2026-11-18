package com.algorithmlx.ecr.api.client.research

import com.algorithmlx.ecr.api.research.content.BookElement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import java.util.concurrent.ConcurrentHashMap

data class BookElementRenderContext(
    val graphics: GuiGraphicsExtractor,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val mouseX: Int,
    val mouseY: Int,
    val partialTick: Float,
    val screenX: Int = x,
    val screenY: Int = y,
    val screenWidth: Int = width,
    val screenHeight: Int = height,
    val scale: Float = 1f,
    val textLines: List<FormattedCharSequence>? = null,
    val interactionKey: String? = null,
    val research: Identifier? = null,
    val textLineStart: Int = 0,
    val textLineCount: Int = textLines?.size ?: 0,
    val scissorArea: ScreenRectangle? = null
) {
    val mc: Minecraft = Minecraft.getInstance()
}

fun interface BookElementRenderer<T : BookElement> {
    fun render(context: BookElementRenderContext, element: T)
}

object BookElementRenderers {
    private val renderers = ConcurrentHashMap<Identifier, BookElementRenderer<out BookElement>>()

    @JvmStatic
    fun <T : BookElement> register(type: Identifier, renderer: BookElementRenderer<T>) {
        check(renderers.putIfAbsent(type, renderer) == null) { "Duplicate book element renderer: $type" }
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun render(type: Identifier, context: BookElementRenderContext, element: BookElement): Boolean {
        val renderer = renderers[type] as? BookElementRenderer<BookElement> ?: return false
        renderer.render(context, element)
        return true
    }
}
