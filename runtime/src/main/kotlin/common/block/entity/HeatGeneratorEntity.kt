package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.block.entity.SynchronizedContainerBlockEntity
import com.algorithmlx.ecr.api.block.structurePosition
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.balance.MutableMRUBalance
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import com.algorithmlx.ecr.api.mru.storage.MRUStorageContainer
import com.algorithmlx.ecr.common.block.HeatGenerator
import com.algorithmlx.ecr.common.init.config.ECConfig
import com.algorithmlx.ecr.common.init.config.UltraHeatWorldEffectsConfig
import com.algorithmlx.ecr.common.menu.HeatGeneratorMenu
import com.algorithmlx.ecr.common.temperature.TemperatureUnit
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.ItemRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.ContainerHelper
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseFireBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

class HeatGeneratorEntity(worldPosition: BlockPos, blockState: BlockState): SynchronizedContainerBlockEntity(BlockEntityTypeRegistry.instance.heatGenerator, worldPosition, blockState), MRUDevice, WorldlyContainer {
    private var upgraded = blockState.getValue(HeatGenerator.IS_UPGRADED)
    private var mutableMRUStorage = createMRUStorage(capacityFor(upgraded))
    private var items: NonNullList<ItemStack> = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY)
    private var worldEffectTicks = 0
    private var fireEffectTicks = 0
    private var slagPending = false
    private var balanceInitialized = false

    var temperatureCelsius: Double = 0.0
        private set
    var temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS
        private set
    var burnTimeRemaining: Double = 0.0
        private set
    var maxBurnTime: Int = 0
        private set
    var currentGeneration: Int = 0
        private set

    private val containerData: ContainerData = object : ContainerData {
        override fun get(index: Int): Int = when (index) {
            DATA_BURN_TIME -> ceil(this@HeatGeneratorEntity.burnTimeRemaining).toInt().coerceAtLeast(0)
            DATA_MAX_BURN_TIME -> this@HeatGeneratorEntity.maxBurnTime
            DATA_TEMPERATURE_LOW -> HeatGeneratorLogic.dataLow(scaledInt(this@HeatGeneratorEntity.temperatureCelsius, TEMPERATURE_SCALE))
            DATA_TEMPERATURE_HIGH -> HeatGeneratorLogic.dataHigh(scaledInt(this@HeatGeneratorEntity.temperatureCelsius, TEMPERATURE_SCALE))
            DATA_GENERATION -> this@HeatGeneratorEntity.currentGeneration
            DATA_TEMPERATURE_UNIT -> this@HeatGeneratorEntity.temperatureUnit.ordinal
            DATA_MRU_LOW -> HeatGeneratorLogic.dataLow(this@HeatGeneratorEntity.mruStorage.mru)
            DATA_MRU_HIGH -> HeatGeneratorLogic.dataHigh(this@HeatGeneratorEntity.mruStorage.mru)
            DATA_MRU_CAPACITY_LOW -> HeatGeneratorLogic.dataLow(this@HeatGeneratorEntity.mruStorage.mruCapacity)
            DATA_MRU_CAPACITY_HIGH -> HeatGeneratorLogic.dataHigh(this@HeatGeneratorEntity.mruStorage.mruCapacity)
            DATA_UPGRADED -> if (this@HeatGeneratorEntity.isUpgraded) 1 else 0
            else -> 0
        }

        override fun set(index: Int, value: Int) = Unit

        override fun getCount(): Int = DATA_COUNT
    }

    var isUpgraded: Boolean
        get() = this.upgraded
        set(value) {
            this.upgraded = value
            resizeMRUStorage()

            val state = this.blockState.setValue(HeatGenerator.IS_UPGRADED, value)
            if (state != this.blockState) this.level?.setBlock(this.blockPos, state, Block.UPDATE_ALL)
            this.setChanged()
        }

    override fun saveAdditional(output: ValueOutput) {
        output.putBoolean(IS_UPGRADED_TAG, this.isUpgraded)
        output.putDouble(TEMPERATURE_TAG, this.temperatureCelsius)
        output.putString(TEMPERATURE_UNIT_TAG, this.temperatureUnit.serializedName)
        output.putDouble(BURN_TIME_TAG, this.burnTimeRemaining)
        output.putInt(MAX_BURN_TIME_TAG, this.maxBurnTime)
        output.putInt(WORLD_EFFECT_TICKS_TAG, this.worldEffectTicks)
        output.putInt(FIRE_EFFECT_TICKS_TAG, this.fireEffectTicks)
        output.putBoolean(SLAG_PENDING_TAG, this.slagPending)
        output.putBoolean(BALANCE_INITIALIZED_TAG, this.balanceInitialized)
        ContainerHelper.saveAllItems(output, this.items)
        this.saveMRUData(output)
        super.saveAdditional(output)
    }

    override fun loadAdditional(input: ValueInput) {
        this.upgraded = input.getBooleanOr(IS_UPGRADED_TAG, this.blockState.getValue(HeatGenerator.IS_UPGRADED))
        resizeMRUStorage()
        this.temperatureCelsius = input.getDoubleOr(TEMPERATURE_TAG, 0.0).finiteNonNegative()
        this.temperatureUnit = TemperatureUnit.fromSerializedName(input.getStringOr(TEMPERATURE_UNIT_TAG, "celsius"))
        this.burnTimeRemaining = input.getDoubleOr(BURN_TIME_TAG, 0.0).finiteNonNegative()
        this.maxBurnTime = input.getIntOr(MAX_BURN_TIME_TAG, 0).coerceAtLeast(0)
        this.worldEffectTicks = input.getIntOr(WORLD_EFFECT_TICKS_TAG, 0).coerceAtLeast(0)
        this.fireEffectTicks = input.getIntOr(FIRE_EFFECT_TICKS_TAG, 0).coerceAtLeast(0)
        this.slagPending = input.getBooleanOr(SLAG_PENDING_TAG, false)
        this.balanceInitialized = input.getBooleanOr(BALANCE_INITIALIZED_TAG, false)
        ContainerHelper.loadAllItems(input, this.items)
        this.loadMRUData(input)
        super.loadAdditional(input)
    }

    override fun getDefaultName(): Component = Component.translatable(if (this.isUpgraded) "block.$ModId.ultra_heat_generator" else "block.$ModId.heat_generator")

    override fun getItems(): NonNullList<ItemStack> = this.items

    override fun setItems(items: NonNullList<ItemStack>) {
        this.items = items
    }

    override fun createMenu(containerId: Int, inventory: Inventory): AbstractContainerMenu = HeatGeneratorMenu(containerId, inventory, this, this, ContainerLevelAccess.create(requireNotNull(this.level), this.blockPos), this.containerData)

    override fun getContainerSize(): Int = this.items.size

    override fun getSlotsForFace(direction: Direction): IntArray = if (direction == Direction.DOWN) BOTTOM_SLOTS else INPUT_SLOTS

    override fun canPlaceItemThroughFace(slot: Int, itemStack: ItemStack, direction: Direction?): Boolean = canPlaceItem(slot, itemStack)

    override fun canTakeItemThroughFace(slot: Int, itemStack: ItemStack, direction: Direction): Boolean =
        slot == OUTPUT_SLOT || slot == FUEL_SLOT && !itemStack.has(DataComponents.COOKING_FUEL)

    override fun canPlaceItem(slot: Int, itemStack: ItemStack): Boolean =
        slot == FUEL_SLOT && itemStack.has(DataComponents.COOKING_FUEL)

    override val mruStorage: IOMRUStorage
        get() = this.mutableMRUStorage
    override val balance: MutableMRUBalance = MRUBalanceContainer { setChanged() }
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.TRANSLATOR

    fun cycleTemperatureUnit(): TemperatureUnit {
        this.temperatureUnit = this.temperatureUnit.next()
        this.setChanged()
        return this.temperatureUnit
    }

    private fun resizeMRUStorage() {
        val capacity = capacityFor(this.upgraded)
        if (this.mutableMRUStorage.mruCapacity == capacity) return
        val amount = this.mutableMRUStorage.mru
        this.mutableMRUStorage = createMRUStorage(capacity)
        this.mutableMRUStorage.set(amount)
    }

    private fun createMRUStorage(capacity: Int): MRUStorageContainer = MRUStorageContainer(capacity, MRUTypeRegistry.instance.radiationUnit) { this.setChanged() }

    private fun synchronizeUpgradeBlockState(level: Level) {
        val state = this.blockState.setValue(HeatGenerator.IS_UPGRADED, this.upgraded)
        if (state != this.blockState) level.setBlock(this.blockPos, state, Block.UPDATE_ALL)
    }

    private fun tryConsumeFuel(level: ServerLevel): Boolean {
        val fuel = this.items[FUEL_SLOT]
        val cookingFuel = fuel.get(DataComponents.COOKING_FUEL) ?: return false
        val burnDuration = cookingFuel.burnTime().get(getLootContext(level), 0)
        if (burnDuration <= 0 || !canAcceptSlag()) return false

        val fuelItem = fuel.item
        fuel.shrink(1)
        if (fuel.isEmpty) this.items[FUEL_SLOT] = fuelItem.craftingRemainder?.create() ?: ItemStack.EMPTY

        this.burnTimeRemaining = burnDuration.toDouble()
        this.maxBurnTime = burnDuration
        this.setChanged()
        return true
    }

    private fun canAcceptSlag(): Boolean {
        val output = this.items[OUTPUT_SLOT]
        if (output.isEmpty) return true
        return output.`is`(ItemRegistry.instance.magicalSlag) && output.count < output.maxStackSize
    }

    private fun outputSlag(): Boolean {
        if (!canAcceptSlag()) return false
        val output = this.items[OUTPUT_SLOT]
        if (output.isEmpty) this.items[OUTPUT_SLOT] = ItemStack(ItemRegistry.instance.magicalSlag)
        else output.grow(1)
        this.slagPending = false
        this.setChanged()
        return true
    }

    private fun consumeBurnTime(amount: Double) {
        if (this.burnTimeRemaining <= 0.0) return
        this.burnTimeRemaining = (this.burnTimeRemaining - amount).coerceAtLeast(0.0)
        if (this.burnTimeRemaining <= 0.0 && !outputSlag()) this.slagPending = true
    }

    private fun generate(amount: Int) {
        if (amount <= 0 || this.mruStorage.isFilled) return
        this.mruStorage.insert(amount)
    }

    private fun initializeBalance(level: Level) {
        if (this.balanceInitialized) return

        val configuredBalance = if (this.isUpgraded) -1.0 else ECConfig.current.heatGenerator.defaultBalance
        val generatedBalance = if (configuredBalance == -1.0) level.random.nextFloat().toDouble() * 2.0 else configuredBalance
        this.balance.setBalance(generatedBalance, generatedBalance)
        this.balanceInitialized = true
        this.setChanged()
    }

    private fun tickWorldEffects(level: Level) {
        val config = ECConfig.current.heatGenerator.ultra.worldEffects
        if (!config.enabled) {
            this.worldEffectTicks = 0
            this.fireEffectTicks = 0
            return
        }

        val instantWater = applyInstantWaterTransitions(level, config)
        ignitePlayers(level, config)
        tickSurfaceFire(level, config)

        val minimumTemperature = minOf(config.temperatureCelsius, config.blockTransitions.values.minOfOrNull { it.temperatureCelsius } ?: config.temperatureCelsius)
        if (this.temperatureCelsius < minimumTemperature) {
            this.worldEffectTicks = 0
            return
        }

        if (instantWater) {
            this.worldEffectTicks = 0
            return
        }

        val intervalTick = HeatGeneratorLogic.advanceInterval(this.worldEffectTicks, config.intervalTicks)
        this.worldEffectTicks = intervalTick.nextElapsedTicks
        if (!intervalTick.triggered) return

        val candidates = configuredTransitions(level, config) + defaultTransitions(level, config)
        if (candidates.isEmpty()) return
        applyTransition(level, candidates[level.random.nextInt(candidates.size)])
    }

    private fun applyInstantWaterTransitions(level: Level, config: UltraHeatWorldEffectsConfig): Boolean {
        if (this.temperatureCelsius <= config.instantTemperatureCelsius) return false
        val candidates = configuredTransitions(level, config, true)
        candidates.forEach { applyTransition(level, it) }
        return candidates.isNotEmpty()
    }

    private fun configuredTransitions(level: Level, config: UltraHeatWorldEffectsConfig, waterOnly: Boolean = false): List<HeatTransition> {
        val candidates = mutableListOf<HeatTransition>()
        for (x in -config.radius..config.radius) {
            for (y in -config.transitionVerticalRadius..config.transitionVerticalRadius) {
                for (z in -config.radius..config.radius) {
                    if (x == 0 && y == 0 && z == 0) continue
                    val target = this.blockPos.offset(x, y, z)
                    if (!level.isLoaded(target)) continue
                    val state = level.getBlockState(target)
                    if (state.block is BaseFireBlock) continue
                    if (waterOnly && !state.`is`(Blocks.WATER)) continue
                    val transition = config.blockTransitionFor(BuiltInRegistries.BLOCK.getKey(state.block).toString()) ?: continue
                    if (this.temperatureCelsius <= transition.temperatureCelsius) continue
                    val replacement = resolveBlockState(transition.target) ?: continue
                    if (state.`is`(replacement.block)) continue
                    candidates += HeatTransition(target, replacement, transition.coolingCelsius)
                }
            }
        }
        return candidates
    }

    private fun defaultTransitions(level: Level, config: UltraHeatWorldEffectsConfig): List<HeatTransition> {
        if (this.temperatureCelsius < config.temperatureCelsius) return emptyList()
        val defaultReplacement = resolveBlockState(config.defaultTransition)
        val flammableReplacement = resolveBlockState(config.flammableTransition)
        if (defaultReplacement == null && flammableReplacement == null) return emptyList()
        val candidates = mutableListOf<HeatTransition>()
        for (x in -config.radius..config.radius) {
            for (y in -config.transitionVerticalRadius..config.transitionVerticalRadius) {
                for (z in -config.radius..config.radius) {
                    if (x == 0 && y == 0 && z == 0) continue
                    val target = this.blockPos.offset(x, y, z)
                    if (!level.isLoaded(target)) continue
                    val state = level.getBlockState(target)
                    val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()
                    if (config.blockTransitionFor(id) != null || state.`is`(Blocks.LAVA) || state.`is`(Blocks.OBSIDIAN) || !canOverheat(level, target, state)) continue
                    val replacementId = HeatGeneratorLogic.unconfiguredTransitionTarget(state.block is BaseFireBlock, state.ignitedByLava(), config.flammableTransition, config.defaultTransition) ?: continue
                    val replacement = if (replacementId == config.flammableTransition) flammableReplacement else defaultReplacement
                    if (replacement == null || state.`is`(replacement.block)) continue
                    candidates += HeatTransition(target, replacement, 0.0)
                }
            }
        }
        return candidates
    }

    private fun applyTransition(level: Level, transition: HeatTransition) {
        if (!level.setBlock(transition.pos, transition.state, Block.UPDATE_ALL)) return
        this.temperatureCelsius = (this.temperatureCelsius - transition.coolingCelsius).coerceAtLeast(0.0)
    }

    private fun tickSurfaceFire(level: Level, config: UltraHeatWorldEffectsConfig) {
        val fire = config.surfaceFire
        if (this.temperatureCelsius <= fire.temperatureCelsius) {
            this.fireEffectTicks = 0
            return
        }

        val intervalTick = HeatGeneratorLogic.advanceInterval(this.fireEffectTicks, fire.intervalTicks)
        this.fireEffectTicks = intervalTick.nextElapsedTicks
        if (!intervalTick.triggered) return

        val candidates = mutableListOf<BlockPos>()
        for (x in HeatGeneratorLogic.centeredRange(fire.area)) {
            for (z in HeatGeneratorLogic.centeredRange(fire.area)) {
                if (x == 0 && z == 0) continue
                findFireTarget(level, x, z, fire.verticalRadius)?.let(candidates::add)
            }
        }
        if (candidates.isEmpty()) return
        val target = candidates[level.random.nextInt(candidates.size)]
        level.setBlock(target, BaseFireBlock.getState(level, target), Block.UPDATE_ALL)
    }

    private fun findFireTarget(level: Level, x: Int, z: Int, verticalRadius: Int): BlockPos? {
        val worldX = this.blockPos.x + x
        val worldZ = this.blockPos.z + z
        for (yOffset in HeatGeneratorLogic.nearestVerticalOffsets(verticalRadius)) {
            val target = BlockPos(worldX, this.blockPos.y + yOffset, worldZ)
            val support = target.below()
            if (!level.isInWorldBounds(target) || !level.isInWorldBounds(support) || !level.isLoaded(target) || !level.isLoaded(support)) continue
            if (!level.getBlockState(target).isAir) continue
            val supportState = level.getBlockState(support)
            if (supportState.isAir || !supportState.fluidState.isEmpty) continue
            if (BaseFireBlock.getState(level, target).canSurvive(level, target)) return target
        }
        return null
    }

    private fun ignitePlayers(level: Level, config: UltraHeatWorldEffectsConfig) {
        if (level.gameTime % config.playerIgniteIntervalTicks != 0L) return
        val area = config.playerIgnitionArea(this.temperatureCelsius) ?: return
        val halfArea = area / 2.0
        val centerX = this.blockPos.x + 0.5
        val centerY = this.blockPos.y + 0.5
        val centerZ = this.blockPos.z + 0.5
        level.players().asSequence().filter { it.isAlive && !it.isCreative && !it.isSpectator && !it.hasEffect(MobEffects.FIRE_RESISTANCE) }.filter { abs(it.x - centerX) <= halfArea && abs(it.y - centerY) <= config.playerVerticalRange && abs(it.z - centerZ) <= halfArea }.forEach { it.igniteForSeconds(config.playerFireSeconds.toFloat()) }
    }

    private fun resolveBlockState(id: String): BlockState? = Identifier.tryParse(id)?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }?.defaultBlockState()

    companion object {
        const val IS_UPGRADED_TAG = "is_upgraded"
        const val DATA_COUNT = 11
        const val DATA_BURN_TIME = 0
        const val DATA_MAX_BURN_TIME = 1
        const val DATA_TEMPERATURE_LOW = 2
        const val DATA_TEMPERATURE_HIGH = 3
        const val DATA_GENERATION = 4
        const val DATA_TEMPERATURE_UNIT = 5
        const val DATA_MRU_LOW = 6
        const val DATA_MRU_HIGH = 7
        const val DATA_MRU_CAPACITY_LOW = 8
        const val DATA_MRU_CAPACITY_HIGH = 9
        const val DATA_UPGRADED = 10
        const val TEMPERATURE_SCALE = 10.0

        const val FUEL_SLOT = 0
        const val OUTPUT_SLOT = 1
        private const val SLOT_COUNT = 2
        private val INPUT_SLOTS = intArrayOf(FUEL_SLOT)
        private val BOTTOM_SLOTS = intArrayOf(OUTPUT_SLOT, FUEL_SLOT)

        private const val TEMPERATURE_TAG = "temperature_celsius"
        private const val TEMPERATURE_UNIT_TAG = "temperature_unit"
        private const val BURN_TIME_TAG = "burn_time"
        private const val MAX_BURN_TIME_TAG = "max_burn_time"
        private const val WORLD_EFFECT_TICKS_TAG = "world_effect_ticks"
        private const val FIRE_EFFECT_TICKS_TAG = "fire_effect_ticks"
        private const val SLAG_PENDING_TAG = "slag_pending"
        private const val BALANCE_INITIALIZED_TAG = "balance_initialized"

        private val CATALYST_POSITIONS = structurePosition {
            pos(-2, 0, 0)
            pos(2, 0, 0)
            pos(0, 0, -2)
            pos(0, 0, 2)
        }

        @JvmStatic
        fun onTick(level: Level, pos: BlockPos, be: HeatGeneratorEntity) {
            val serverLevel = level as? ServerLevel ?: return
            be.initializeBalance(level)
            be.synchronizeUpgradeBlockState(level)
            be.resizeMRUStorage()
            if (be.isUpgraded) be.temperatureCelsius = be.temperatureCelsius.coerceAtMost(ECConfig.current.heatGenerator.ultra.maximumTemperatureCelsius)

            if (be.slagPending && !be.outputSlag()) {
                be.stopGenerating(level)
                return
            }

            if (level.hasNeighborSignal(pos)) {
                be.stopGenerating(level)
                return
            }

            if (be.mruStorage.isFilled) {
                be.stopGenerating(level)
                return
            }

            if (be.burnTimeRemaining <= 0.0 && be.canAcceptSlag()) be.tryConsumeFuel(serverLevel)
            if (be.burnTimeRemaining <= 0.0) {
                be.stopGenerating(level)
                return
            }

            if (be.isUpgraded) {
                val config = ECConfig.current.heatGenerator.ultra
                val tick = HeatGeneratorLogic.ultraTick(be.temperatureCelsius, config.burnSpeed, config.heatingSpeed, config.generationTemperatureCelsius, config.heatingSlowdownTemperatureCelsius, config.maximumTemperatureCelsius)
                be.temperatureCelsius = tick.nextTemperatureCelsius
                be.currentGeneration = tick.generation
                be.generate(tick.generation)
                be.consumeBurnTime(tick.fuelCost)
                be.tickWorldEffects(level)
            } else {
                be.currentGeneration = normalGeneration(level, pos)
                be.generate(be.currentGeneration)
                be.consumeBurnTime(1.0)
                be.worldEffectTicks = 0
                be.fireEffectTicks = 0
            }

            be.setChanged()
        }

        private fun normalGeneration(level: Level, pos: BlockPos): Int {
            val config = ECConfig.current.heatGenerator
            val catalysts = CATALYST_POSITIONS[pos].map(level::getBlockState)
            val firstState = catalysts.firstOrNull() ?: return config.defaultGeneration
            if (!catalysts.all { it.`is`(firstState.block) }) return config.defaultGeneration

            val blockId = BuiltInRegistries.BLOCK.getKey(firstState.block).toString()
            return config.heatBlockGeneration[blockId] ?: config.defaultGeneration
        }

        private fun canOverheat(level: Level, pos: BlockPos, state: BlockState): Boolean = state.block !is BaseFireBlock && !state.isAir && state.fluidState.isEmpty && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0.0F

        private fun capacityFor(upgraded: Boolean): Int = ECConfig.current.heatGenerator.let { if (upgraded) it.ultraCapacity else it.capacity }

        private fun scaledInt(value: Double, scale: Double): Int = (value * scale).coerceIn(0.0, Int.MAX_VALUE.toDouble()).roundToInt()

        private fun Double.finiteNonNegative(): Double = takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    }

    private fun stopGenerating(level: Level) {
        val previousTemperature = this.temperatureCelsius
        val previousWorldEffectTicks = this.worldEffectTicks
        val previousFireEffectTicks = this.fireEffectTicks
        if (this.isUpgraded) this.tickWorldEffects(level) else {
            this.worldEffectTicks = 0
            this.fireEffectTicks = 0
        }
        val nextTemperature = if (this.isUpgraded) HeatGeneratorLogic.coolDown(this.temperatureCelsius, ECConfig.current.heatGenerator.ultra.coolingSpeed) else this.temperatureCelsius
        if (this.currentGeneration == 0 && previousWorldEffectTicks == this.worldEffectTicks && previousFireEffectTicks == this.fireEffectTicks && previousTemperature == nextTemperature) return
        this.temperatureCelsius = nextTemperature
        this.currentGeneration = 0
        this.setChanged()
    }

    private data class HeatTransition(val pos: BlockPos, val state: BlockState, val coolingCelsius: Double)
}
