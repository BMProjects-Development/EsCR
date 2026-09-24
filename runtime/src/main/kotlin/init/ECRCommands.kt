package com.algorithmlx.ecr.init

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.commands.commands
import com.algorithmlx.ecr.api.research.ResearchJson
import com.algorithmlx.ecr.api.research.ResearchProgress
import com.algorithmlx.ecr.common.components.playerMatrix
import com.algorithmlx.ecr.common.components.updatePlayerMatrix
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

object ECRCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.commands { ModId {
            aliases("essential-craft", "essentialcraft", "ecr")
            requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))

            "research" {
                "reset" {
                    executes {
                        val player = this.context.source.player
                        if (player == null) {
                            source.sendFailure(Component.translatableWithFallback(
                                "research.reset.failure".prefix,
                                "Research reset without player available only inside game. Not console."
                            ))
                            return@executes 0
                        }
                        ResearchProgress.reset(player)

                        source.sendSuccess({
                            Component.translatableWithFallback(
                                "research.reset.success".prefix,
                                "Reset research progress for %s successful.",
                                player.name.string
                            )
                        }, true)
                        1
                    }

                    argument(
                        "targets",
                        EntityArgument.players(),
                        { context, name -> EntityArgument.getPlayers(context, name) }
                    ) { targets ->
                        executes {
                            val players = targets()
                            players.forEach(ResearchProgress::reset)
                            source.sendSuccess({
                                Component.translatableWithFallback(
                                    "research.reset.success.sized".prefix,
                                    "Reset research progress for %s player(s) successful.",
                                    players.size
                                )
                            }, true)
                            players.size
                        }
                    }
                }

                "unlock_all" {
                    executes {
                        val player = this.context.source.player
                        if (player == null) {
                            source.sendFailure(
                                Component.translatableWithFallback(
                                    "research.unlock.all.failure".prefix,
                                    "Research unlock without player available only inside game. Not console."
                                )
                            )
                            return@executes 0
                        }
                        ResearchProgress.grantAll(player)

                        source.sendSuccess({
                            Component.translatableWithFallback(
                                "research.unlock.all.success".prefix,
                                "Unlocked all research for %s.",
                                player.name.string
                            )
                        }, true)
                        1
                    }

                    argument(
                        "targets",
                        EntityArgument.players(),
                        { context, name -> EntityArgument.getPlayers(context, name) }
                    ) { targets ->
                        executes {
                            val players = targets()
                            players.forEach(ResearchProgress::grantAll)

                            source.sendSuccess({
                                Component.translatableWithFallback(
                                    "research.unlock.all.success.sized".prefix,
                                    "Unlocked all research for %s player(s).",
                                    players.size
                                )
                            }, true)
                            players.size
                        }
                    }
                }

                "unlock" {
                    argument("research", StringArgumentType.string()) { research ->
                        executes {
                            val target = parseTarget(research()) ?: return@executes 0
                            val player = this.context.source.player
                            if (player == null) {
                                source.sendFailure(
                                    Component.translatableWithFallback(
                                        "research.unlock.failure".prefix,
                                        "Research progress update without player available only inside game. Not console."
                                    )
                                )
                                return@executes 0
                            }
                            ResearchProgress.grant(player, target.first, target.second)
                            source.sendSuccess({
                                Component.translatableWithFallback(
                                    "research.unlock.success".prefix,
                                    "Updated research progress for %s.",
                                    player.name.string
                                )
                            }, true)
                            1
                        }

                        argument(
                            "targets",
                            EntityArgument.players(),
                            { context, name -> EntityArgument.getPlayers(context, name) }
                        ) { targets ->
                            executes {
                                val target = parseTarget(research()) ?: return@executes 0
                                val changed = targets()
                                    .count { ResearchProgress.grant(it, target.first, target.second) }
                                source.sendSuccess({
                                    Component.translatableWithFallback(
                                        "research.unlock.success.sized".prefix,
                                        "Updated research progress for %s player(s).",
                                        changed
                                    )
                                }, true)
                                changed
                            }
                        }
                    }
                }
            }

            ECRModIDs.UBMRU {
                "query" {
                    executes {
                        val player = this.context.source.player
                        if (player == null) {
                            source.sendFailure(
                                Component.translatableWithFallback(
                                    "ubmru.query.failure".prefix,
                                    "Get UBMRU count without player available only inside game. Not console."
                                )
                            )
                            return@executes 0
                        }

                        source.sendSuccess(
                            {
                                Component.translatableWithFallback(
                                    "ubmru.query.success".prefix,
                                    "%s has %s %s.",
                                    player.name.string, player.playerMatrix.mru,
                                    player.playerMatrix.mruType.name.string
                                )
                                Component.literal(
                                    "${player.name.string} has ${player.playerMatrix.mru} ${player.playerMatrix.mruType.name.string}."
                                )
                            },
                            true
                        )
                        1
                    }
                }

                "add" {
                    argument("count", IntegerArgumentType.integer()) { count ->
                        executes {
                            val player = this.context.source.player
                            if (player == null) {
                                source.sendFailure(
                                    Component.translatableWithFallback(
                                        "ubmru.query.failure".prefix,
                                        "UBMRU addition without player available only inside game. Not console."
                                    )
                                )
                                return@executes 0
                            }

                            player.updatePlayerMatrix { this.insert(count()) }

                            source.sendSuccess(
                                {
                                    Component.translatableWithFallback(
                                        "ubmru.query.success".prefix,
                                        "%s %s was added to %s.",
                                        count(), player.playerMatrix.mruType.name.string,
                                        player.name.string
                                    )
                                },
                                true
                            )

                            1
                        }

                        argument(
                            "targets",
                            EntityArgument.players(),
                            { context, name -> EntityArgument.getPlayers(context, name) }
                        ) { targets ->
                            executes {
                                val players = targets()
                                val mruTypeName = players.first().playerMatrix.mruType.name
                                players.forEach { player ->
                                    player.updatePlayerMatrix { this.insert(count()) }
                                }

                                source.sendSuccess(
                                    {
                                        Component.translatableWithFallback(
                                            "ubmru.query.success.sized".prefix,
                                            "%s %s was added for %s player(s).",
                                            count(), mruTypeName, players.size
                                        )
                                    },
                                    true
                                )

                                players.size
                            }
                        }
                    }
                }

                "set" {
                    argument("count", IntegerArgumentType.integer()) { count ->
                        executes {
                            val player = this.context.source.player
                            if (player == null) {
                                source.sendFailure(
                                    Component.translatableWithFallback(
                                        "ubmru.set.failure".prefix,
                                        "UBMRU set without player available only inside game. Not console."
                                    )
                                )
                                return@executes 0
                            }

                            player.updatePlayerMatrix { this.set(count()) }

                            source.sendSuccess(
                                {
                                    Component.translatableWithFallback(
                                        "ubmru.set.success".prefix,
                                        "%s %s was sets to %s.",
                                        count(), player.playerMatrix.mruType.name.string,
                                        player.name.string
                                    )
                                },
                                true
                            )

                            1
                        }

                        argument(
                            "targets",
                            EntityArgument.players(),
                            { context, name -> EntityArgument.getPlayers(context, name) }
                        ) { targets ->
                            executes {
                                val players = targets()
                                val mruTypeName = players.first().playerMatrix.mruType.name
                                players.forEach { player ->
                                    player.updatePlayerMatrix { this.set(count()) }
                                }

                                source.sendSuccess(
                                    {
                                        Component.translatableWithFallback(
                                            "ubmru.set.success.sized".prefix,
                                            "%s %s was sets for %s player(s).",
                                            count(), mruTypeName, players.size
                                        )
                                    },
                                    true
                                )

                                players.size
                            }
                        }
                    }
                }
            }
        } }
    }

    private fun parseTarget(value: String): Pair<Identifier, String?>? = runCatching {
        val requirement = ResearchJson.parseRequirement(value, null)
        requirement.researchId(Identifier.parse(value.substringBeforeLast('.', value))) to requirement.task
    }.getOrNull()

    private val String.prefix get() = "command.$ModId.$this"
}
