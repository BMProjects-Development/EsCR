package com.algorithmlx.ecr.client.widget.mim

import com.algorithmlx.ecr.api.mim.MIMCraftingState
import com.algorithmlx.ecr.api.client.MRULineAnimation
import com.algorithmlx.ecr.api.client.drawMRUGradientLine
import com.algorithmlx.ecr.api.mru.storage.MRUStorage
import com.algorithmlx.ecr.api.network.sendToServer
import com.algorithmlx.ecr.client.utils.MIMGuiTextures
import com.algorithmlx.ecr.common.menu.MIMMenu
import com.algorithmlx.ecr.common.menu.MIMTaskCancel
import com.algorithmlx.ecr.common.menu.MIMTaskEntry
import com.algorithmlx.ecr.common.menu.MIMTaskRequest
import com.algorithmlx.ecr.common.menu.MIMTaskResponse
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import kotlin.math.roundToInt
import com.algorithmlx.ecr.client.widget.mim.MIMDependencyLayout as Layout
import com.algorithmlx.ecr.client.widget.mim.MIMDependencyTextures as Textures

class MIMTaskWindow(private val font: Font, private val menu: MIMMenu, onStopAll: () -> Unit) : AbstractContainerEventHandler(), MIMFloatingWindow {
    private val geometry = MIMWindowFrame(Layout.WIDTH, Layout.HEIGHT, Layout.TITLE_HEIGHT, ::layout)
    private var scale = 1f
    private var open = false
    private var available = false
    private var nextRefresh = 0L
    private var requestId = 0
    private var acceptedId = 0
    private var offset = 0
    var total = 0
        private set
    var entries = emptyList<MIMTaskEntry>()
        private set
    override val x: Int get() = geometry.x
    override val y: Int get() = geometry.y
    override val windowWidth: Int get() = (Layout.WIDTH * scale).roundToInt()
    override val windowHeight: Int get() = (Layout.HEIGHT * scale).roundToInt()
    override var visible = false
        private set
    private val close = MIMDependencyButton(
        font, Component.translatable("screen.escr.mim.tasks.close"),
        Textures.CLOSE, Textures.CLOSE_PRESSED, Textures.CLOSE_HOVERED, false
    ) { close() }
    private val stop = MIMDependencyButton(
        font, Component.translatable("screen.escr.mim.tasks.stop_all"), Textures.CRAFT, Textures.CRAFT_PRESSED,
        Textures.CRAFT_HOVERED
    ) {
        onStopAll()
        nextRefresh = 0
    }
    private val cancel = List(MIMTaskResponse.PAGE_SIZE) { row ->
        MIMDependencyButton(
            font, Component.translatable("screen.escr.mim.tasks.cancel"),
            Textures.CLOSE, Textures.CLOSE_PRESSED, Textures.CLOSE_HOVERED, false
        ) {
            entries.getOrNull(row)?.let { task ->
                task.id?.let { MIMTaskCancel(menu.containerId, task.module, it).sendToServer() }
            }
            nextRefresh = 0
        }.also { it.setTooltip(Tooltip.create(Component.translatable("screen.escr.mim.tasks.cancel"))) }
    }
    private val scrollbar = MIMDependencyScrollBar({ select(it.roundToInt()) }, Component.translatable("screen.escr.mim.tasks.scroll"))
    private val buttons = listOf(close, stop) + cancel
    private val widgets: List<AbstractWidget> = buttons + scrollbar
    private val maximum: Int get() = (total - MIMTaskResponse.PAGE_SIZE).coerceAtLeast(0)
    private val mruAnimation = MRULineAnimation()
    private val power = object : MRUStorage {
        override val mru: Int get() = menu.coreMRU
        override val mruCapacity: Int get() = menu.coreMRUCapacity
        override val mruType get() = MRUTypeRegistry.radiationUnit
    }

    init {
        close.setTooltip(Tooltip.create(close.message))
        stop.setTooltip(Tooltip.create(stop.message))
    }

    fun toggle() {
        if (open) close()
        else {
            open = true
            nextRefresh = 0
            update(menu)
        }
    }

