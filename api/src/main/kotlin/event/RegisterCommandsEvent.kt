package com.algorithmlx.ecr.api.event

import com.algorithmlx.ecr.api.event.engine.Event
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class RegisterCommandsEvent(
    val dispatcher: CommandDispatcher<CommandSourceStack>,
    val buildContext: CommandBuildContext,
    val commandSelection: Commands.CommandSelection
) : Event()
