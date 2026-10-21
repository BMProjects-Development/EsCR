package com.algorithmlx.ecr.api.mim

import com.algorithmlx.ecr.api.inventory.InventoryEndpoint
import com.algorithmlx.ecr.api.inventory.InventoryNetwork
import com.algorithmlx.ecr.api.mru.storage.ModifiableMRUStorage
import com.algorithmlx.ecr.api.item.BoundGemTarget
import net.minecraft.resources.Identifier
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

interface MIMManager {
    val blockEntity: BlockEntity
    val power: ModifiableMRUStorage
    fun isLinked(module: MIMModule): Boolean
    fun network(): InventoryNetwork
    fun craftingModules(): List<MIMCraftingProvider>
}

interface MIMModule : BoundGemTarget {
    val blockEntity: BlockEntity
    fun attach(manager: MIMManager): Boolean
    fun managerTick(context: MIMContext) {}
}

class MIMContext(val manager: MIMManager, val operationDue: Boolean) {
    val network: InventoryNetwork by lazy(manager::network)
}

interface MIMInventoryProvider {
    fun endpoints(): List<InventoryEndpoint>
}

interface MIMStorageProvider : MIMModule, MIMInventoryProvider

interface MIMStorageNodeProvider : MIMInventoryProvider, BoundGemTarget

interface MIMCraftingProvider : MIMModule {
    val pendingCrafts: Int
    val activity: MIMCraftingActivity? get() = null
    fun patterns(): List<ItemStack>
    fun craftingPatterns(): List<MIMCraftingPattern>
    fun craft(manager: MIMManager, template: ItemStack, times: Int): Int
    fun cancelCrafting()
    fun tasks(): List<MIMCraftingTask> = emptyList()
    fun cancelTask(id: UUID): Boolean = false
}

enum class MIMCraftingState { IDLE, PLANNING, MATERIALS, POWER, SPACE, WORKING }

data class MIMCraftingActivity(val state: MIMCraftingState, val completed: Int, val ticks: Int, val mruPerTick: Int)

class MIMCraftingTask(
    val id: UUID, target: ItemStack, val remaining: Int, val activity: MIMCraftingActivity
) {
    private val template = target.copyWithCount(1)
    val target: ItemStack get() = template.copy()
}

interface MIMTransferModule : MIMModule {
    val transferOrder: Int get() = 0
    fun transfer(network: InventoryNetwork)
}

interface MIMTerminal : MIMModule {
    val canWithdraw: Boolean
    fun parent(): MIMManager?
    fun deposit(stack: ItemStack): ItemStack
    fun withdraw(template: ItemStack, amount: Int): ItemStack
}

interface MIMExpandableContainer : Container {
    val availableSlots: Int
    fun insertConfiguration(stack: ItemStack): ItemStack
}

object MIMModules {
    private val adapters = linkedMapOf<Identifier, (BlockEntity) -> MIMModule?>()
    private val inventories = linkedMapOf<Identifier, (BlockEntity) -> MIMStorageNodeProvider?>()

    @JvmStatic
    fun register(id: Identifier, adapter: (BlockEntity) -> MIMModule?) {
        check(adapters.putIfAbsent(id, adapter) == null) { "Duplicate MIM module adapter: $id" }
        BoundGemTarget.register(id.withPrefix("mim/module/")) { adapter(it) != null }
    }

    @JvmStatic
    fun registerInventory(id: Identifier, adapter: (BlockEntity) -> MIMStorageNodeProvider?) {
        check(inventories.putIfAbsent(id, adapter) == null) { "Duplicate MIM inventory adapter: $id" }
        BoundGemTarget.register(id.withPrefix("mim/inventory/")) { adapter(it) != null }
    }

    @JvmStatic
    fun resolve(target: BlockEntity): MIMModule? =
        target as? MIMModule ?: adapters.values.firstNotNullOfOrNull { it(target) }

    @JvmStatic
    fun all(target: BlockEntity): List<MIMModule> {
        val seen = Collections.newSetFromMap(IdentityHashMap<MIMModule, Boolean>())
        return (listOfNotNull(target as? MIMModule) + adapters.values.mapNotNull { it(target) }).filter(seen::add)
    }

    @JvmStatic
    fun inventory(target: BlockEntity): MIMStorageNodeProvider? =
        target as? MIMStorageNodeProvider ?: inventories.values.firstNotNullOfOrNull { it(target) }

    @JvmStatic
    fun isInternal(container: Container): Boolean = container is MIMManager || container is MIMModule ||
        container is BlockEntity && resolve(container) != null
}