    override fun update(menu: MIMMenu) {
        val wasVisible = visible
        available = menu.isConnected
        visible = open && available
        if (visible && !wasVisible) nextRefresh = 0
        if (visible && Util.getMillis() >= nextRefresh) {
            nextRefresh = Util.getMillis() + REFRESH_MS
            MIMTaskRequest(menu.containerId, ++requestId, offset).sendToServer()
        }
        refreshWidgets()
        if (!visible) releasePointer()
    }

    fun accept(response: MIMTaskResponse) {
        if (response.containerId != menu.containerId || response.requestId <= acceptedId || response.requestId != requestId) return
        acceptedId = response.requestId
        entries = response.entries
        offset = response.offset
        total = response.total
        refreshWidgets()
    }

    private fun select(row: Int) {
        val next = row.coerceIn(0, maximum)
        if (next == offset) return
        offset = next
        entries = emptyList()
        nextRefresh = 0
        refreshWidgets()
    }

    override fun close() {
        open = false
        visible = false
        setFocused(null)
        releasePointer()
        refreshWidgets()
    }

    override fun fitViewport(width: Int, height: Int, initialX: Int, initialY: Int) {
        scale = minOf(1f, width.coerceAtLeast(1).toFloat() / Layout.WIDTH, height.coerceAtLeast(1).toFloat() / Layout.HEIGHT)
        geometry.fitViewport(width, height, initialX, initialY, scale)
    }

    private fun place(widget: AbstractWidget, left: Int, top: Int, width: Int, height: Int) {
        widget.setRectangle((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1),
            x + (left * scale).roundToInt(), y + (top * scale).roundToInt())
    }

    private fun layout() {
        place(close, Layout.CLOSE_X, Layout.CLOSE_Y, 17, 17)
        place(stop, Layout.CRAFT_X, Layout.ACTION_Y, 64, 16)
        cancel.forEachIndexed { index, button -> place(button, CANCEL_X, Layout.CONTENT_Y + index * ROW_HEIGHT + 7, 17, 17) }
        place(scrollbar, Layout.SCROLL_X, Layout.BODY_Y, 13, Layout.BODY_HEIGHT)
        buttons.forEach { it.textScale = scale }
        scrollbar.update(offset.toDouble(), maximum.toDouble(), MIMTaskResponse.PAGE_SIZE.toDouble(), 1.0, scale)
    }

    private fun refreshWidgets() {
        close.visible = visible
        stop.visible = visible
        stop.active = available && total > 0
        cancel.forEachIndexed { index, button ->
            button.visible = visible && entries.getOrNull(index) != null
            button.active = entries.getOrNull(index)?.id != null
        }
        scrollbar.visible = visible && maximum > 0
        layout()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        if (!visible) return
        graphics.nextStratum()
        local(graphics) {
            Textures.PANEL.draw(graphics, 0, 0)
            graphics.text(font, Component.translatable("screen.escr.mim.tasks"), 11, 11, MIMGuiTextures.TEXT, false)
            graphics.text(font, Component.translatable("screen.escr.mim.work.queued", menu.queuedCrafts), 13, 40, MIMGuiTextures.TEXT, false)
            if (entries.isEmpty()) graphics.text(font, Component.translatable(
                if (total == 0) "screen.escr.mim.tasks.empty" else "screen.escr.mim.dependency.loading"
            ), 13, Layout.CONTENT_Y + 6, MIMGuiTextures.TEXT, false)
            entries.forEachIndexed { index, task ->
                val top = Layout.CONTENT_Y + index * ROW_HEIGHT
                Textures.CARD.frame(graphics, 13, top, CARD_WIDTH)
                if (!task.target.isEmpty) graphics.fakeItem(task.target, 17, top + 8)
                val name = if (task.target.isEmpty) Component.translatable("screen.escr.mim.tasks.module") else task.target.hoverName
                graphics.text(font, font.plainSubstrByWidth(name.string, 162), 37, top + 5, MIMGuiTextures.TEXT, false)
                val details = Component.translatable("screen.escr.mim.tasks.details", task.remaining, status(task)).string
                graphics.text(font, font.plainSubstrByWidth(details, 162), 37, top + 18, MIMGuiTextures.TEXT, false)
            }
            graphics.text(font, Component.translatable("screen.escr.mim.tasks.page", if (total == 0) 0 else offset + 1,
                offset + entries.size, total), 11, 239, MIMGuiTextures.TEXT, false)
            drawMRUGradientLine(graphics, power, 8, 224, 0, 0, 8, 124, animation = mruAnimation, deltaTicks = deltaTicks)
        }
        graphics.nextStratum()
        widgets.forEach { it.extractRenderState(graphics, mouseX, mouseY, deltaTicks) }
    }

