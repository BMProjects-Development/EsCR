package com.algorithmlx.ecr.api.vfx

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.world.phys.Vec3
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

object VfxCommands {
    fun create(): LiteralArgumentBuilder<CommandSourceStack> =
            Commands.literal("vfx")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("effect", IdentifierArgument.id())
                        .executes { context ->
                            play(context.source, IdentifierArgument.getId(context, "effect"), context.source.position)
                        }
                        .then(Commands.argument("position", Vec3Argument.vec3()).executes { context ->
                            play(context.source, IdentifierArgument.getId(context, "effect"), Vec3Argument.getVec3(context, "position"))
                        })
                )

    private fun play(source: CommandSourceStack, id: Identifier, position: Vec3): Int {
        val player = source.playerOrException
        val handle = VfxNetwork.play(listOf(player), id, position)
        source.sendSuccess({ Component.literal("Playing VFX $id (handle ${handle.id})") }, false)
        return 1
    }
}
