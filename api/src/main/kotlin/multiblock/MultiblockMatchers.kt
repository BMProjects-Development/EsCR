package com.algorithmlx.ecr.api.multiblock

import com.algorithmlx.ecr.api.init.MultiblockMatcherTypes
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import java.util.Optional

data class TagMultiblockMatcher(
    val tag: TagKey<Block>
): MultiblockMatcher {
    override val type: MultiblockMatcherType<*> get() = MultiblockMatcherTypes.tag

    override fun matches(block: BlockState): Boolean = block.`is`(tag)

    override fun default(): BlockState = BuiltInRegistries.BLOCK.getTagOrEmpty(tag)
        .firstOrNull()?.value()?.defaultBlockState() ?: Blocks.AIR.defaultBlockState()

    companion object {
        private val tagCodec = Identifier.CODEC.xmap(
            { id -> TagKey.create(Registries.BLOCK, id) },
            { tag -> tag.location }
        )

        @JvmField
        val CODEC: MapCodec<TagMultiblockMatcher> = RecordCodecBuilder.mapCodec {
            it.group(
                tagCodec.fieldOf("tag")
                    .forGetter(TagMultiblockMatcher::tag)
            ).apply(it, ::TagMultiblockMatcher)
        }
    }
}

data class ListMultiblockMatcher(
    val matchers: List<MultiblockMatcher>,
    val defaultState: BlockState? = null
): MultiblockMatcher {
    init {
        require(matchers.isNotEmpty()) { "List multiblock matcher must contain at least one matcher" }
        require(defaultState == null || matches(defaultState)) { "Default state must match at least one list matcher" }
    }

    override val type: MultiblockMatcherType<*> get() = MultiblockMatcherTypes.list

    override fun matches(block: BlockState): Boolean =
        matchers.any { matcher -> matcher.matches(block) }

    override fun default(): BlockState = defaultState ?: matchers.first().default()

    companion object {
        @JvmField
        val CODEC: MapCodec<ListMultiblockMatcher> = RecordCodecBuilder.mapCodec {
            it.group(
                MultiblockMatcher.CODEC.listOf().fieldOf("matchers")
                    .forGetter(ListMultiblockMatcher::matchers),
                BlockState.CODEC.optionalFieldOf("default_state")
                    .forGetter { matcher -> Optional.ofNullable(matcher.defaultState) }
            ).apply(it) { matchers, defaultState ->
                ListMultiblockMatcher(matchers, defaultState.orElse(null))
            }
        }
    }
}

data class BlockMultiblockMatcher(
    val state: BlockState,
    val ignoreTag: Boolean = false,
    override val required: Boolean = true
): MultiblockMatcher {
    override val type: MultiblockMatcherType<*> get() = MultiblockMatcherTypes.block

    override fun matches(block: BlockState): Boolean =
        if (ignoreTag) block.`is`(state.block)
        else block == state

    override fun default(): BlockState = state

    companion object {
        @JvmField
        val CODEC: MapCodec<BlockMultiblockMatcher> = RecordCodecBuilder.mapCodec {
            it.group(
                BlockState.CODEC.fieldOf("state").forGetter(BlockMultiblockMatcher::state),
                Codec.BOOL.optionalFieldOf("ignore_tag", false)
                    .forGetter(BlockMultiblockMatcher::ignoreTag),
                Codec.BOOL.optionalFieldOf("required", true)
                    .forGetter(BlockMultiblockMatcher::required)
            ).apply(it, ::BlockMultiblockMatcher)
        }
    }
}
