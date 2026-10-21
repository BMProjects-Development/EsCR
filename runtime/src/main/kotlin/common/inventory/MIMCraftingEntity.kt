package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.inventory.InventoryNetwork
import com.algorithmlx.ecr.api.inventory.ItemStorageIdentity
import com.algorithmlx.ecr.api.inventory.PlatformItemContainer
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.common.menu.MIMMenuKind
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.api.mim.*
import com.algorithmlx.ecr.api.item.BoundGemTooltipProvider
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.jvm.optionals.getOrNull
import java.util.UUID

class MIMCraftingEntity(pos: BlockPos, state: BlockState) : MIMModuleEntity(
    pos, state, BlockEntityTypeRegistry.mimCrafting.get(), MIMMenuKind.CRAFTING.slots
), MIMCraftingProvider, BoundGemTooltipProvider {
    override val menuKind get() = MIMMenuKind.CRAFTING
    override val previousInventorySize: Int get() = 54
    private val requests = ArrayDeque<MIMCraftingRequest>()
    private var generation: CraftingGeneration? = null
    override val activity: MIMCraftingActivity? get() = requests.firstOrNull()?.let {
        activity(it)
    }
    override val pendingCrafts: Int get() = requests.sumOf { it.remaining.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean =
        slot in 0 until containerSize && MIMFrames.provider(stack) != null

    override fun patterns(): List<ItemStack> {
        val level = level as? ServerLevel ?: return emptyList()
        val result = mutableListOf<ItemStack>()
        currentPatterns(level).forEach { pattern ->
            if (result.none { ItemStack.isSameItemSameComponents(it, pattern.output) }) result += pattern.output.copy()
        }
        return result
    }

    override fun craftingPatterns(): List<MIMCraftingPattern> = (level as? ServerLevel)?.let(::currentPatterns).orEmpty()

    override fun craft(manager: MIMManager, template: ItemStack, times: Int): Int {
        val level = level as? ServerLevel ?: return 0
        if (template.isEmpty || times <= 0 || parent() !== manager ||
            currentPatterns(level).none { ItemStack.isSameItemSameComponents(it.output, template) })
            return 0
        val config = ECConfig.current.inventoryManager
        if (times > config.maxQueuedCrafts - pendingCrafts) return 0
        val plan = MIMCraftingPlanner(manager.craftingModules().flatMap { it.craftingPatterns() }, manager.network().snapshot(),
            config.dependencyMaxNodes, config.dependencyMaxDepth, config.dependencyMaxCrafts).plan(template, times)
        if (!plan.complete) return 0
        val accepted = times
        val existing = requests.firstOrNull { ItemStack.isSameItemSameComponents(it.template, template) }
        if (existing == null) requests.addLast(MIMCraftingRequest(template, accepted))
        else existing.remaining += accepted
        setChanged()
        return accepted
    }

    override fun cancelCrafting() {
        if (requests.isEmpty()) return
        requests.clear()
        generation = null
        setChanged()
    }

    override fun tasks(): List<MIMCraftingTask> = requests.map {
        MIMCraftingTask(it.id, it.template, it.remaining, activity(it))
    }

    override fun cancelTask(id: UUID): Boolean {
        if (!requests.removeIf { it.id == id }) return false
        setChanged()
        return true
    }

    private fun activity(request: MIMCraftingRequest): MIMCraftingActivity = MIMCraftingActivity(
        request.state, request.progress?.completed ?: 0, request.progress?.work?.ticks ?: 0,
        request.progress?.work?.mruPerTick ?: 0
    )

    override fun serverTick() {
        super.serverTick()
        val level = level as? ServerLevel ?: return
        val core = parent() ?: return
        if (requests.isEmpty()) return
        val network = core.network()
        val patterns = core.craftingModules().flatMap { it.craftingPatterns() }
        val current = CraftingGeneration(core, network, patterns)
        if (!current.sameAs(generation)) {
            requests.forEach(MIMCraftingRequest::resetPlan)
            generation = current
        }
        val request = requests.first()
        if (level.gameTime < request.retryAt) {
            requests.addLast(requests.removeFirst())
            return
        }
        if (request.plan == null) {
            request.state = MIMCraftingState.PLANNING
            val config = ECConfig.current.inventoryManager
            val plan = MIMCraftingPlanner(patterns, network.snapshot(), config.dependencyMaxNodes, config.dependencyMaxDepth, config.dependencyMaxCrafts)
                .plan(request.template, 1)
            if (!plan.complete) {
                request.state = MIMCraftingState.MATERIALS
                request.retryAt = level.gameTime + config.operationTicks
                requests.addLast(requests.removeFirst())
                return
            }
            request.plan = plan
            request.executions = plan.steps.first().executions
        }
        val step = request.plan!!.steps[request.step]
        if (request.job == null) request.job = MIMCraftingJob(listOf(step.pattern), network.snapshot())
        val result = request.job!!.advance(ECConfig.current.inventoryManager.craftingStepsPerTick) { pattern, inputs, outputs ->
            execute(core, network, request, pattern, inputs, outputs, level.gameTime)
        }
        when (result) {
            MIMCraftingJob.Status.CRAFTED -> {
                request.job = null
                request.progress = null
                request.executions--
                if (request.executions == 0) {
                    request.step++
                    if (request.step == request.plan!!.steps.size) {
                        request.remaining--
                        if (request.remaining == 0) requests.removeFirst()
                        else request.resetPlan()
                    } else request.executions = request.plan!!.steps[request.step].executions
                }
                setChanged()
            }
            MIMCraftingJob.Status.EXHAUSTED -> {
                request.resetPlan()
                request.state = MIMCraftingState.MATERIALS
                requests.addLast(requests.removeFirst())
            }
            MIMCraftingJob.Status.SEARCHING -> Unit
        }
    }

    private fun execute(
        core: MIMManager, network: InventoryNetwork, request: MIMCraftingRequest, pattern: MIMCraftingPattern,
        inputs: List<ItemStack>, outputs: List<ItemStack>, tick: Long
    ): MIMCraftingJob.Decision {
        if (!network.canTransact(inputs, outputs, pattern.isolatedOutputs)) {
            if (!network.canTransact(inputs, emptyList())) return MIMCraftingJob.Decision.REJECTED
            request.state = MIMCraftingState.SPACE
            return MIMCraftingJob.Decision.WAITING
        }
        val cost = MIMCraftingCosts.work(pattern)
        val progress = request.progress?.takeIf { it.matches(inputs, outputs, cost) }
            ?: MIMCraftingProgress(inputs, outputs, cost).also { request.progress = it }
        if (!progress.complete) {
            if (progress.advance(core.power, tick)) {
                request.state = MIMCraftingState.WORKING
                setChanged()
            } else if (core.power.mru == 0 && cost.mruPerTick > 0) request.state = MIMCraftingState.POWER
        }
        if (!progress.complete) return MIMCraftingJob.Decision.WAITING
        return if (network.transact(inputs, outputs, pattern.isolatedOutputs, pattern.prepareOutputs)) MIMCraftingJob.Decision.CRAFTED
        else MIMCraftingJob.Decision.WAITING
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        output.store("crafting_queue", MIMCraftingRequest.CODEC.listOf(), requests.toList())
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries)
        saveDeviceProperties(output)
        return output.buildResult()
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        requests.clear()
        requests.addAll(input.read("crafting_queue", MIMCraftingRequest.CODEC.listOf()).getOrNull() ?: emptyList())
        generation = null
    }

    private fun currentPatterns(level: ServerLevel): List<MIMCraftingPattern> =
        (0 until containerSize).flatMap { MIMFrames.patterns(level, getItem(it)) }

    override fun boundGemTooltip(): List<Component> = listOf(
        Component.translatable("tooltip.escr.mim.crafting"),
        Component.translatable("tooltip.escr.mim.frames", (0 until containerSize).count { !getItem(it).isEmpty }, containerSize).withStyle(ChatFormatting.GRAY)
    )

    private class CraftingGeneration(
        core: MIMManager, network: InventoryNetwork, private val patterns: List<MIMCraftingPattern>
    ) {
        private val coreIdentity = core.blockEntity
        private val topology = network.endpoints.map { endpoint ->
            EndpointState(endpoint.identities.map { (it as? PlatformItemContainer)?.storageIdentity ?: ItemStorageIdentity(it) }.toSet(), endpoint.side?.ordinal, endpoint.slots().toList())
        }

        fun sameAs(other: CraftingGeneration?): Boolean = other != null &&
            coreIdentity === other.coreIdentity && topology == other.topology &&
            patterns.size == other.patterns.size && patterns.indices.all {
                val first = patterns[it]
                val second = other.patterns[it]
                first.sameAs(second)
            }

        private data class EndpointState(val identities: Set<Any>, val side: Int?, val slots: List<Int>)
    }
}
