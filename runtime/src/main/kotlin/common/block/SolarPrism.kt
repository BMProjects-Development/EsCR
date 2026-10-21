package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.api.block.SideBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.BeaconBeamBlock
import net.minecraft.world.level.block.CrossCollisionBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class SolarPrism(properties: Properties): SideBlock(properties), BeaconBeamBlock {
    private val northShape = Shapes.box(0.0, 0.4375, 0.0, 1.0, 0.5625, 0.125)
    private val eastShape = Shapes.box(0.875, 0.4375, 0.0, 1.0, 0.5625, 1.0)
    private val southShape = Shapes.box(0.0, 0.4375, 0.875, 1.0, 0.5625, 1.0)
    private val westShape = Shapes.box(0.0, 0.4375, 0.0, 0.125, 0.5625, 1.0)
    private val sideShape = Shapes.box(0.0, 0.484375, 0.0, 1.0, 0.515625, 1.0)

    override fun getColor(): DyeColor = DyeColor.YELLOW

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        var shape = sideShape

        if (!state.getValue(NORTH)) shape = Shapes.or(shape, northShape)
        if (!state.getValue(EAST)) shape = Shapes.or(shape, eastShape)
        if (!state.getValue(SOUTH)) shape = Shapes.or(shape, southShape)
        if (!state.getValue(WEST)) shape = Shapes.or(shape, westShape)

        return shape
    }

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return this.getShape(state, level, pos, context)
    }
}
