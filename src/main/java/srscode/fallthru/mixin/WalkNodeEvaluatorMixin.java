/*
 * Project      : FallThru
 * File         : WalkNodeEvaluatorMixin.java
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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;

import srscode.fallthru.FallThru;

/**
 *  This mixin provides patches to fix entity pathfinding so that entities can pathfind through passable blocks and
 *  not pathfind around them or get stuck in them.
 */
@SuppressWarnings("AbstractClassNeverImplemented")
@Mixin(net.minecraft.world.level.pathfinder.WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin extends NodeEvaluator
{
    WalkNodeEvaluatorMixin()
    {}

    /**
     *  A patch for pathfinding to allow entities to pathfind through passable blocks.
     *  SRG name: func_215744_a, Official name: evaluateBlockPathType
     *
     *  @param callback Returns the penalized PathPoint.
     */
    @Inject(at = @At("HEAD"), cancellable = true, method = "evaluateBlockPathType(Lnet/minecraft/world/level/BlockGetter;ZZLnet/minecraft/core/BlockPos;" +
        "Lnet/minecraft/world/level/pathfinder/BlockPathTypes;)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;")
    private void evaluateBlockPathType(final BlockGetter world, final boolean closed, final boolean door, final BlockPos pos,
                                       final BlockPathTypes pathNodeType, final CallbackInfoReturnable<BlockPathTypes> callback)
    {
        if (FallThru.BLOCK_CONFIG_MAP.hasKey(world.getBlockState(pos).getBlock())) {
            callback.setReturnValue(BlockPathTypes.WALKABLE);
        }
    }

    /**
     *  A patch for pathfinding to allow entities to pathfind through passable blocks.
     *  SRG name: func_186330_a, Official name: getBlockPathType
     *
     *  @param callback Returns <tt>PathNodeType.WALKABLE</tt> if this is a passable block.
     */
    @Inject(at = @At("HEAD"), cancellable = true,
        method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;III)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;")
    private void getBlockPathType(final BlockGetter world, final int x, final int y, final int z, final CallbackInfoReturnable<BlockPathTypes> callback)
    {
        if (FallThru.BLOCK_CONFIG_MAP.hasKey(world.getBlockState(new BlockPos(x, y, z)).getBlock())) {
            callback.setReturnValue(BlockPathTypes.WALKABLE);
        }
    }

    /**
     *  A patch for pathfinding to add a penalty for entities pathfinding through passable blocks.
     *  The penalty is 2x the inverse of the configured speed multiplier, which shouldn't be overly disruptive
     *  of the natural path, but enough for entities to avoid passable blocks in most cases.
     *  SRG name: func_186332_a, Official name: findAcceptedNode
     *
     *  @param callback Returns the penalized Path.
     */
    @Inject(at = @At("HEAD"), cancellable = true,
        method = "findAcceptedNode(IIIIDLnet/minecraft/core/Direction;Lnet/minecraft/world/level/pathfinder/BlockPathTypes;)Lnet/minecraft/world/level/pathfinder/Node;")
    private void findAcceptedNode(final int x, final int y, final int z, final int stepHeight, final double groundYIn,
                             final Direction facing, final BlockPathTypes nodeType, final CallbackInfoReturnable<Node> callback)
    {
        FallThru.BLOCK_CONFIG_MAP
            .getConfig(this.level.getBlockState(new BlockPos(x, y, z)).getBlock())
            .ifPresent(blockConfig -> {
                final var node = this.getNode(x, y, z);
                node.type = BlockPathTypes.WALKABLE;
                node.costMalus = (float)Math.max(0.0, (1.0 / blockConfig.speedMult()) * 2.0);
                callback.setReturnValue(node);
            });
    }
}
