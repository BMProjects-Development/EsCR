package com.algorithmlx.ecr.network

import com.algorithmlx.ecr.api.geo.client.ClientGeoAnimations
import com.algorithmlx.ecr.api.research.ClientResearchState
import com.algorithmlx.ecr.api.vfx.VfxClient
import com.algorithmlx.ecr.client.renderer.MagicShieldRenderer
import com.algorithmlx.ecr.client.utils.BoundGemSlotTooltips
import com.algorithmlx.ecr.client.screen.MIMScreen
import net.minecraft.client.Minecraft

object ECRClientPackets {
    fun init() {
        ECRPackets.mimTaskResponse.handle { payload ->
            (Minecraft.getInstance().gui.screen() as? MIMScreen)?.acceptTasks(payload)
        }
        ECRPackets.mimDependencyResponse.handle { payload ->
            (Minecraft.getInstance().gui.screen() as? MIMScreen)?.acceptDependencyTree(payload)
        }
        ECRPackets.menuBoundGemTooltipResponse.handle { payload -> BoundGemSlotTooltips.accept(payload) }
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
        ECRPackets.vfxCommand.handle { payload -> VfxClient.accept(payload) }
        ECRPackets.installClient()
    }
}