    override fun extractTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean {
        if (!isMouseOver(mouseX.toDouble(), mouseY.toDouble())) return false
        val localX = (mouseX - x) / scale
        val localY = (mouseY - y) / scale
        if (localX !in 13f..CANCEL_X.toFloat() || localY < Layout.CONTENT_Y) return false
        val index = ((localY - Layout.CONTENT_Y) / ROW_HEIGHT).toInt()
        val task = entries.getOrNull(index) ?: return false
        val position = Component.translatable("tooltip.escr.bound_gem.coordinates", task.module.x, task.module.y, task.module.z)
        graphics.setComponentTooltipForNextFrame(font, listOf(
            if (task.target.isEmpty) Component.translatable("screen.escr.mim.tasks.module") else task.target.hoverName,
            Component.translatable("screen.escr.mim.tasks.details", task.remaining, status(task)), position
        ), mouseX, mouseY)
        return true
    }

    private inline fun local(graphics: GuiGraphicsExtractor, draw: () -> Unit) {
        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(scale, scale)
        draw()
        graphics.pose().popMatrix()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false
        select(offset - vertical.roundToInt())
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!isMouseOver(event.x(), event.y())) return false
        if (!super<AbstractContainerEventHandler>.mouseClicked(event, doubleClick)) {
            setFocused(null)
            geometry.begin(event)
        }
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean =
        visible && (geometry.drag(event) || super<AbstractContainerEventHandler>.mouseDragged(event, dragX, dragY))

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        geometry.release()
        buttons.forEach { it.mouseReleased(event) }
        scrollbar.mouseReleased(event)
        return super<AbstractContainerEventHandler>.mouseReleased(event)
    }

    override fun releasePointer() {
        geometry.release()
        buttons.forEach { it.releasePointer() }
        scrollbar.releasePointer()
        setDragging(false)
    }

    override fun children(): List<AbstractWidget> = widgets
    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean = visible && geometry.contains(mouseX, mouseY)
    override fun getRectangle(): ScreenRectangle = ScreenRectangle(x, y, windowWidth, windowHeight)
    override fun isActive(): Boolean = visible
    override fun narrationPriority(): NarratableEntry.NarrationPriority =
        if (visible && isFocused) NarratableEntry.NarrationPriority.FOCUSED else NarratableEntry.NarrationPriority.NONE
    override fun getNarratables(): Collection<NarratableEntry> = widgets.filter { it.visible }
    override fun updateNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, Component.translatable("screen.escr.mim.tasks"))
    }

    private fun status(task: MIMTaskEntry): Component = when (task.activity.state) {
        MIMCraftingState.POWER -> Component.translatable("screen.escr.mim.work.power")
        MIMCraftingState.MATERIALS -> Component.translatable("screen.escr.mim.work.materials")
        MIMCraftingState.SPACE -> Component.translatable("screen.escr.mim.work.space")
        MIMCraftingState.PLANNING -> Component.translatable("screen.escr.mim.work.planning")
        MIMCraftingState.WORKING -> Component.translatable("screen.escr.mim.work.crafting", task.activity.completed, task.activity.ticks)
        else -> Component.translatable("screen.escr.mim.tasks.queued")
    }

    companion object {
        private const val REFRESH_MS = 500L
        private const val ROW_HEIGHT = 36
        private const val CANCEL_X = 208
        private const val CARD_WIDTH = 217
    }
}
