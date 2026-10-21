package com.algorithmlx.ecr.client.widget.mim

import com.algorithmlx.ecr.api.client.MRULineAnimation
import com.algorithmlx.ecr.api.client.drawMRUGradientLine
import com.algorithmlx.ecr.api.mim.MIMCraftingState
import com.algorithmlx.ecr.api.mim.MIMDependencyStatus
import com.algorithmlx.ecr.api.mru.storage.MRUStorage
import com.algorithmlx.ecr.client.utils.MIMGuiTextures
import com.algorithmlx.ecr.client.utils.MIMItemCount
import com.algorithmlx.ecr.client.widget.mim.MIMDependencyLayout as Layout
import com.algorithmlx.ecr.client.widget.mim.MIMDependencyTextures as Textures
import com.algorithmlx.ecr.common.menu.MIMDependencyEntry
import com.algorithmlx.ecr.common.menu.MIMDependencyResponse
import com.algorithmlx.ecr.common.menu.MIMMenu
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

class MIMDependencyWindow(
    private val font: Font, private val onCraft: (Int, ItemStack, Int) -> Unit, onStop: () -> Unit
) : AbstractContainerEventHandler(), MIMFloatingWindow {
    private val geometry = MIMWindowFrame(WIDTH, HEIGHT, Layout.TITLE_HEIGHT, ::layout)
    private val viewport = MIMDependencyViewport()
    val horizontalOffset: Double get() = viewport.horizontal
    val verticalOffset: Double get() = viewport.vertical
    private var panning = false
    private var displayScale = Layout.DISPLAY_SCALE
    override val x: Int get() = geometry.x
    override val y: Int get() = geometry.y
    override val windowWidth: Int get() = (WIDTH * displayScale).roundToInt()
    override val windowHeight: Int get() = (HEIGHT * displayScale).roundToInt()
    override var visible = false
        private set
    private var isOpen = false
    private var available = false
    private var result: MIMDependencyResponse? = null
    val preview: MIMDependencyResponse? get() = result
    private var selected: Selection? = null
    val selection: MIMCraftingSelection? get() = selected?.let { target ->
        quantity.value.toIntOrNull()?.takeIf { it > 0 }?.let { MIMCraftingSelection(target.target, it) }
    }
    override val isEditing: Boolean get() = visible && focused === quantity
    override fun capturesInput(): Boolean = isEditing
    private var completed = 0
    private var ticks = 0
    private var queued = 0
    private var state: MIMCraftingState? = null
    private var coreMRU = 0
    private var coreCapacity = 0
    private val close = MIMDependencyButton(
        font, Component.translatable("screen.escr.mim.dependency.close"),
        Textures.CLOSE, Textures.CLOSE_PRESSED, Textures.CLOSE_HOVERED, false
    ) { close() }
    private val smaller = MIMDependencyButton(
        font, Component.literal("-"), Textures.BUTTON, Textures.BUTTON_PRESSED, Textures.BUTTON_HOVERED
    ) { zoom(-Layout.ZOOM_STEP) }
    private val larger = MIMDependencyButton(
        font, Component.literal("+"), Textures.BUTTON, Textures.BUTTON_PRESSED, Textures.BUTTON_HOVERED
    ) { zoom(Layout.ZOOM_STEP) }
    private val decrease = MIMDependencyButton(
        font, Component.literal("-"), Textures.BUTTON, Textures.BUTTON_PRESSED, Textures.BUTTON_HOVERED
    ) { changeQuantity(-1) }
    private val increase = MIMDependencyButton(
        font, Component.literal("+"), Textures.BUTTON, Textures.BUTTON_PRESSED, Textures.BUTTON_HOVERED
    ) { changeQuantity(1) }
    private val craft = MIMDependencyButton(
        font, Component.translatable("screen.escr.mim.craft"), Textures.CRAFT, Textures.CRAFT_PRESSED, Textures.CRAFT_HOVERED
    ) {
        selected?.let { selected -> selection?.let { onCraft(selected.slot, it.target, it.batches) } }
    }
    private val stop = MIMDependencyButton(
        font, Component.translatable("screen.escr.mim.stop"), Textures.STOP, Textures.STOP_PRESSED,
        Textures.STOP_HOVERED, action = onStop
    )
    private val scrollbar = MIMDependencyScrollBar(viewport::selectVertical)
    private val quantity = MIMQuantityBox(font) { result = null; refreshWidgets() }
    private val buttons = listOf(close, smaller, larger, decrease, increase, craft, stop)
    private val widgets: List<AbstractWidget> = buttons + scrollbar + quantity
    private val mruAnimation = MRULineAnimation()
    private val power = object : MRUStorage {
        override val mru: Int get() = coreMRU
        override val mruCapacity: Int get() = coreCapacity
        override val mruType get() = MRUTypeRegistry.radiationUnit
    }

    init {
        close.setTooltip(Tooltip.create(Component.translatable("screen.escr.mim.dependency.close")))
        smaller.setTooltip(Tooltip.create(Component.translatable("screen.escr.mim.dependency.zoom_out")))
        larger.setTooltip(Tooltip.create(Component.translatable("screen.escr.mim.dependency.zoom_in")))
        craft.setTooltip(Tooltip.create(Component.translatable("screen.escr.mim.craft_recipe")))
        stop.setTooltip(Tooltip.create(Component.translatable("screen.escr.mim.cancel_jobs")))
        decrease.setTooltip(Tooltip.create(Component.translatable("screen.escr.mim.batches.decrease")))
        increase.setTooltip(Tooltip.create(Component.translatable("screen.escr.mim.batches.increase")))
        quantity.setTooltip(Tooltip.create(Component.translatable("screen.escr.mim.batches.tooltip")))
    }

    fun loading(slot: Int, target: ItemStack, batches: Int) {
        result = null
        selected = Selection(slot, target.copyWithCount(1))
        quantity.value = batches.toString()
        viewport.reset()
        isOpen = true
        refreshWidgets()
    }

    fun accept(response: MIMDependencyResponse) {
        result = response
        viewport.update(response.nodes.map { it.parent })
        refreshWidgets()
    }

    override fun update(menu: MIMMenu) {
        available = menu.isStorageAvailable
        completed = menu.craftCompleted
        ticks = menu.craftTicks
        queued = menu.queuedCrafts
        state = menu.craftingState
        coreMRU = menu.coreMRU
        coreCapacity = menu.coreMRUCapacity
        refreshWidgets()
        if (!visible) releasePointer()
    }

    override fun close() {
        isOpen = false
        setFocused(null)
        releasePointer()
        refreshWidgets()
    }

    override fun fitViewport(width: Int, height: Int, initialX: Int, initialY: Int) {
        displayScale = minOf(Layout.DISPLAY_SCALE, width.coerceAtLeast(1).toFloat() / WIDTH, height.coerceAtLeast(1).toFloat() / HEIGHT)
        geometry.fitViewport(width, height, initialX, initialY, displayScale)
    }

    private fun place(widget: AbstractWidget, left: Int, top: Int, width: Int, height: Int) {
        widget.x = x + (left * displayScale).roundToInt()
        widget.y = y + (top * displayScale).roundToInt()
        widget.width = (width * displayScale).roundToInt().coerceAtLeast(1)
        widget.height = (height * displayScale).roundToInt().coerceAtLeast(1)
    }

    private fun layout() {
        place(close, Layout.CLOSE_X, Layout.CLOSE_Y, Textures.CLOSE.width, Textures.CLOSE.height)
        place(smaller, viewport.plusX - Layout.ZOOM_GAP, Layout.ZOOM_Y, Layout.BUTTON_SIZE, Layout.BUTTON_SIZE)
        place(larger, viewport.plusX, Layout.ZOOM_Y, Layout.BUTTON_SIZE, Layout.BUTTON_SIZE)
        place(craft, Layout.CRAFT_X, Layout.ACTION_Y, Textures.CRAFT.width, Textures.CRAFT.height)
        place(stop, Layout.STOP_X, Layout.ACTION_Y, Textures.STOP.width, Textures.STOP.height)
        place(scrollbar, Layout.SCROLL_X, Layout.BODY_Y, Textures.TRACK.width, Layout.BODY_HEIGHT)
        place(decrease, Layout.DECREASE_X, Layout.CLOSE_Y, Layout.BUTTON_SIZE, Layout.BUTTON_SIZE)
        place(quantity, Layout.QUANTITY_X, Layout.CLOSE_Y, Textures.QUANTITY.width, Textures.QUANTITY.height)
        place(increase, Layout.INCREASE_X, Layout.CLOSE_Y, Layout.BUTTON_SIZE, Layout.BUTTON_SIZE)
        buttons.forEach { it.textScale = displayScale }
        scrollbar.update(viewport.vertical, viewport.maxVertical, Layout.CONTENT_HEIGHT.toDouble(), viewport.step, displayScale)
    }

    private fun zoom(delta: Float) {
        viewport.zoom(delta)
        refreshWidgets()
    }

    private fun changeQuantity(direction: Int) {
        val client = Minecraft.getInstance()
        val step = when {
            client.hasControlDown() && client.hasShiftDown() -> COMBINED_QUANTITY_STEP
            client.hasShiftDown() -> SHIFT_QUANTITY_STEP
            client.hasControlDown() -> CONTROL_QUANTITY_STEP
            else -> 1
        }
        val current = quantity.value.toLongOrNull() ?: 0
        quantity.value = (current + direction.toLong() * step).coerceIn(1, Int.MAX_VALUE.toLong()).toString()
    }

    private fun refreshWidgets() {
        visible = available && isOpen
        widgets.forEach { it.visible = visible }
        smaller.active = viewport.zoom > 0.5f
        larger.active = viewport.zoom < 1.5f
        quantity.active = available && selected != null
        decrease.active = quantity.active
        increase.active = quantity.active
        craft.active = available && selection != null && result?.complete == true
        stop.active = available && queued > 0
        scrollbar.visible = visible && viewport.hasScrollbar
        layout()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        if (!visible) return
        graphics.nextStratum()
        local(graphics) {
            Textures.PANEL.draw(graphics, 0, 0)
            graphics.text(font, font.plainSubstrByWidth(Component.translatable("screen.escr.mim.dependency.title").string, Layout.TITLE_WIDTH), Layout.TITLE_X, 11, MIMGuiTextures.TEXT, false)
            Textures.SLOT.draw(graphics, Layout.RESULT_X, Layout.RESULT_Y)
            val target = selected?.target
            if (target != null) graphics.fakeItem(target, Layout.RESULT_X + 2, Layout.RESULT_Y + 2)
            val name = target?.hoverName?.string ?: Component.translatable("screen.escr.mim.recipe").string
            graphics.text(font, font.plainSubstrByWidth(name, 201), Layout.RESULT_TEXT_X, Layout.RESULT_Y + 1, MIMGuiTextures.TEXT, false)
            val summary = result?.let { Component.translatable(if (it.complete) "screen.escr.mim.dependency.ready" else "screen.escr.mim.dependency.missing") }
                ?: Component.translatable("screen.escr.mim.dependency.loading")
            graphics.text(font, font.plainSubstrByWidth(Component.translatable("screen.escr.mim.dependency.summary", summary, selection?.batches ?: 0).string, 201),
                Layout.RESULT_TEXT_X, Layout.RESULT_Y + 12, MIMGuiTextures.TEXT, false)
            val text = Component.translatable("screen.escr.mim.dependency.footer", result?.totalMRU ?: 0, workStatus()).string
            graphics.text(font, font.plainSubstrByWidth(text, 234), 11, 237, MIMGuiTextures.TEXT, false)
            drawMRUGradientLine(graphics, power, 8, 224, 0, 0, 8, 124, animation = mruAnimation, deltaTicks = deltaTicks)
        }
        drawNodes(graphics)
        graphics.nextStratum()
        widgets.forEach { it.extractRenderState(graphics, mouseX, mouseY, deltaTicks) }
    }

    private fun drawNodes(graphics: GuiGraphicsExtractor) {
        val data = result ?: return
        graphics.enableScissor(
            x + (Layout.CONTENT_X * displayScale).roundToInt(), y + (Layout.CONTENT_Y * displayScale).roundToInt(),
            x + ((Layout.CONTENT_X + viewport.width) * displayScale).roundToInt(),
            y + ((Layout.CONTENT_Y + Layout.CONTENT_HEIGHT) * displayScale).roundToInt()
        )
        local(graphics) {
            graphics.pose().translate((Layout.CONTENT_X + viewport.horizontalPadding - viewport.horizontal).toFloat(),
                (Layout.CONTENT_Y - viewport.vertical).toFloat())
            graphics.pose().scale(viewport.zoom, viewport.zoom)
            data.nodes.forEachIndexed { index, node -> drawConnection(graphics, node, index) }
            graphics.nextStratum()
            viewport.visibleNodes().forEach { index -> drawNode(graphics, data.nodes[index], index) }
        }
        graphics.disableScissor()
    }

    private fun drawConnection(graphics: GuiGraphicsExtractor, node: MIMDependencyEntry, index: Int) {
        if (node.parent < 0) return
        val parent = viewport.graph.nodes[node.parent]
        val child = viewport.graph.nodes[index]
        val fromX = parent.x + Layout.NODE_WIDTH / 2
        val fromY = parent.y + Layout.NODE_HEIGHT
        val toX = child.x + Layout.NODE_WIDTH / 2
        val middle = (fromY + child.y) / 2
        graphics.fill(fromX, fromY, fromX + 1, middle + 1, CONNECTION_COLOR)
        graphics.fill(minOf(fromX, toX), middle, maxOf(fromX, toX) + 1, middle + 1, CONNECTION_COLOR)
        graphics.fill(toX, middle, toX + 1, child.y, CONNECTION_COLOR)
    }

    private fun drawNode(graphics: GuiGraphicsExtractor, node: MIMDependencyEntry, index: Int) {
        val position = viewport.graph.nodes[index]
        Textures.CARD.draw(graphics, position.x, position.y)
        graphics.fakeItem(node.stack, position.x + 4, position.y + 8)
        graphics.text(font, font.plainSubstrByWidth(node.stack.hoverName.string, 66), position.x + 24, position.y + 5, MIMGuiTextures.TEXT, false)
        Textures.status(node.status).draw(graphics, position.x + 81, position.y + 19)
        graphics.text(font, Component.literal("×${MIMItemCount.format(node.amount).text}"), position.x + 24, position.y + 18, MIMGuiTextures.TEXT, false)
    }

    private inline fun local(graphics: GuiGraphicsExtractor, draw: () -> Unit) {
        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(displayScale, displayScale)
        draw()
        graphics.pose().popMatrix()
    }

    override fun extractTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean {
        if (!isMouseOver(mouseX.toDouble(), mouseY.toDouble())) return false
        val localX = (mouseX - x) / displayScale.toDouble()
        val localY = (mouseY - y) / displayScale.toDouble()
        val lines = when {
            localX >= 8 && localX < 132 && localY >= 224 && localY < 232 -> listOf(Component.translatable("screen.escr.mim.dependency.core_mru", coreMRU, coreCapacity))
            localY >= 233 -> listOf(Component.translatable("screen.escr.mim.dependency.plan_mru", result?.totalMRU ?: 0), workStatus())
            localX >= Layout.RESULT_X && localX < Layout.RESULT_X + Textures.SLOT.width &&
                localY >= Layout.RESULT_Y && localY < Layout.RESULT_Y + Textures.SLOT.height -> listOfNotNull(selected?.target?.hoverName)
            else -> viewport.nodeAt(localX, localY)?.let { index ->
                val node = result!!.nodes[index]
                listOf(node.stack.hoverName, Component.translatable("screen.escr.mim.dependency.amounts", node.amount, node.stored, node.crafts), status(node.status))
            }.orEmpty()
        }
        if (lines.isEmpty()) return false
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY)
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false
        val localY = (mouseY - y) / displayScale
        if (localY < Layout.BODY_Y || localY >= Layout.BODY_Y + Layout.BODY_HEIGHT) return true
        val client = Minecraft.getInstance()
        when {
            client.hasControlDown() -> viewport.zoom((if (vertical != 0.0) vertical else horizontal).toFloat() * Layout.ZOOM_STEP)
            client.hasShiftDown() -> viewport.scroll(-(vertical + horizontal) * Layout.PAN_STEP, 0.0)
            else -> viewport.scroll(-horizontal * Layout.PAN_STEP, -vertical * viewport.step)
        }
        refreshWidgets()
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!isMouseOver(event.x(), event.y())) return false
        if (super<AbstractContainerEventHandler>.mouseClicked(event, doubleClick)) return true
        setFocused(null)
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT &&
            viewport.contains((event.x() - x) / displayScale, (event.y() - y) / displayScale))
            panning = true
        else geometry.begin(event)
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (!visible) return false
        if (panning && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            viewport.scroll(-dragX / displayScale, -dragY / displayScale)
            layout()
            return true
        }
        return geometry.drag(event) || super<AbstractContainerEventHandler>.mouseDragged(event, dragX, dragY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        geometry.release()
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) panning = false
        buttons.forEach { it.mouseReleased(event) }
        scrollbar.mouseReleased(event)
        return super<AbstractContainerEventHandler>.mouseReleased(event)
    }

    override fun releasePointer() {
        geometry.release()
        panning = false
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
        output.add(NarratedElementType.TITLE, Component.translatable("screen.escr.mim.dependency.title"))
    }

    private fun workStatus(): Component = when (state) {
        MIMCraftingState.POWER -> Component.translatable("screen.escr.mim.work.power")
        MIMCraftingState.MATERIALS -> Component.translatable("screen.escr.mim.work.materials")
        MIMCraftingState.SPACE -> Component.translatable("screen.escr.mim.work.space")
        MIMCraftingState.WORKING -> Component.translatable("screen.escr.mim.work.crafting", completed, ticks)
        MIMCraftingState.PLANNING -> Component.translatable("screen.escr.mim.work.planning")
        else -> Component.translatable("screen.escr.mim.work.queued", queued)
    }

    private class Selection(val slot: Int, val target: ItemStack)

    companion object {
        const val WIDTH = Layout.WIDTH
        const val HEIGHT = Layout.HEIGHT
        private const val CONTROL_QUANTITY_STEP = 5
        private const val SHIFT_QUANTITY_STEP = 50
        private const val COMBINED_QUANTITY_STEP = 100
        private const val CONNECTION_COLOR = 0xFF777777.toInt()

        private fun status(value: MIMDependencyStatus): Component = when (value) {
            MIMDependencyStatus.STORED -> Component.translatable("screen.escr.mim.dependency.status.stored")
            MIMDependencyStatus.CRAFTABLE -> Component.translatable("screen.escr.mim.dependency.status.craftable")
            MIMDependencyStatus.MISSING -> Component.translatable("screen.escr.mim.dependency.status.missing")
            MIMDependencyStatus.CYCLE -> Component.translatable("screen.escr.mim.dependency.status.cycle")
            MIMDependencyStatus.LIMIT -> Component.translatable("screen.escr.mim.dependency.status.limit")
        }
    }
}
