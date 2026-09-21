package com.algorithmlx.ecr.network

import com.algorithmlx.ecr.api.geo.client.ClientGeoAnimations
import com.algorithmlx.ecr.api.research.ClientResearchState
import com.algorithmlx.ecr.client.renderer.MagicShieldRenderer

object ECRClientPackets {
    fun init() {
        ECRPackets.geoBlockAnimation.handle { payload -> ClientGeoAnimations.handle(payload) }
        ECRPackets.geoEntityAnimation.handle { payload -> ClientGeoAnimations.handle(payload) }
        ECRPackets.geoItemAnimation.handle { payload -> ClientGeoAnimations.handle(payload) }
        ECRPackets.geoBlockAnimationStop.handle { payload -> ClientGeoAnimations.handle(payload) }
        ECRPackets.geoEntityAnimationStop.handle { payload -> ClientGeoAnimations.handle(payload) }
        ECRPackets.geoItemAnimationStop.handle { payload -> ClientGeoAnimations.handle(payload) }
        ECRPackets.researchSync.handle { payload -> ClientResearchState.apply(payload) }
        ECRPackets.researchProgress.handle { payload -> ClientResearchState.apply(payload) }
        ECRPackets.boundGemTooltipResponse.handle { payload -> BoundGemTooltipNetwork.acceptResponse(payload) }
        ECRPackets.soulStoneTooltipResponse.handle { payload -> SoulStoneTooltipNetwork.acceptResponse(payload) }
        ECRPackets.magicShield.handle { payload -> MagicShieldRenderer.accept(payload) }
        ECRPackets.installClient()
    }
}
