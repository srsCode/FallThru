/*
 * Project      : FallThru
 * File         : WalkNodeProcessorMixin.java
 * Last Modified: 20210326-07:19:46-0400
 *
 * Copyright (c) 2019-2021 srsCode, srs-bsns (forfrdm [at] gmail.com)
 *
 * The MIT License (MIT)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package srscode.fallthru.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.pathfinding.NodeProcessor;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;

import srscode.fallthru.FallThru;

/**
 *  This mixin provides patches to fix entity pathfinding so that entities can pathfind through passable blocks and
 *  not pathfind around them or get stuck in them.
 */
@SuppressWarnings("AbstractClassNeverImplemented")
@Mixin(net.minecraft.pathfinding.WalkNodeProcessor.class)
public abstract class WalkNodeProcessorMixin extends NodeProcessor
{
    WalkNodeProcessorMixin()
    {}

    /**
     *  A patch for pathfinding to allow entities to pathfind through passable blocks.
     *  SRG name: func_215744_a
     *
     *  @param callback Returns the penalized PathPoint.
     */
    @Inject(at = @At("HEAD"), cancellable = true,
        method = "refineNodeType(Lnet/minecraft/world/IBlockReader;ZZLnet/minecraft/util/math/BlockPos;Lnet/minecraft/pathfinding/PathNodeType;)Lnet/minecraft/pathfinding/PathNodeType;")
    private void refineNodeType(final IBlockReader world, final boolean closed, final boolean door, final BlockPos pos,
                                final PathNodeType pathNodeType, final CallbackInfoReturnable<PathNodeType> callback)
    {
        if (FallThru.BLOCK_CONFIG_MAP.hasKey(world.getBlockState(pos).getBlock())) {
            callback.setReturnValue(PathNodeType.WALKABLE);
        }
    }

    /**
     *  A patch for pathfinding to allow entities to pathfind through passable blocks.
     *  SRG name: func_186330_a
     *
     *  @param callback Returns <tt>PathNodeType.WALKABLE</tt> if this is a passable block.
     */
    @Inject(at = @At("HEAD"), cancellable = true, method = "getFloorNodeType(Lnet/minecraft/world/IBlockReader;III)Lnet/minecraft/pathfinding/PathNodeType;")
    private void getFloorNodeType(final IBlockReader world, final int x, final int y, final int z, final CallbackInfoReturnable<PathNodeType> callback)
    {
        if (FallThru.BLOCK_CONFIG_MAP.hasKey(world.getBlockState(new BlockPos(x, y, z)).getBlock())) {
            callback.setReturnValue(PathNodeType.WALKABLE);
        }
    }

    /**
     *  A patch for pathfinding to add a penalty for entities pathfinding through passable blocks.
     *  The penalty is 2x the inverse of the configured speed multiplier, which shouldn't be overly disruptive
     *  of the natural path, but enough for entities to avoid passable blocks in most cases.
     *  SRG name: func_186332_a
     *
     *  @param callback Returns the penalized PathPoint.
     */
    @Inject(at = @At("HEAD"), cancellable = true, method = "getSafePoint(IIIIDLnet/minecraft/util/Direction;Lnet/minecraft/pathfinding/PathNodeType;)Lnet/minecraft/pathfinding/PathPoint;")
    private void getSafePoint(final int x, final int y, final int z, final int stepHeight, final double groundYIn,
                              final Direction facing, final PathNodeType nodeType, final CallbackInfoReturnable<PathPoint> callback)
    {
        FallThru.BLOCK_CONFIG_MAP
            .getConfig(this.blockaccess.getBlockState(new BlockPos(x, y, z)).getBlock())
            .ifPresent(blockConfig -> {
                final PathPoint pp = this.openPoint(x, y, z);
                pp.nodeType = PathNodeType.WALKABLE;
                pp.costMalus = (float)Math.max(0.0, (1.0 / blockConfig.getSpeedMult()) * 2.0);
                callback.setReturnValue(pp);
            });
    }
}
