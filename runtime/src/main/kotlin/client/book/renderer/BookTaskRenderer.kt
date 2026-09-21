package com.algorithmlx.ecr.client.book.renderer

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.client.research.BookElementRenderContext
import com.algorithmlx.ecr.api.research.*
import com.algorithmlx.ecr.api.research.content.BookEntry
import com.algorithmlx.ecr.api.research.content.BookIcon
import com.algorithmlx.ecr.api.research.content.BookText
import com.algorithmlx.ecr.api.research.content.ResearchTaskDefinition
import com.algorithmlx.ecr.api.research.content.TaskListBookElement
import com.mojang.blaze3d.platform.cursor.CursorTypes
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object BookTaskRenderer {
    private val checkmark = Identifier.withDefaultNamespace("icon/checkmark")

    fun render(
        context: BookElementRenderContext,
        element: TaskListBookElement
    ) {
        val entry = ResearchCatalog.snapshot().entries[element.research] ?: return
        val level = entry.taskLevels.getOrNull(element.level) ?: return
        val offset = entry.taskLevels.take(element.level).sumOf { it.tasks.size }
        val progress = ClientResearchState.taskProgress(entry.id)
        val levelComplete = ClientResearchState.has(entry.id) || element.level < ClientResearchState.completedTaskLevels(entry.id)
        var visibleIndex = 0
        level.tasks.forEachIndexed { index, definition ->
            if (definition.hidden) return@forEachIndexed
            val x = context.x + visibleIndex % TASKS_PER_ROW * TASK_CELL_SIZE
            val y = context.y + visibleIndex / TASKS_PER_ROW * TASK_CELL_SIZE
            val sourceProgress = progress.getOrNull(offset + index) ?: ResearchTaskProgress(0, 1)
            val taskProgress = if (levelComplete) sourceProgress.copy(current = sourceProgress.required) else sourceProgress
            renderTask(context, entry, definition, taskProgress, x, y)
            visibleIndex++
        }
    }

    private fun renderTask(
        context: BookElementRenderContext,
        entry: BookEntry,
        definition: ResearchTaskDefinition,
        progress: ResearchTaskProgress,
        x: Int,
        y: Int
    ) {
        val customIcon = entry.taskIcons[definition.id].takeUnless { definition.task is ItemResearchTask }
        val stack = taskStack(definition.task, customIcon)
        if (stack != null) {
            context.graphics.item(stack, x, y)
            if (stack.count > 1) context.graphics.itemDecorations(Minecraft.getInstance().font, stack, x, y)
        } else {
            renderIcon(context, customIcon, x, y)
        }

        if (progress.complete) {
            context.graphics.blitSprite(RenderPipelines.GUI_TEXTURED, checkmark, x + 8, y + 8, 8, 8)
        }

        val mouseX = context.screenX + ((context.mouseX - context.x) * context.scale).toInt()
        val mouseY = context.screenY + ((context.mouseY - context.y) * context.scale).toInt()
        val left = context.screenX + ((x - context.x) * context.scale).toInt()
        val top = context.screenY + ((y - context.y) * context.scale).toInt()
        val size = (16 * context.scale).toInt().coerceAtLeast(1)
        if (mouseX in left until left + size && mouseY in top until top + size) {
            context.graphics.requestCursor(CursorTypes.POINTING_HAND)
            context.graphics.setComponentTooltipForNextFrame(
                Minecraft.getInstance().font,
                tooltip(definition, stack, progress),
                mouseX,
                mouseY
            )
        }
    }

    private fun taskStack(
        task: ResearchTask,
        customIcon: BookIcon?
    ): ItemStack? =
        when {
            task is ItemResearchTask -> {
                Minecraft
                    .getInstance()
                    .level
                    ?.registryAccess()
                    ?.let { task.createStack(it) }
            }

            customIcon != null -> {
                null
            }

            task is CraftingResearchTask -> {
                ItemStack(Items.CRAFTING_TABLE)
            }

            task is ExperienceResearchTask -> {
                ItemStack(Items.EXPERIENCE_BOTTLE)
            }

            else -> {
                null
            }
        }

    private fun tooltip(
        definition: ResearchTaskDefinition,
        stack: ItemStack?,
        progress: ResearchTaskProgress
    ): List<Component> =
        buildList {
            add(taskTitle(definition, stack))
            add(Component.literal("${progress.current}/${progress.required}"))
        }

    private fun taskTitle(
        definition: ResearchTaskDefinition,
        stack: ItemStack?
    ): Component =
        definition.title?.component() ?: when (val task = definition.task) {
            is CraftingResearchTask -> {
                Component.literal(task.recipe.toString())
            }

            is ExperienceResearchTask -> {
                Component.translatable(
                    if (task.levels) "screen.$ModId.research_book.experience.levels" else "screen.$ModId.research_book.experience"
                )
            }

            else -> {
                stack?.hoverName ?: Component.literal("screen.$ModId.research_book.task")
            }
        }

    private fun renderIcon(
        context: BookElementRenderContext,
        icon: BookIcon?,
        x: Int,
        y: Int
    ) {
        icon ?: return
        icon.item?.let { id ->
            BuiltInRegistries.ITEM.getOptional(id).ifPresent { context.graphics.item(ItemStack(it), x, y) }
        }
        icon.texture?.let { texture ->
            context.graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 16, 16, 16, 16)
        }
    }

    private const val TASK_CELL_SIZE = 20
    private const val TASKS_PER_ROW = 11
}

private fun BookText.component(): Component = if (translated) Component.translatable(value) else Component.literal(value)
