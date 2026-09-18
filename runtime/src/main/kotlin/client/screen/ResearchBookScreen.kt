package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.client.research.BookElementRenderContext
import com.algorithmlx.ecr.api.client.research.BookElementRenderers
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.research.*
import com.algorithmlx.ecr.api.research.content.*
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.client.book.*
import com.algorithmlx.ecr.client.book.controller.BookBookmarkController
import com.algorithmlx.ecr.client.book.controller.MultiblockBookPreviewController
import com.algorithmlx.ecr.client.book.renderer.BookRecipeElementRenderer
import com.algorithmlx.ecr.client.book.renderer.BookThreadRenderer
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.cursor.CursorTypes
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.item.ItemStack
import kotlin.math.*

class ResearchBookScreen(
    private val bookType: BookType? = null,
) : Screen(Component.translatable("screen.$ModId.research_book")) {
    private val bookTexture = "textures/gui/book/book.png".ecRL
    private val arrowLeft = "textures/gui/book/arrow_left.png".ecRL
    private val arrowLeftSelected = "textures/gui/book/arrow_left_selected.png".ecRL
    private val arrowRight = "textures/gui/book/arrow_right.png".ecRL
    private val arrowRightSelected = "textures/gui/book/arrow_right_selected.png".ecRL

    private var selectedCategory: Identifier? = null
    private var selectedEntry: BookEntry? = null
    private var spreads = listOf(BookSpread(emptyList()))
    private var spreadIndex = 0
    private var contentRevision = -1L

    private var panX = 0f
    private var panY = 0f
    private var targetPanX = 0f
    private var targetPanY = 0f

    private var zoom = 1f
    private var targetZoom = 1f

    private var categoryScroll = 0f
    private var targetCategoryScroll = 0f

    private val bookmarks = BookBookmarkController()

    private var draggingGraph = false
    private var draggingCategorySlider = false
    private var partialTick = 0f

    private var lastFrameNanos = -1L
    private var frameDt = 0f

    override fun init() {
        super.init()
        MultiblockBookPreviewController.clear()
        val categories = categories()
        val saved = ClientResearchState.viewState()
        bookmarks.restore(saved, width, height)
        val savedCategory = saved.category?.takeIf { id -> categories.any { it.id == id && isCategoryAvailable(it) } }
        selectedCategory = savedCategory ?: categories.firstOrNull(::isCategoryAvailable)?.id
        if (savedCategory != null) {
            targetPanX = saved.panX
            targetPanY = saved.panY
            targetZoom = saved.zoom.coerceIn(0.5f, 2f)
            saved.entry?.let { ResearchCatalog.snapshot().entries[it] }
                ?.takeIf(::isAvailable)
                ?.takeIf { ResearchCatalog.snapshot().layout[it.id]?.category == selectedCategory }
                ?.let { openEntry(it, saved.spread) }
        } else centerCategory()

        constrainPan()
        panX = targetPanX
        panY = targetPanY
        zoom = targetZoom
    }

    override fun tick() {
        super.tick()
        val revision = ClientResearchState.revision()
        val entry = selectedEntry
        if (entry != null && revision != contentRevision) {
            spreads = BookPageLayout.paginate(entry)
            spreadIndex = spreadIndex.coerceIn(0, spreads.lastIndex)
            contentRevision = revision
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.partialTick = partialTick
        this.frameDt = frameDelta()

        panX = approach(panX, targetPanX, frameDt, PAN_HALF_LIFE)
        panY = approach(panY, targetPanY, frameDt, PAN_HALF_LIFE)
        zoom = approach(zoom, targetZoom, frameDt, ZOOM_HALF_LIFE)
        categoryScroll = approach(categoryScroll, targetCategoryScroll, frameDt, SCROLL_HALF_LIFE)
        constrainPan()

        bookmarks.update(frameDt, width, height, mouseX, mouseY)

        if (selectedEntry == null) {
            renderSpace(graphics)
            renderGraph(graphics, mouseX, mouseY)
        } else {
            renderBook(graphics, mouseX, mouseY, partialTick)
        }
        bookmarks.renderPicker(graphics, mouseX, mouseY)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (
            event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT &&
            event.button() != InputConstants.MOUSE_BUTTON_MIDDLE
        ) {
            return super.mouseClicked(event, doubleClick)
        }
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()

        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && bookmarks.click(mouseX, mouseY)) return true
        if (selectedEntry != null) {
            return MultiblockBookPreviewController.mouseClicked(mouseX, mouseY, event.button(), isShiftDown(), ::onClose) ||
                handleBookClick(mouseX, mouseY, event.button())
        }
        if (
            event.button() == InputConstants.MOUSE_BUTTON_LEFT &&
            bookmarks.clickGlobalSlider(mouseX, mouseY, width, height)
        ) return true
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
        bookmarks.selectGlobal(mouseX, mouseY, width, height)?.let { bookmark ->
            ResearchCatalog.snapshot().entries[bookmark.research]?.let { openEntry(it, bookmark.spread) }
            return true
        }
        if (selectCategoryAt(mouseX, mouseY)) return true
        if (hasCategoryOverflow() && mouseY in 14..20) {
            draggingCategorySlider = true
            updateCategorySlider(mouseX)
            return true
        }

        nodeAt(mouseX, mouseY)?.let { node ->
            if (!isAvailable(node.entry)) return true
            openEntry(node.entry)
            return true
        }

        if (mouseY >= GRAPH_TOP) {
            draggingGraph = true
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT)
            return super.mouseDragged(event, dragX, dragY)

        if (selectedEntry != null && MultiblockBookPreviewController.mouseDragged(dragX, dragY, isShiftDown())) return true
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseDragged(event, dragX, dragY)
        if (bookmarks.drag(event.x().toInt(), event.y().toInt(), width, height)) return true

        if (draggingCategorySlider) {
            updateCategorySlider(event.x().toInt())
            return true
        }
        if (draggingGraph && selectedEntry == null) {
            val dx = (dragX / zoom).toFloat()
            val dy = (dragY / zoom).toFloat()
            targetPanX += dx
            targetPanY += dy
            return true
        }
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val previewReleased = MultiblockBookPreviewController.mouseReleased(event.button())
        draggingGraph = false
        draggingCategorySlider = false
        bookmarks.stopDragging()
        return previewReleased || super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (selectedEntry != null) {
            return MultiblockBookPreviewController.mouseScrolled(mouseX.toInt(), mouseY.toInt(), scrollY)
        }
        val shift = isShiftDown()

        if (bookmarks.scrollGlobal(mouseX.toInt(), mouseY.toInt(), scrollY, width, height)) return true

        if (shift && hasCategoryOverflow()) {
            targetCategoryScroll = (targetCategoryScroll - scrollY.toFloat() * 24f).coerceIn(0f, maxCategoryScroll())
            return true
        }

        val oldTargetZoom = targetZoom
        targetZoom = (targetZoom + scrollY.toFloat() * ResearchBookConfigValues.zoomStep()).coerceIn(0.5f, 2f)
        val graphX = mouseX.toFloat() - width / 2f
        val graphY = mouseY.toFloat() - GRAPH_TOP

        if (oldTargetZoom != targetZoom) {
            targetPanX -= graphX * (1f / oldTargetZoom - 1f / targetZoom)
            targetPanY -= graphY * (1f / oldTargetZoom - 1f / targetZoom)
        }
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (selectedEntry != null && event.key() == InputConstants.KEY_R && BookRecipeElementRenderer.openHoveredRecipe()) return true
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (bookmarks.isPickerOpen) {
                bookmarks.close()
                return true
            }
            if (selectedEntry != null) {
                closeEntry()
                return true
            }
        }
        return super.keyPressed(event)
    }

    override fun isPauseScreen(): Boolean = false

    override fun removed() {
        saveViewState()
        MultiblockBookPreviewController.clear()
        super.removed()
    }

    private fun isShiftDown(): Boolean = InputConstants.isKeyDown(
        InputConstants.KEY_LSHIFT
    ) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)

    private fun frameDelta(): Float {
        val now = System.nanoTime()
        val delta = if (lastFrameNanos < 0) 0f else ((now - lastFrameNanos) / 1000000000f).coerceIn(0f, 0.1f)
        lastFrameNanos = now
        return delta
    }

    private fun approach(current: Float, target: Float, dt: Float, halfLife: Float): Float {
        if (halfLife <= 0f || dt <= 0f) return target
        val decay = 0.5f.pow(dt / halfLife)
        val result = target + (current - target) * decay
        return if (abs(result - target) < 0.01f) target else result
    }

    private fun renderSpace(graphics: GuiGraphicsExtractor) {
        graphics.fill(
            BookRenderPipelines.forCategory(selectedCategory()),
            0,
            0,
            width,
            height,
            ResearchBookConfigValues.spaceColor(panX, panY, zoom),
        )
    }

    private fun renderGraph(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        renderCategories(graphics, mouseX, mouseY)
        graphics.enableScissor(0, GRAPH_TOP, width, height)
        val nodes = visibleNodes()
        val visibleById = nodes.associateBy { it.entry.id }
        nodes.forEach { node ->
            node.entry.dependencies.forEach { dependencyId ->
                val dependency = visibleById[dependencyId]
                if (dependency != null && dependency.category == node.category) renderThread(graphics, dependency, node)
            }
        }
        nodes.forEach { renderNode(graphics, it) }
        graphics.disableScissor()
        nodes.firstOrNull { isInsideNode(it, mouseX, mouseY) }?.let { node ->
            if (isAvailable(node.entry)) graphics.requestCursor(CursorTypes.POINTING_HAND)
            renderNodeTooltip(graphics, node.entry, mouseX, mouseY)
        }
        bookmarks.renderGlobal(graphics, width, mouseX, mouseY)
    }

    private fun renderCategories(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        categories().forEachIndexed { index, category ->
            val x = (index * TAB_WIDTH - categoryScroll).toInt()
            if (x + TAB_WIDTH < 0 || x > width) return@forEachIndexed
            val selected = category.id == selectedCategory
            val available = isCategoryAvailable(category)

            val bgColor = if (selected) 0x40FFFFFF else 0x00000000
            if (bgColor != 0) {
                graphics.fill(x, 0, x + TAB_WIDTH, CATEGORY_HEIGHT, bgColor)
            }

            renderIcon(graphics, category.icon, x + 4, 3, 10)
            if (!available) graphics.fill(x, 0, x + TAB_WIDTH, CATEGORY_HEIGHT, 0xA0000000.toInt())

            if (mouseX in x until x + TAB_WIDTH && mouseY in 0..CATEGORY_HEIGHT) {
                if (available) graphics.requestCursor(CursorTypes.POINTING_HAND)
                graphics.setComponentTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    listOf(
                        category.title.component(category.titleShadow),
                        categoryProgress(category),
                    ),
                    mouseX,
                    mouseY.coerceAtLeast(CATEGORY_HEIGHT + 4),
                )
            }
        }

        if (hasCategoryOverflow()) {
            val trackWidth = width.coerceAtLeast(1)
            val thumbWidth = (trackWidth * (trackWidth.toFloat() / (categories().size * TAB_WIDTH))).toInt().coerceAtLeast(24)
            val thumbX = ((trackWidth - thumbWidth) * (categoryScroll / maxCategoryScroll())).toInt()
            if (mouseY in 14..20) graphics.requestCursor(CursorTypes.POINTING_HAND)
            graphics.fill(0, 16, trackWidth, 18, 0x80101820.toInt())
            graphics.fill(thumbX, 16, thumbX + thumbWidth, 18, 0xFFD0D8E8.toInt())
        }
    }

    private fun renderThread(graphics: GuiGraphicsExtractor, from: ResolvedBookEntry, to: ResolvedBookEntry) {
        val color = ResearchCatalog.snapshot().categories[to.category]?.threadColor
        BookThreadRenderer.render(graphics, nodeCenter(from), nodeCenter(to), ClientResearchState.has(to.entry.id), color)
    }

    private fun renderNode(graphics: GuiGraphicsExtractor, node: ResolvedBookEntry) {
        val available = isAvailable(node.entry)

        val point = nodePoint(node)
        val frame = node.entry.frame

        graphics.pose().pushMatrix()
        graphics.pose().translate(point.first, point.second)
        graphics.pose().scale(zoom, zoom)

        if (frame != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, frame.texture, 0, 0, 0f, 0f, frame.width, frame.height, frame.width, frame.height)
        }

        val iconX = frame?.itemX ?: 0
        val iconY = frame?.itemY ?: 0
        renderIcon(graphics, node.entry.icon, iconX, iconY, frame?.itemSize ?: 16)
        if (available && !ClientResearchState.has(node.entry.id)) renderAvailablePulse(graphics, node.entry)

        if (!available || !ClientResearchState.has(node.entry.id))
            graphics.fill(0, 0, nodeWidth(node.entry), nodeHeight(node.entry), 0x78000000)

        graphics.pose().popMatrix()
    }

    private fun renderAvailablePulse(graphics: GuiGraphicsExtractor, entry: BookEntry) {
        val blinkSeconds = ResearchBookConfigValues.availableBlinkSeconds()
        val cycle = ((System.nanoTime() / 1000000000.0) % blinkSeconds) / blinkSeconds
        val alpha = (36 + (sin(cycle * Math.PI * 2.0 - Math.PI / 2.0) * 0.5 + 0.5) * 112).roundToInt()
        val color = (alpha.coerceIn(0, 255) shl 24) or 0xDCEBFF
        graphics.outline(-2, -2, nodeWidth(entry) + 4, nodeHeight(entry) + 4, color)
        graphics.outline(-1, -1, nodeWidth(entry) + 2, nodeHeight(entry) + 2, color)
    }

    private fun renderNodeTooltip(graphics: GuiGraphicsExtractor, entry: BookEntry, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font
        val description =
            entry.description
                ?.takeUnless { it.value.isBlank() }
                ?.component(false)
                ?.copy()
                ?.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        if (description == null) {
            graphics.setComponentTooltipForNextFrame(font, nodeTooltip(entry), mouseX, mouseY)
            return
        }

        val components = mutableListOf<ClientTooltipComponent>()
        components += ClientTooltipComponent.create(entry.title.component(entry.titleShadow).visualOrderText)
        font.split(description, (NODE_TOOLTIP_DESCRIPTION_WIDTH / NODE_TOOLTIP_DESCRIPTION_SCALE).toInt()).forEach { line ->
            components += SmallTextTooltipComponent(line)
        }
        nodeTooltip(entry, includeTitle = false).forEach { line ->
            components += ClientTooltipComponent.create(line.visualOrderText)
        }

        graphics.tooltip(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null, false)
    }

    private fun nodeTooltip(entry: BookEntry, includeTitle: Boolean = true): List<Component> =
        buildList {
            if (includeTitle) add(entry.title.component(entry.titleShadow))
            val missing = missingRequirements(entry)
            if (missing.visible.isNotEmpty() || missing.hidden > 0) {
                add(Component.translatable("screen.$ModId.research_book.task.requires"))
                missing.visible.forEach { add(Component.literal(" - ").append(it)) }
                if (missing.hidden > 0) add(Component.translatable("screen.$ModId.research_book.task.more", missing.hidden))
            }
            activeTaskProgress(entry).filter { !it.first.hidden }.forEach { (definition, progress) ->
                add(Component.empty().append(taskTitle(definition)).append(Component.literal(": ${progress.current}/${progress.required}")))
            }
        }

    private class SmallTextTooltipComponent(private val line: FormattedCharSequence) : ClientTooltipComponent {
        override fun getHeight(font: Font): Int = (font.lineHeight * NODE_TOOLTIP_DESCRIPTION_SCALE).roundToInt().coerceAtLeast(1)

        override fun getWidth(font: Font): Int = (font.width(line) * NODE_TOOLTIP_DESCRIPTION_SCALE).roundToInt().coerceAtLeast(1)

        override fun extractText(
            graphics: GuiGraphicsExtractor,
            font: Font,
            x: Int,
            y: Int,
        ) {
            graphics.pose().pushMatrix()
            graphics.pose().translate(x.toFloat(), y.toFloat())
            graphics.pose().scale(NODE_TOOLTIP_DESCRIPTION_SCALE, NODE_TOOLTIP_DESCRIPTION_SCALE)
            graphics.text(font, line, 0, 0, 0xFFA0A0A0.toInt())
            graphics.pose().popMatrix()
        }
    }

    private fun missingRequirements(entry: BookEntry): MissingRequirements {
        val visible = mutableListOf<Component>()
        var hidden = 0
        val snapshot = ResearchCatalog.snapshot()

        entry.dependencies.forEach { dependencyId ->
            if (ClientResearchState.has(dependencyId)) return@forEach
            val dependency = snapshot.entries[dependencyId]
            if (dependency != null && !isVisible(dependency)) {
                hidden++
            } else {
                visible += researchRequirementComponent(dependencyId, null)
            }
        }

        entry.requirements.forEach { requirement ->
            if (ClientResearchState.requirementMet(entry.id, requirement)) return@forEach
            val target = snapshot.entries[requirement.researchId(entry.id)]
            if (target != null && !isVisible(target)) hidden++
            else visible += researchRequirementComponent(requirement.researchId(entry.id), requirement.task)
        }

        return MissingRequirements(visible, hidden)
    }

    private fun researchRequirementComponent(
        research: Identifier,
        taskId: String?,
    ): Component {
        val entry = ResearchCatalog.snapshot().entries[research]
        val title = entry?.title?.component(entry.titleShadow) ?: Component.literal(research.toString())
        return if (taskId == null) {
            Component.translatable("screen.$ModId.research_book.research", title)
        } else {
            val taskTitle =
                entry?.taskDefinitions?.firstOrNull { it.id == taskId }?.let(::taskTitle)
                    ?: if (taskId.startsWith("task_")) Component.translatable("screen.$ModId.research_book.task")
                    else Component.literal(taskId)
            Component.translatable("screen.$ModId.research_book.task")
                .append(String.format(": %s / %s", title, taskTitle))
        }
    }

    private fun taskTitle(definition: ResearchTaskDefinition): Component =
        definition.title?.component() ?: when (val task = definition.task) {
            is CraftingResearchTask -> {
                Component.literal(task.recipe.toString())
            }

            is ExperienceResearchTask -> {
                if (task.levels) {
                    Component.translatable("screen.$ModId.research_book.experience.levels")
                } else {
                    Component.translatable("screen.$ModId.research_book.experience")
                }
            }

            else -> {
                Component.translatable("screen.$ModId.research_book.task")
            }
        }

    private fun renderBook(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val entry = selectedEntry ?: return
        val transform = bookTransform()
        val localMouseX = ((mouseX - transform.x) / transform.scale).toInt()
        val localMouseY = ((mouseY - transform.y) / transform.scale).toInt()

        graphics.pose().pushMatrix()
        graphics.pose().translate(transform.x.toFloat(), transform.y.toFloat())
        graphics.pose().scale(transform.scale, transform.scale)

        val bookmarkHovered = localMouseX in 496..<550 && localMouseY in BOOKMARK_Y until BOOKMARK_Y + 16
        bookmarks.renderPage(graphics, entry, spreadIndex, bookmarkHovered, frameDt)
        if (bookmarkHovered && ClientResearchState.has(entry.id)) {
            graphics.requestCursor(CursorTypes.POINTING_HAND)
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, bookTexture, 0, 0, 0f, 0f, BOOK_WIDTH, BOOK_HEIGHT, BOOK_WIDTH, BOOK_HEIGHT)

        renderPageArrows(graphics, localMouseX, localMouseY)

        graphics.enableScissor(0, 0, BOOK_WIDTH, BOOK_HEIGHT)

        BookRecipeElementRenderer.clearHoveredViewerStack()
        BookResearchLinkController.beginFrame()
        MultiblockBookPreviewController.beginFrame()
        spreads[spreadIndex].elements.forEachIndexed { placementIndex, placement ->
            val scale = transform.scale

            val absX = transform.x + (placement.x * scale).roundToInt()
            val absY = transform.y + (placement.y * scale).roundToInt()

            val absRight = transform.x + ((placement.x + placement.width) * scale).roundToInt()
            val absLeft = transform.y + ((placement.y + placement.height) * scale).roundToInt()

            val absW = (absRight - absX).coerceAtLeast(1)
            val absH = (absLeft - absY).coerceAtLeast(1)

            val pageScissor = pageScissor(transform, placement.x)

            BookElementRenderers.render(
                placement.element.content.type,
                BookElementRenderContext(
                    graphics,
                    placement.x,
                    placement.y,
                    placement.width,
                    placement.height,
                    localMouseX,
                    localMouseY,
                    partialTick,
                    absX,
                    absY,
                    absW,
                    absH,
                    transform.scale,
                    placement.textLines,
                    "${entry.id}|$spreadIndex|$placementIndex",
                    entry.id,
                    placement.textLineStart,
                    placement.textLineCount,
                    pageScissor,
                ),
                placement.element.content,
            )
        }
        graphics.disableScissor()

        val font = Minecraft.getInstance().font
        val title = entry.title.component(entry.titleShadow)
        graphics.text(font, title, TITLE_X, TITLE_Y, 0xFF263746.toInt(), entry.titleShadow)
        renderCompleteButton(graphics, entry, localMouseX, localMouseY)

        graphics.pose().popMatrix()
        renderTaskLevels(graphics, entry)
        if (bookmarkHovered && ClientResearchState.has(entry.id)) {
            renderBookmarkTooltip(graphics, entry, spreadIndex, spreads.size, mouseX, mouseY)
        }
    }

    private fun renderBookmarkTooltip(
        graphics: GuiGraphicsExtractor,
        entry: BookEntry,
        spread: Int,
        spreadCount: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val font = Minecraft.getInstance().font
        val title = entry.title.component(entry.titleShadow)

        val page =
            Component
                .translatable("screen.$ModId.research_book.page", spread + 1, spreadCount.coerceAtLeast(1))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        graphics.setComponentTooltipForNextFrame(font, listOf(title, page), mouseX, mouseY)
    }

    private fun bookTransform(): BookTransform {
        val finalScale = min((width - 40f) / BOOK_WIDTH, (height - 40f) / BOOK_HEIGHT).coerceAtMost(1f)
        return BookTransform(
            ((width - BOOK_WIDTH * finalScale) / 2f).toInt(),
            ((height - BOOK_HEIGHT * finalScale) / 2f).toInt(),
            finalScale,
        )
    }

    private fun pageScissor(transform: BookTransform, elementX: Int): ScreenRectangle {
        val pageX = if (elementX < BOOK_WIDTH / 2) FIRST_PAGE_X else SECOND_PAGE_X

        val scale = transform.scale

        val left = transform.x + (pageX * scale).roundToInt()
        val top = transform.y + (PAGE_TOP * scale).roundToInt()

        val right = transform.x + ((pageX + PAGE_WIDTH) * scale).roundToInt()
        val bottom = transform.y + ((PAGE_TOP + PAGE_HEIGHT) * scale).roundToInt()

        return ScreenRectangle(
            left,
            top,
            (right - left).coerceAtLeast(1),
            (bottom - top).coerceAtLeast(1),
        )
    }

    private fun renderPageArrows(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (spreadIndex > 0) {
            val leftHovered = mouseX in LEFT_ARROW_X until LEFT_ARROW_X + 27 && mouseY in ARROW_Y until ARROW_Y + 23
            if (leftHovered) graphics.requestCursor(CursorTypes.POINTING_HAND)
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                if (leftHovered) arrowLeftSelected else arrowLeft,
                LEFT_ARROW_X,
                ARROW_Y,
                0f,
                0f,
                27,
                23,
                27,
                23,
            )
        }
        if (spreadIndex < spreads.lastIndex) {
            val rightHovered = mouseX in RIGHT_ARROW_X until RIGHT_ARROW_X + 27 && mouseY in ARROW_Y until ARROW_Y + 23
            if (rightHovered) graphics.requestCursor(CursorTypes.POINTING_HAND)
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                if (rightHovered) arrowRightSelected else arrowRight,
                RIGHT_ARROW_X,
                ARROW_Y,
                0f,
                0f,
                27,
                23,
                27,
                23,
            )
        }
    }

    private fun handleBookClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
        val entry = selectedEntry ?: return false
        val transform = bookTransform()
        val x = ((mouseX - transform.x) / transform.scale).toInt()
        val y = ((mouseY - transform.y) / transform.scale).toInt()

        if (button == InputConstants.MOUSE_BUTTON_LEFT && openHoveredLink()) return true

        if (button == InputConstants.MOUSE_BUTTON_LEFT && shouldShowCompleteButton(entry) && x in COMPLETE_BUTTON_X until COMPLETE_BUTTON_X + COMPLETE_BUTTON_WIDTH &&
            y in COMPLETE_BUTTON_Y until COMPLETE_BUTTON_Y + COMPLETE_BUTTON_HEIGHT
        ) {
            if (tasksComplete(entry)) ResearchNetwork.completeResearch(entry.id)
            return true
        }

        if (x in 496..<550 && y in BOOKMARK_Y until BOOKMARK_Y + 16) {
            bookmarks.activate(entry, spreadIndex, button == InputConstants.MOUSE_BUTTON_MIDDLE)
            return true
        }

        if (button == InputConstants.MOUSE_BUTTON_LEFT && spreadIndex > 0 && x in LEFT_ARROW_X until LEFT_ARROW_X + 27 && y in ARROW_Y until ARROW_Y + 23) {
            spreadIndex--
            return true
        }

        if (button == InputConstants.MOUSE_BUTTON_LEFT && spreadIndex < spreads.lastIndex && x in RIGHT_ARROW_X until RIGHT_ARROW_X + 27 &&
            y in ARROW_Y until ARROW_Y + 23
        ) {
            spreadIndex++
            return true
        }

        return true
    }

    private fun openHoveredLink(): Boolean = BookResearchLinkController.hovered()?.let(::openLink) == true

    private fun openLink(link: BookResearchLink): Boolean {
        val entry = ResearchCatalog.snapshot().entries[link.research] ?: return false
        if (!isVisible(entry) || !isAvailable(entry)) return false
        return openEntry(entry, link.spread)
    }

    private fun renderCompleteButton(graphics: GuiGraphicsExtractor, entry: BookEntry, mouseX: Int, mouseY: Int) {
        if (!shouldShowCompleteButton(entry)) return
        val enabled = tasksComplete(entry)
        val hovered =
            enabled && mouseX in COMPLETE_BUTTON_X until COMPLETE_BUTTON_X + COMPLETE_BUTTON_WIDTH &&
                mouseY in COMPLETE_BUTTON_Y until COMPLETE_BUTTON_Y + COMPLETE_BUTTON_HEIGHT
        val background =
            when {
                !enabled -> 0xFF343840.toInt()
                hovered -> 0xFF55789A.toInt()
                else -> 0xFF3F607E.toInt()
            }
        if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND)
        val border = if (enabled) 0xFFB7D3EA.toInt() else 0xFF686D76.toInt()
        val textColor = if (enabled) 0xFFFFFFFF.toInt() else 0xFF9A9DA3.toInt()
        graphics.fill(
            COMPLETE_BUTTON_X,
            COMPLETE_BUTTON_Y,
            COMPLETE_BUTTON_X + COMPLETE_BUTTON_WIDTH,
            COMPLETE_BUTTON_Y + COMPLETE_BUTTON_HEIGHT,
            background,
        )
        graphics.outline(
            COMPLETE_BUTTON_X,
            COMPLETE_BUTTON_Y,
            COMPLETE_BUTTON_WIDTH,
            COMPLETE_BUTTON_HEIGHT,
            border,
        )
        val font = Minecraft.getInstance().font
        val labelKey = if (hasFinalTaskLevel(entry)) "complete_research" else "complete_task"
        val label = Component.translatable("screen.$ModId.$labelKey")
        val textX = COMPLETE_BUTTON_X + (COMPLETE_BUTTON_WIDTH - font.width(label)) / 2
        val textY = COMPLETE_BUTTON_Y + (COMPLETE_BUTTON_HEIGHT - font.lineHeight) / 2
        graphics.text(font, label, textX, textY, textColor, false)
    }

    private fun shouldShowCompleteButton(entry: BookEntry): Boolean =
        !entry.automatic && !ClientResearchState.has(entry.id) && isAvailable(entry) && !currentTaskLevelOpenOnly(entry)

    private fun currentTaskLevelOpenOnly(entry: BookEntry): Boolean {
        val levelIndex = ClientResearchState.completedTaskLevels(entry.id)
        val level = entry.taskLevels.getOrNull(levelIndex) ?: return false
        return level.tasks.isNotEmpty() && level.tasks.all { it.task is OpenResearchTask }
    }

    private fun tasksComplete(entry: BookEntry): Boolean {
        if (entry.taskLevels.isEmpty()) return true
        val active = activeTaskProgress(entry)
        return active.isNotEmpty() && active.all { it.second.complete }
    }

    private fun hasFinalTaskLevel(entry: BookEntry): Boolean =
        entry.taskLevels.isEmpty() || ClientResearchState.completedTaskLevels(entry.id) >= entry.taskLevels.lastIndex

    private fun activeTaskProgress(entry: BookEntry): List<Pair<ResearchTaskDefinition, ResearchTaskProgress>> {
        val levelIndex = ClientResearchState.completedTaskLevels(entry.id)
        val level = entry.taskLevels.getOrNull(levelIndex) ?: return emptyList()
        val offset = entry.taskLevels.take(levelIndex).sumOf { it.tasks.size }
        val progress = ClientResearchState.taskProgress(entry.id)
        return level.tasks.mapIndexedNotNull { index, definition ->
            progress.getOrNull(offset + index)?.let { definition to it }
        }
    }

    private fun renderTaskLevels(
        graphics: GuiGraphicsExtractor,
        entry: BookEntry,
    ) {
        if (entry.taskLevels.size <= 1) return
        val completed = if (ClientResearchState.has(entry.id)) entry.taskLevels.size
        else ClientResearchState.completedTaskLevels(entry.id)

        entry.taskLevels.indices.forEach { index ->
            val x = width - 8 - (entry.taskLevels.size - index) * TASK_LEVEL_STEP
            val color = when {
                index < completed -> 0xFF344252.toInt()
                index == completed -> 0xFFFFFFFF.toInt()
                else -> 0xFF87909B.toInt()
            }
            graphics.fill(x, 8, x + TASK_LEVEL_SIZE, 8 + TASK_LEVEL_SIZE, color)
            graphics.outline(x, 8, TASK_LEVEL_SIZE, TASK_LEVEL_SIZE, 0xFF202832.toInt())
        }
    }

    private fun renderIcon(graphics: GuiGraphicsExtractor, icon: BookIcon, x: Int, y: Int, size: Int = 16) {
        icon.item?.let { id ->
            BuiltInRegistries.ITEM.getOptional(id).ifPresent {
                graphics.pose().pushMatrix()
                graphics.pose().translate(x.toFloat(), y.toFloat())
                val itemScale = size / 16f
                graphics.pose().scale(itemScale, itemScale)
                graphics.item(ItemStack(it), 0, 0)
                graphics.pose().popMatrix()
            }
        }
        icon.texture?.let { graphics.blit(RenderPipelines.GUI_TEXTURED, it, x, y, 0f, 0f, size, size, 16, 16) }
    }

    private fun selectCategoryAt(mouseX: Int, mouseY: Int): Boolean {
        if (mouseY !in 0..16) return false
        categories().forEachIndexed { index, category ->
            val x = (index * TAB_WIDTH - categoryScroll).toInt()
            if (mouseX in x until x + TAB_WIDTH) {
                if (!isCategoryAvailable(category)) return true
                selectedCategory = category.id
                selectedEntry = null
                centerCategory()
                constrainPan()
                return true
            }
        }
        return false
    }

    private fun nodeAt(mouseX: Int, mouseY: Int): ResolvedBookEntry? = visibleNodes().lastOrNull { isInsideNode(it, mouseX, mouseY) }

    private fun isInsideNode(node: ResolvedBookEntry, mouseX: Int, mouseY: Int): Boolean {
        val point = nodePoint(node)
        val w = nodeWidth(node.entry) * zoom
        val h = nodeHeight(node.entry) * zoom
        return mouseX >= point.first && mouseX < point.first + w && mouseY >= point.second && mouseY < point.second + h
    }

    private fun nodePoint(node: ResolvedBookEntry): Pair<Float, Float> =
        width / 2f + (node.position.x + panX) * zoom to GRAPH_TOP + (node.position.y + panY) * zoom

    private fun nodeCenter(node: ResolvedBookEntry): Pair<Int, Int> {
        val point = nodePoint(node)
        return (point.first + nodeWidth(node.entry) * zoom / 2f).toInt() to
            (point.second + nodeHeight(node.entry) * zoom / 2f).toInt()
    }

    private fun visibleNodes(): List<ResolvedBookEntry> =
        selectedCategory
            ?.let(ResearchCatalog.snapshot()::entriesIn)
            .orEmpty()
            .filter { isVisible(it.entry) }

    private fun isAvailable(entry: BookEntry): Boolean = isAvailable(entry, LinkedHashSet())

    private fun isAvailable(entry: BookEntry, visited: MutableSet<Identifier>): Boolean {
        if (!visited.add(entry.id)) return false
        val snapshot = ResearchCatalog.snapshot()
        val category =
            snapshot
                .layout[entry.id]
                ?.category
                ?.let(snapshot.categories::get)
                ?: return false
        if (!isCategoryAvailable(category) || !entry.dependencies.all(ClientResearchState::has) ||
            !entry.requirements.all { ClientResearchState.requirementMet(entry.id, it) }
        ) {
            return false
        }
        return when (val link = entry.link) {
            is BookEntryLink.Category -> snapshot.categories[link.category]?.let(::isCategoryAvailable) == true
            is BookEntryLink.Research -> snapshot.entries[link.research]?.let { isAvailable(it, visited) } == true
            is BookEntryLink.Page -> snapshot.entries[link.research]?.let { isAvailable(it, visited) } == true
            null -> true
        }
    }

    private fun isCategoryAvailable(category: BookCategory): Boolean =
        category.dependencies.all(ClientResearchState::has) && ResearchProgress.meetsBookLevel(currentBookType(), category.bookLevel)

    private fun currentBookType(): Identifier? = bookType?.let(::bookTypeId) ?: ClientResearchState.bookLevel()

    private fun bookTypeId(type: BookType): Identifier? =
        ECRegistries.BOOK_TYPES.getKey(type)
            ?: ECRegistries.BOOK_TYPES
                .entrySet()
                .firstOrNull { it.value == type }
                ?.key
                ?.identifier()

    private fun isVisible(entry: BookEntry): Boolean = !entry.hiddenUntilAvailable || isAvailable(entry)

    private fun openEntry(entry: BookEntry, page: Int = 0, visited: MutableSet<Identifier> = LinkedHashSet()): Boolean {
        if (!visited.add(entry.id)) return false
        when (val link = entry.link) {
            is BookEntryLink.Category -> return openCategory(link.category)
            is BookEntryLink.Research -> {
                val target = ResearchCatalog.snapshot().entries[link.research] ?: return false
                if (!isVisible(target) || !isAvailable(target)) return false
                return openEntry(target, 0, visited)
            }
            is BookEntryLink.Page -> {
                val target = ResearchCatalog.snapshot().entries[link.research] ?: return false
                if (!isVisible(target) || !isAvailable(target)) return false
                return openEntry(target, link.spread, visited)
            }
            null -> Unit
        }
        ResearchCatalog.snapshot().layout[entry.id]?.let { selectedCategory = it.category }
        selectedEntry = entry
        spreads = BookPageLayout.paginate(entry)
        spreadIndex = page.coerceIn(0, spreads.lastIndex)
        contentRevision = ClientResearchState.revision()
        bookmarks.close()
        saveViewState()
        return true
    }

    private fun openCategory(categoryId: Identifier): Boolean {
        val category = ResearchCatalog.snapshot().categories[categoryId] ?: return false
        if (!isCategoryAvailable(category)) return false
        selectedCategory = categoryId
        selectedEntry = null
        spreads = listOf(BookSpread(emptyList()))
        spreadIndex = 0
        centerCategory()
        constrainPan()
        bookmarks.close()
        saveViewState()
        return true
    }

    private fun closeEntry() {
        selectedEntry = null
        spreads = listOf(BookSpread(emptyList()))
        spreadIndex = 0
        bookmarks.close()
    }

    private fun centerCategory() {
        val first = visibleNodes().minWithOrNull(compareBy<ResolvedBookEntry> { it.position.y }.thenBy { it.position.x }) ?: return
        targetPanX = -first.position.x.toFloat() - nodeWidth(first.entry) / 2f
        targetPanY = -first.position.y.toFloat() + 48f
        constrainPan()
    }

    private fun categories(): List<BookCategory> = ResearchCatalog.snapshot()
        .categories.values
        .sortedWith(compareBy<BookCategory> { it.order }.thenBy { it.id.toString() })

    private fun categoryProgress(category: BookCategory): Component {
        val entries = ResearchCatalog.snapshot()
            .entriesIn(category.id)
            .map(ResolvedBookEntry::entry)
            .filter { it.link == null }
        val researched = entries.count { ClientResearchState.has(it.id) }
        val percent = if (entries.isEmpty()) 0 else (researched * 100.0 / entries.size).roundToInt()
        return Component
            .translatable("screen.$ModId.research_book.category.progress", percent)
            .withStyle(ChatFormatting.GRAY)
    }

    private fun hasCategoryOverflow(): Boolean = categories().size * TAB_WIDTH > width

    private fun maxCategoryScroll(): Float = (categories().size * TAB_WIDTH - width).coerceAtLeast(0).toFloat()

    private fun updateCategorySlider(mouseX: Int) {
        if (!hasCategoryOverflow()) return
        val thumbWidth = (width * (width.toFloat() / (categories().size * TAB_WIDTH))).toInt().coerceAtLeast(24)
        val ratio = ((mouseX - thumbWidth / 2f) / (width - thumbWidth).coerceAtLeast(1)).coerceIn(0f, 1f)
        targetCategoryScroll = ratio * maxCategoryScroll()
    }

    private fun BookText.component(shadow: Boolean = false): Component {
        val component = if (translated) Component.translatable(value) else Component.literal(value)
        return if (shadow) component else component.withoutShadow()
    }

    private fun nodeWidth(entry: BookEntry) = entry.frame?.width ?: 18

    private fun nodeHeight(entry: BookEntry) = entry.frame?.height ?: 18

    private fun selectedCategory(): BookCategory? = selectedCategory?.let(ResearchCatalog.snapshot().categories::get)

    private fun constrainPan() {
        if (selectedEntry != null) return
        val nodes = visibleNodes()
        if (nodes.isEmpty()) return
        val safeZoom = targetZoom.coerceIn(0.5f, 2f)
        val minX = nodes.minOf { it.position.x.toFloat() }
        val maxX = nodes.maxOf { it.position.x + nodeWidth(it.entry).toFloat() }
        val minY = nodes.minOf { it.position.y.toFloat() }
        val maxY = nodes.maxOf { it.position.y + nodeHeight(it.entry).toFloat() }
        val safeLeft = SAFE_MARGIN + bookmarks.graphSafeLeft(height)
        val safeRight = SAFE_MARGIN + bookmarks.graphSafeRight(height)
        val minPanX = (safeLeft - width / 2f) / safeZoom - maxX
        val maxPanX = (width - safeRight - width / 2f) / safeZoom - minX
        val minPanY = SAFE_MARGIN / safeZoom - maxY
        val maxPanY = (height - SAFE_MARGIN - GRAPH_TOP) / safeZoom - minY
        targetPanX = targetPanX.coerceIn(minPanX, maxPanX)
        targetPanY = targetPanY.coerceIn(minPanY, maxPanY)
    }

    private fun saveViewState() {
        val state =
            bookmarks.appendTo(
                BookViewState(
                    selectedCategory,
                    selectedEntry?.id,
                    spreadIndex,
                    targetPanX,
                    targetPanY,
                    targetZoom,
                ),
            )
        ClientResearchState.updateLocalView(state)
        ResearchNetwork.updateView(state)
    }

    private data class BookTransform(
        val x: Int,
        val y: Int,
        val scale: Float,
    )

    private data class MissingRequirements(
        val visible: List<Component>,
        val hidden: Int,
    )

    companion object {
        private const val CATEGORY_HEIGHT = 16
        private const val GRAPH_TOP = 18
        private const val TAB_WIDTH = 18
        private const val BOOK_WIDTH = 512
        private const val BOOK_HEIGHT = 256
        private const val FIRST_PAGE_X = 16
        private const val SECOND_PAGE_X = 271
        private const val PAGE_TOP = 26
        private const val PAGE_WIDTH = 225
        private const val PAGE_HEIGHT = 204
        private const val TITLE_X = FIRST_PAGE_X
        private const val TITLE_Y = 13

        private const val BOOKMARK_Y = BookBookmarkController.BOOKMARK_Y

        private const val LEFT_ARROW_X = 4
        private const val RIGHT_ARROW_X = 481
        private const val ARROW_Y = 240
        private const val COMPLETE_BUTTON_WIDTH = 140
        private const val COMPLETE_BUTTON_HEIGHT = 18
        private const val COMPLETE_BUTTON_X = (BOOK_WIDTH - COMPLETE_BUTTON_WIDTH) / 2
        private const val COMPLETE_BUTTON_Y = BOOK_HEIGHT + 2

        private const val PAN_HALF_LIFE = 0.06f
        private const val ZOOM_HALF_LIFE = 0.12f
        private const val SCROLL_HALF_LIFE = 0.12f
        private const val SAFE_MARGIN = 28f
        private const val TASK_LEVEL_SIZE = 5
        private const val TASK_LEVEL_GAP = 2
        private const val TASK_LEVEL_STEP = TASK_LEVEL_SIZE + TASK_LEVEL_GAP
        private const val NODE_TOOLTIP_DESCRIPTION_WIDTH = 160
        private const val NODE_TOOLTIP_DESCRIPTION_SCALE = 0.75f
    }
}
