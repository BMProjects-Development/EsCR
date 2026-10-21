package com.algorithmlx.ecr.network

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.geo.GeoBlockAnimationPayload
import com.algorithmlx.ecr.api.geo.GeoBlockAnimationStopPayload
import com.algorithmlx.ecr.api.geo.GeoEntityAnimationPayload
import com.algorithmlx.ecr.api.geo.GeoEntityAnimationStopPayload
import com.algorithmlx.ecr.api.geo.GeoItemAnimationPayload
import com.algorithmlx.ecr.api.geo.GeoItemAnimationStopPayload
import com.algorithmlx.ecr.api.network.MaxPayloadSize
import com.algorithmlx.ecr.api.network.PacketRegistry
import com.algorithmlx.ecr.api.research.CompleteResearchPayload
import com.algorithmlx.ecr.api.research.FavoriteResearchPayload
import com.algorithmlx.ecr.api.research.ResearchProgress
import com.algorithmlx.ecr.api.research.ResearchProgressPayload
import com.algorithmlx.ecr.api.research.ResearchSyncPayload
import com.algorithmlx.ecr.api.research.UpdateBookViewPayload
import com.algorithmlx.ecr.api.vfx.VfxCommandPayload
import com.algorithmlx.ecr.common.menu.MIMSearchPayload
import com.algorithmlx.ecr.common.menu.MIMViewportPayload
import com.algorithmlx.ecr.common.menu.MIMCraftPayload
import com.algorithmlx.ecr.common.menu.MIMCoreClickPayload
import com.algorithmlx.ecr.common.menu.MIMDependencyRequest
import com.algorithmlx.ecr.common.menu.MIMDependencyResponse
import com.algorithmlx.ecr.common.menu.MIMTaskRequest
import com.algorithmlx.ecr.common.menu.MIMTaskCancel
import com.algorithmlx.ecr.common.menu.MIMTaskResponse

object ECRPackets : PacketRegistry(ModId) {
    val mimTaskRequest = serverbound(MIMTaskRequest.TYPE, MIMTaskRequest.STREAM_CODEC) { payload ->
        MIMTaskRequest.handle(player, payload)
    }
    val mimTaskCancel = serverbound(MIMTaskCancel.TYPE, MIMTaskCancel.STREAM_CODEC) { payload ->
        MIMTaskCancel.handle(player, payload)
    }
    val mimTaskResponse = clientbound(MIMTaskResponse.TYPE, MIMTaskResponse.STREAM_CODEC, MaxPayloadSize(4 * 1024 * 1024))
    val mimDependencyRequest = serverbound(MIMDependencyRequest.TYPE, MIMDependencyRequest.STREAM_CODEC) { payload ->
        MIMDependencyRequest.handle(player, payload)
    }
    val mimDependencyResponse = clientbound(MIMDependencyResponse.TYPE, MIMDependencyResponse.STREAM_CODEC, MaxPayloadSize(8 * 1024 * 1024))
    val menuBoundGemTooltipRequest = serverbound(MenuBoundGemTooltipRequest.TYPE, MenuBoundGemTooltipRequest.STREAM_CODEC) { payload ->
        MenuBoundGemTooltipRequest.handle(player, payload)
    }
    val menuBoundGemTooltipResponse = clientbound(MenuBoundGemTooltipResponse.TYPE, MenuBoundGemTooltipResponse.STREAM_CODEC)
    val mimCoreClick = serverbound(MIMCoreClickPayload.TYPE, MIMCoreClickPayload.STREAM_CODEC) { payload ->
        MIMCoreClickPayload.handle(player, payload)
    }
    val mimCraft = serverbound(MIMCraftPayload.TYPE, MIMCraftPayload.STREAM_CODEC) { payload ->
        MIMCraftPayload.handle(player, payload)
    }
    val mimViewport = serverbound(MIMViewportPayload.TYPE, MIMViewportPayload.STREAM_CODEC) { payload ->
        MIMViewportPayload.handle(player, payload)
    }
    val mimSearch = serverbound(MIMSearchPayload.TYPE, MIMSearchPayload.STREAM_CODEC) { payload ->
        MIMSearchPayload.handle(player, payload)
    }
    val geoBlockAnimation = clientbound(GeoBlockAnimationPayload.TYPE, GeoBlockAnimationPayload.STREAM_CODEC)
    val geoEntityAnimation = clientbound(GeoEntityAnimationPayload.TYPE, GeoEntityAnimationPayload.STREAM_CODEC)
    val geoItemAnimation = clientbound(GeoItemAnimationPayload.TYPE, GeoItemAnimationPayload.STREAM_CODEC)
    val geoBlockAnimationStop = clientbound(GeoBlockAnimationStopPayload.TYPE, GeoBlockAnimationStopPayload.STREAM_CODEC)
    val geoEntityAnimationStop = clientbound(GeoEntityAnimationStopPayload.TYPE, GeoEntityAnimationStopPayload.STREAM_CODEC)
    val geoItemAnimationStop = clientbound(GeoItemAnimationStopPayload.TYPE, GeoItemAnimationStopPayload.STREAM_CODEC)
    val boundGemTooltipResponse = clientbound(BoundGemTooltipResponsePayload.TYPE, BoundGemTooltipResponsePayload.STREAM_CODEC)
    val soulStoneTooltipResponse = clientbound(SoulStoneTooltipResponsePayload.TYPE, SoulStoneTooltipResponsePayload.STREAM_CODEC)
    val magicShield = clientbound(MagicShieldPayload.TYPE, MagicShieldPayload.STREAM_CODEC)
    val researchSync = clientbound(
        ResearchSyncPayload.TYPE,
        ResearchSyncPayload.STREAM_CODEC,
        MaxPayloadSize(8 * 1024 * 1024)
    )
    val researchProgress = clientbound(ResearchProgressPayload.TYPE, ResearchProgressPayload.STREAM_CODEC)
    val vfxCommand = clientbound(VfxCommandPayload.TYPE, VfxCommandPayload.STREAM_CODEC)

    val completeResearch = serverbound(CompleteResearchPayload.TYPE, CompleteResearchPayload.STREAM_CODEC) { payload ->
        ResearchProgress.tryUnlock(player, payload.research)
    }
    val favoriteResearch = serverbound(FavoriteResearchPayload.TYPE, FavoriteResearchPayload.STREAM_CODEC) { payload ->
        ResearchProgress.setBookmark(player, payload.research, payload.spread, payload.color)
    }
    val updateBookView = serverbound(UpdateBookViewPayload.TYPE, UpdateBookViewPayload.STREAM_CODEC) { payload ->
        ResearchProgress.updateView(player, payload.state)
    }
    val boundGemTooltipRequest = serverbound(
        BoundGemTooltipRequestPayload.TYPE,
        BoundGemTooltipRequestPayload.STREAM_CODEC
    ) { payload ->
        BoundGemTooltipNetwork.handleRequest(player, payload)
    }
    val soulStoneTooltipRequest = serverbound(
        SoulStoneTooltipRequestPayload.TYPE,
        SoulStoneTooltipRequestPayload.STREAM_CODEC
    ) { payload ->
        SoulStoneTooltipNetwork.handleRequest(player, payload)
    }
}
