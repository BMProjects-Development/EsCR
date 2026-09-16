package com.algorithmlx.ecr.client.renderer

import com.algorithmlx.ecr.common.init.ECRTags
import net.minecraft.world.entity.player.Player
import java.util.*

object MRULinkViewerAccess {
    private var cachedPlayer: UUID? = null
    private var cachedGameTime: Long = Long.MIN_VALUE
    private var cachedResult: Boolean = false

    @JvmStatic
    fun invalidate() {
        cachedPlayer = null
        cachedGameTime = Long.MIN_VALUE
        cachedResult = false
    }

    @JvmStatic
    fun isActive(player: Player): Boolean {
        val gameTime = player.level().gameTime
        val uuid = player.uuid

        if (cachedPlayer == uuid && cachedGameTime == gameTime) return cachedResult

        val inHands = player.mainHandItem.`is`(ECRTags.Items.MRU_LINK_VIEWER_ITEMS)

        cachedPlayer = uuid
        cachedGameTime = gameTime
        cachedResult = inHands

        return inHands
    }
}
