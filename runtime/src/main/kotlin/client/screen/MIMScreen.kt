package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.api.network.sendToServer
import com.algorithmlx.ecr.api.client.MRULineAnimation
import com.algorithmlx.ecr.api.client.drawMRULine
import com.algorithmlx.ecr.api.mru.storage.MRUStorage
import com.algorithmlx.ecr.client.utils.MIMGuiTextures
import com.algorithmlx.ecr.client.utils.MIMItemCount
import com.algorithmlx.ecr.client.widget.mim.MIMTaskWindow
import com.algorithmlx.ecr.client.widget.mim.MIMDependencyWindow
import com.algorithmlx.ecr.client.widget.mim.MIMCraftingPreview
import com.algorithmlx.ecr.client.widget.mim.MIMFloatingWindow
import com.algorithmlx.ecr.client.widget.mim.MIMWindowHost
import com.algorithmlx.ecr.common.menu.MIMDependencyResponse
import com.algorithmlx.ecr.common.menu.MIMTaskResponse
import com.algorithmlx.ecr.client.widget.mim.MIMItemButton
import com.algorithmlx.ecr.client.widget.mim.MIMScrollBar
import com.algorithmlx.ecr.client.widget.mim.MIMSearchBox
import com.algorithmlx.ecr.common.menu.MIMMenu
import com.algorithmlx.ecr.common.menu.MIMCraftPayload
import com.algorithmlx.ecr.common.menu.MIMCoreClickPayload
import com.algorithmlx.ecr.common.menu.MIMSearchPayload
import com.algorithmlx.ecr.common.menu.MIMTerminalLayout
import com.algorithmlx.ecr.common.menu.MIMViewportPayload
import com.algorithmlx.ecr.mixin.client.ContainerScreenAccessor
import com.algorithmlx.ecr.registry.ItemRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.util.Util
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.Slot
import net.minecraft.world.inventory.ContainerInput

