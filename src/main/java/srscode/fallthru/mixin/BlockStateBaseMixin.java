/*
 * Project      : FallThru
 * File         : BlockStateBaseMixin.java
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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import srscode.fallthru.FallThru;
import srscode.fallthru.RedirectionHandler;

/**
 *  This mixin provides:
 *  1. The redirect to the FallThru RedirectionHandler to handle collision for configured blocks.
 *  2. A patch to help entity AI pathfind through configured blocks.
 */
@SuppressWarnings("AbstractClassNeverImplemented")
@Mixin(net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin
{
    BlockStateBaseMixin()
    {}

    @Shadow protected abstract BlockState asState();

    /**
     *  A patch for <tt>net.minecraft.block.AbstractBlock.AbstractBlockState#onEntityCollision</tt>
     *  to redirect collision handing to the FallThru RedirectionHandler.
     *  SRG name: func_196950_a, Official name: entityInside
     *
     *  @param callback will be set to cancel unless the native collision handling should also execute.
     */
    @Inject(at = @At("HEAD"), cancellable = true,
        method = "entityInside(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V")
    private void entityInside(final Level world, final BlockPos pos, final Entity entity, final CallbackInfo callback)
    {
        if (entity instanceof LivingEntity) {
            FallThru.BLOCK_CONFIG_MAP.getConfig(this.asState().getBlock())
                .ifPresent(blockConfig -> RedirectionHandler.handleCollision(world, pos, (LivingEntity)entity, this.asState(), blockConfig, callback));
        }
    }

    /**
     *  A patch for pathfinding to help entities pathfind through passable blocks.
     *  SRG name: func_196957_g, Official name: isPathfindable
     *
     *  @param callback will always be set to <tt>true</tt> for configured blocks, preventing native functionality.
     */
    @Inject(at = @At("HEAD"), cancellable = true,
        method = "isPathfindable(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/pathfinder/PathComputationType;)Z")
    private void isPathfindable(final BlockGetter world, final BlockPos pos, final PathComputationType type, final CallbackInfoReturnable<Boolean> callback)
    {
        if (type == PathComputationType.LAND && FallThru.BLOCK_CONFIG_MAP.hasKey(this.asState().getBlock())) {
            callback.setReturnValue(true);
        }
    }

    /**
     *  A patch for pathfinding to prevent entities from trying to jump onto passable blocks when moving through them.
     *  SRG name: func_196952_d, Official name: getCollisionShape
     *
     *  @param callback will return <tt>VoxelShapes#empty</tt> for configured blocks, preventing native functionality.
     */
    @Inject(at = @At("HEAD"), cancellable = true,
        method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
    private void getCollisionShape(final BlockGetter world, final BlockPos pos, final CallbackInfoReturnable<VoxelShape> callback)
    {
        if (FallThru.BLOCK_CONFIG_MAP.hasKey(this.asState().getBlock())) {
            callback.setReturnValue(Shapes.empty());
        }
    }
}
