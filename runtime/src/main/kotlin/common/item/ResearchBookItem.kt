package com.algorithmlx.ecr.common.item

import com.algorithmlx.ecr.api.item.HasSubItem
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.research.BookType
import com.algorithmlx.ecr.api.research.ResearchProgress
import com.algorithmlx.ecr.registry.BookTypeRegistry
import com.algorithmlx.ecr.registry.DataComponentRegistry
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class ResearchBookItem(
    properties: Properties,
) : Item(properties),
    HasSubItem {
    override fun use(
        level: Level,
        player: Player,
        hand: InteractionHand,
    ): InteractionResult {
        val stack = player.getItemInHand(hand)
        val basic = BookTypeRegistry.basic.get()
        val basicKey = ECRegistries.BOOK_TYPES.getResourceKey(basic).get()
        var bookTypeKey =
            stack.getOrDefault(
                DataComponentRegistry.bookType.get(),
                basicKey,
            )

        val bookTypeOptional = ECRegistries.BOOK_TYPES.get(bookTypeKey)
        val bookType =
            if (bookTypeOptional.isPresent) {
                bookTypeOptional.get().value()
            } else {
                bookTypeKey = basicKey
                stack.set(DataComponentRegistry.bookType.get(), basicKey)
                basic
            }

        if (level.isClientSide) {
            ResearchBookHooks.open(bookType)
        } else if (player is ServerPlayer) {
            ResearchProgress.setBookLevel(player, bookTypeKey.identifier())
        }

        return InteractionResult.SUCCESS
    }

    override fun addSubItems(original: ItemStack): List<ItemStack> {
        val items = mutableListOf<ItemStack>()
        ECRegistries.BOOK_TYPES.listElements().forEach {
            items += original.copy().apply { this.set(DataComponentRegistry.bookType.get(), it.key()) }
        }

        return items
    }
}

object ResearchBookHooks {
    @JvmField var open: (BookType) -> Unit = {}
}
