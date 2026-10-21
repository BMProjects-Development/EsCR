package com.algorithmlx.ecr.neoforge

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.neoforge.init.NeoForgeInit
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

@Mod(ModId)
class Main(eventBus: IEventBus) {
    init {
        NeoForgeInit.init(eventBus)
    }
}
