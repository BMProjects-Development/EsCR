package com.algorithmlx.ecr.additions.forge

import com.algorithmlx.ecr.additions.Additions
import com.algorithmlx.ecr.additions.client.AdditionsClient
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment

@Mod(Additions.ModId)
class AdditionsForge {
    init {
        Additions.init()
        if (FMLEnvironment.getDist().isClient) AdditionsClient.init()
    }
}