@Suppress("CAST_NEVER_SUCCEEDS")
class MIMScreen(menu: MIMMenu, inventory: Inventory, title: Component) : AbstractContainerScreen<MIMMenu>(
    menu, inventory, title, menu.screenWidth, menu.screenHeight
), MIMWindowHost {
    override val floatingWindowBounds: List<ScreenRectangle> get() = floatingWindows.filter { it.visible }.map { it.rectangle }
    override val overlayExclusionBounds: List<ScreenRectangle> get() = floatingWindowBounds +
        listOfNotNull(craftingButton, modeButton).filter { it.visible }.map { it.rectangle }
    override val hasOverlayPointerCapture: Boolean get() = windowClickButtons.isNotEmpty() || itemButtonClicks.isNotEmpty()
    override val isFloatingTextInput: Boolean get() = (focused as? MIMFloatingWindow)?.isEditing == true
    override val isFloatingInputFocused: Boolean get() = (focused as? MIMFloatingWindow)?.visible == true
    override fun releaseFloatingFocus() {
        if (focused is MIMFloatingWindow) setFocused(null)
    }
    private var searchText = ""
    private var pendingSearchAt = 0L
    private var search: MIMSearchBox? = null
    private var taskWindow: MIMTaskWindow? = null
    private var dependencyWindow: MIMDependencyWindow? = null
    private val floatingWindows = mutableListOf<MIMFloatingWindow>()
    private var craftingPreview: MIMCraftingPreview? = null
    private var craftingButton: MIMItemButton? = null
    private var modeButton: MIMItemButton? = null
    private var scrollBar: MIMScrollBar? = null
    private var draggingScrollBar = false
    private var pendingScrollRow: Int? = null
    private var warningSince: Long? = null
    private val virtualClickButtons = mutableSetOf<Int>()
    private val itemButtonClicks = mutableMapOf<Int, MIMItemButton>()
    private val windowClickButtons = mutableMapOf<Int, MIMFloatingWindow>()
    private val mruAnimation = MRULineAnimation()
    private val mruView = object : MRUStorage {
        override val mru: Int get() = menu.mru
        override val mruCapacity: Int get() = menu.mruCapacity
        override val mruType get() = MRUTypeRegistry.radiationUnit
    }

    override fun init() {
        if (menu.isTerminal) (this as ContainerScreenAccessor).setImageHeight(menu.screenHeight)
        super.init()
        menu.updateSlotPositions()
        virtualClickButtons.clear()
        itemButtonClicks.clear()
        windowClickButtons.clear()
        draggingScrollBar = false
        if (!menu.isTerminal) {
            scrollBar = menu.configurationLayout?.scrollBar?.let { bounds ->
                addRenderableWidget(MIMScrollBar(
                    leftPos + bounds.x, topPos + bounds.y, bounds.height, ::requestScroll,
                    MIMGuiTextures.scrollThumb(menu.configurationLayout!!, bounds)
                ))
            }
            updateLayout()
            return
        }
        search = addRenderableWidget(MIMSearchBox(font)).also { field ->
            field.value = searchText
            field.setResponder { value -> searchText = value; pendingSearchAt = Util.getMillis() + 150 }
        }
        dependencyWindow = dependencyWindow ?: MIMDependencyWindow(font, { _, expected, batches ->
            MIMCraftPayload(menu.containerId, expected, batches).sendToServer()
        }, { minecraft.gameMode?.handleInventoryButtonClick(menu.containerId, MIMMenu.CANCEL_CRAFTING) })
        val window = taskWindow ?: MIMTaskWindow(font, menu) {
            minecraft.gameMode?.handleInventoryButtonClick(menu.containerId, MIMMenu.CANCEL_CRAFTING)
        }
        taskWindow = window
        craftingPreview = craftingPreview ?: MIMCraftingPreview(menu, dependencyWindow!!)
        registerWindow(window)
        registerWindow(dependencyWindow!!)
        floatingWindows.forEach { registerWindow(it); it.releasePointer() }
        craftingButton = addWidget(
            MIMItemButton(
                ItemStack(ItemRegistry.hammer.get()),
                Component.translatable("screen.escr.mim.tasks.open")
            ) {
                window.toggle()
                if (window.visible) bringToFront(window)
            })
        modeButton = addWidget(MIMItemButton(ItemStack(Items.CHEST), Component.translatable("screen.escr.mim.mode.storage")) {
            minecraft.gameMode?.handleInventoryButtonClick(menu.containerId, MIMMenu.TOGGLE_MODE)
        })
        scrollBar = addRenderableWidget(
            MIMScrollBar(
                leftPos + MIMTerminalLayout.SCROLL_X, topPos + MIMTerminalLayout.GRID_Y,
                MIMTerminalLayout.scrollHeight(menu.visibleRows), ::requestScroll
            )
        )
        updateLayout()
        MIMViewportPayload(menu.containerId, MIMTerminalLayout.rowsForHeight(height), menu.scrollRow).sendToServer()
    }

    override fun containerTick() {
        super.containerTick()
        updateLayout()
        if (pendingSearchAt != 0L && Util.getMillis() >= pendingSearchAt) {
            pendingSearchAt = 0
            MIMSearchPayload(menu.containerId, searchText).sendToServer()
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val window = floatingWindows.lastOrNull { it.visible }
        if (window != null && event.isEscape()) {
            window.close()
            if (focused === window) setFocused(null)
            return true
        }
        val editing = focused as? MIMFloatingWindow
        if (editing?.isEditing == true) {
            editing.keyPressed(event)
            return true
        }
        val field = focused as? EditBox
        if (field?.isFocused == true && !event.isEscape()) {
            field.keyPressed(event)
            return true
        }
        return super.keyPressed(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        updateLayout()
        floatingWindows.lastOrNull { it.isMouseOver(event.x(), event.y()) }?.let { window ->
            bringToFront(window)
            window.mouseClicked(event, doubleClick)
            setFocused(floatingWindows.lastOrNull { it.visible })
            windowClickButtons[event.button()] = window
            return true
        }
        val itemButton = listOfNotNull(craftingButton, modeButton).firstOrNull { it.isMouseOver(event.x(), event.y()) }
        if (itemButton != null) {
            if (itemButton.mouseClicked(event, doubleClick)) setFocused(itemButton)
            itemButtonClicks[event.button()] = itemButton
            return true
        }
        val bar = scrollBar
        if (bar?.active == true && bar.isMouseOver(event.x(), event.y()) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            val handled = super.mouseClicked(event, doubleClick)
            draggingScrollBar = handled
            return handled
        }
        if (menu.isTerminal) {
            val slot = menu.slots.take(MIMMenu.PAGE_SIZE).firstOrNull { slot ->
                slot.isActive && isHovering(slot.x, slot.y, 16, 16, event.x(), event.y())
            }
            val button = when (event.button()) {
                InputConstants.MOUSE_BUTTON_LEFT -> 0
                InputConstants.MOUSE_BUTTON_RIGHT -> 1
                else -> null
            }
            if (slot != null && button != null) {
                setFocused(null)
                if (menu.carried.isEmpty && menu.isCraftingMode && menu.canCraft(slot.index)) {
                    requestDependencyTree(slot.index, slot.item.copyWithCount(1), 1)
                    setFocused(dependencyWindow)
                    virtualClickButtons.add(event.button())
                    return true
                }
                val input = if (menu.carried.isEmpty && event.hasShiftDown()) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
                slotClicked(slot, slot.index, button, input)
                virtualClickButtons.add(event.button())
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean =
        when {
            itemButtonClicks.isNotEmpty() -> true
            windowClickButtons.isNotEmpty() -> {
                windowClickButtons.values.first().mouseDragged(event, dragX, dragY)
                true
            }
            virtualClickButtons.isNotEmpty() -> true
            draggingScrollBar -> scrollBar?.mouseDragged(event, dragX, dragY) == true
            else -> super.mouseDragged(event, dragX, dragY)
        }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        itemButtonClicks.remove(event.button())?.let { button ->
            button.mouseReleased(event)
            return true
        }
        windowClickButtons.remove(event.button())?.let { window ->
            window.mouseReleased(event)
            return true
        }
        if (virtualClickButtons.remove(event.button())) return true
        if (!draggingScrollBar) return super.mouseReleased(event)
        draggingScrollBar = false
        setDragging(false)
        scrollBar?.mouseReleased(event)
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        updateLayout()
        floatingWindows.lastOrNull { it.isMouseOver(mouseX, mouseY) }?.let { window ->
            window.mouseScrolled(mouseX, mouseY, horizontal, vertical)
            return true
        }
        val gridY = menu.configurationLayout?.gridY ?: MIMTerminalLayout.GRID_Y
        val available = !menu.isTerminal || menu.isStorageAvailable
        if (available && mouseX >= leftPos + 7 && mouseX < leftPos + imageWidth - 7 &&
            (scrollBar?.isMouseOver(mouseX, mouseY) == true ||
                mouseY >= topPos + gridY - 1 && mouseY < topPos + gridY + menu.visibleRows * MIMTerminalLayout.ROW_HEIGHT) &&
            scrollBar?.mouseScrolled(mouseX, mouseY, horizontal, vertical) == true)
            return true
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    private fun requestScroll(row: Int) {
        pendingScrollRow = row
        MIMViewportPayload(menu.containerId, MIMTerminalLayout.rowsForHeight(height), row).sendToServer()
    }

    private fun updateLayout() {
        leftPos = (width - imageWidth) / 2
        menu.updateSlotPositions()
        if (pendingScrollRow == menu.scrollRow || (pendingScrollRow ?: 0) > menu.maxScrollRow) pendingScrollRow = null
        if (!menu.isTerminal) {
            scrollBar?.update(pendingScrollRow ?: menu.scrollRow, menu.maxScrollRow, menu.visibleRows)
            return
        }
        if (imageHeight != menu.screenHeight) {
            (this as ContainerScreenAccessor).setImageHeight(menu.screenHeight)
            topPos = (height - imageHeight) / 2
        }
        search?.layout(leftPos, topPos)
        floatingWindows.forEach { it.update(menu) }
        craftingPreview?.update()
        val rightWindowX = leftPos + MIMTerminalLayout.CRAFT_BUTTON_X + MIMTerminalLayout.CRAFT_BUTTON_WIDTH + PANEL_GAP
        floatingWindows.forEach { window ->
            val initialX = if (rightWindowX + window.windowWidth <= width) rightWindowX
            else (width - window.windowWidth) / 2
            window.fitViewport(width, height, initialX, topPos)
        }
        craftingButton?.let { button ->
            button.x = leftPos + MIMTerminalLayout.CRAFT_BUTTON_X
            button.y = topPos + MIMTerminalLayout.CRAFT_BUTTON_Y
            button.visible = menu.isConnected && menu.queuedCrafts > 0
            button.active = button.visible
        }
        modeButton?.let { button ->
            button.x = leftPos + MIMTerminalLayout.CRAFT_BUTTON_X
            button.y = topPos + if (craftingButton?.visible == true) MIMTerminalLayout.MODE_BUTTON_Y else MIMTerminalLayout.CRAFT_BUTTON_Y
            button.visible = menu.isStorageAvailable && menu.hasRecipes
            button.active = button.visible
            button.update(if (menu.isCraftingMode) Items.CRAFTING_TABLE else Items.CHEST,
                Component.translatable(if (menu.isCraftingMode) "screen.escr.mim.mode.crafting" else "screen.escr.mim.mode.storage"))
        }
        scrollBar?.let { bar ->
            bar.x = leftPos + MIMTerminalLayout.SCROLL_X
            bar.y = topPos + MIMTerminalLayout.GRID_Y
            bar.height = MIMTerminalLayout.scrollHeight(menu.visibleRows)
            bar.update(pendingScrollRow ?: menu.scrollRow, menu.maxScrollRow, menu.visibleRows)
            bar.visible = menu.isStorageAvailable
        }
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        updateLayout()
        super.extractBackground(graphics, mouseX, mouseY, deltaTicks)
        if (menu.isTerminal)
            MIMGuiTextures.terminal(graphics, leftPos, topPos, menu.visibleRows, menu.isStorageAvailable)
        else
            MIMGuiTextures.configuration(graphics, leftPos, topPos, menu.configurationLayout!!)
        menu.configurationLayout?.mruBar?.let { bar ->
            val overWindow = floatingWindows.any { it.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) }
            drawMRULine(graphics, mruView, bar.x, bar.y,
                leftPos, topPos, bar.width, bar.height,
                if (overWindow) -1 else mouseX, if (overWindow) -1 else mouseY,
                animation = mruAnimation, deltaTicks = deltaTicks)
        }
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val overWindow = floatingWindows.any { it.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) }
        super.extractContents(graphics, if (overWindow) -1 else mouseX, if (overWindow) -1 else mouseY, deltaTicks)
    }

    override fun extractFloatingWindows(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val overWindow = isFloatingWindowAt(mouseX.toDouble(), mouseY.toDouble())
        listOfNotNull(craftingButton, modeButton).forEach {
            it.extractRenderState(graphics, if (overWindow) -1 else mouseX, if (overWindow) -1 else mouseY, deltaTicks)
        }
        floatingWindows.forEach { it.extractRenderState(graphics, mouseX, mouseY, deltaTicks) }
        floatingWindows.lastOrNull { it.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) }
            ?.extractTooltip(graphics, mouseX, mouseY)
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (menu.isTerminal) extractStorageWarning(graphics)
    }

    private fun extractStorageWarning(graphics: GuiGraphicsExtractor) {
        val warning = when {
            !menu.isConnected -> Component.translatable("screen.escr.mim.no_core")
            !menu.hasPower -> Component.translatable("screen.escr.mim.no_power")
            else -> null
        }
        if (warning == null) {
            warningSince = null
            return
        }
        val now = Util.getMillis()
        val since = warningSince ?: now.also { warningSince = it }
        if ((now - since) % WARNING_PERIOD_MS >= WARNING_VISIBLE_MS) return
        graphics.text(
            font, warning,
            MIMTerminalLayout.STORAGE_X + (MIMTerminalLayout.STORAGE_WIDTH - font.width(warning)) / 2,
            MIMTerminalLayout.GRID_Y + (menu.visibleRows * MIMTerminalLayout.ROW_HEIGHT - font.lineHeight) / 2,
            TEXT, false
        )
    }

    override fun extractSlots(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        super.extractSlots(graphics, mouseX, mouseY)
        if (!menu.isTerminal || !menu.isStorageAvailable) return
        graphics.nextStratum()
        menu.slots.take(MIMMenu.PAGE_SIZE).filter { it.isActive && it.hasItem() }.forEach { slot ->
            val amount = menu.count(slot.index)
            val count = MIMItemCount.format(amount, menu.canCraft(slot.index))
            graphics.pose().pushMatrix()
            graphics.pose().translate(slot.x + 16f, slot.y + 16f)
            graphics.pose().scale(count.scale, count.scale)
            graphics.text(font, count.text, -font.width(count.text), -font.lineHeight, 0xFFFFFFFF.toInt(), true)
            graphics.pose().popMatrix()
        }
    }

    override fun extractSlot(graphics: GuiGraphicsExtractor, slot: Slot, mouseX: Int, mouseY: Int) {
        if (menu.isTerminal && slot.index < MIMMenu.PAGE_SIZE && !menu.isStorageAvailable) return
        super.extractSlot(graphics, slot, mouseX, mouseY)
    }

    fun sendCoreClick(slotId: Int, button: Int, input: ContainerInput) {
        MIMCoreClickPayload(menu.containerId, menu.scrollRow, slotId, button, input).sendToServer()
    }

    override fun getTooltipFromContainerItem(stack: ItemStack): List<Component> {
        val tooltip = super.getTooltipFromContainerItem(stack).toMutableList()
        val index = hoveredSlot?.index ?: return tooltip
        if (menu.isTerminal && index in 0..<MIMMenu.PAGE_SIZE) {
            tooltip += if (menu.isCraftingMode) Component.translatable("tooltip.escr.mim.open_recipe")
            else Component.translatable("tooltip.escr.mim.transfer")
        }
        return tooltip
    }

    override fun extractTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (floatingWindows.any { it.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) }) return
        if (menu.isTerminal && hoveredSlot?.let { it.index < MIMMenu.PAGE_SIZE } == true && !menu.isStorageAvailable) return
        super.extractTooltip(graphics, mouseX, mouseY)
    }

    override fun hasClickedOutside(mouseX: Double, mouseY: Double, left: Int, top: Int): Boolean {
        if (floatingWindows.any { it.isMouseOver(mouseX, mouseY) }) return false
        if (craftingButton?.isMouseOver(mouseX, mouseY) == true) return false
        if (modeButton?.isMouseOver(mouseX, mouseY) == true) return false
        return super.hasClickedOutside(mouseX, mouseY, left, top)
    }

    fun registerWindow(window: MIMFloatingWindow) {
        if (window !in floatingWindows) floatingWindows += window
        if (window !in children()) addWidget(window)
    }

    private fun requestDependencyTree(slot: Int, target: ItemStack, batches: Int) {
        craftingPreview?.openTree(slot, target, batches)
        dependencyWindow?.let { window ->
            bringToFront(window)
        }
    }

    private fun bringToFront(window: MIMFloatingWindow) {
        floatingWindows.remove(window)
        floatingWindows.add(window)
    }

    fun acceptDependencyTree(response: MIMDependencyResponse) {
        craftingPreview?.accept(response)
    }

    fun acceptTasks(response: MIMTaskResponse) { taskWindow?.accept(response) }

    companion object {
        private const val WARNING_PERIOD_MS = 1500L
        private const val WARNING_VISIBLE_MS = 1000L
        private const val TEXT = MIMGuiTextures.TEXT
        private const val PANEL_GAP = 4
    }
}
