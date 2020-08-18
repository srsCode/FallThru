/*
 * Project      : FallThru
 * File         : AbstractBlockStateMixin.java
 * Last Modified: 20200816-21:28:41-0400
 *
 * Copyright (c) 2019-2020 srsCode, srs-bsns (forfrdm [at] gmail.com)
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

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.pathfinding.PathType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import srscode.fallthru.FallThru;
import srscode.fallthru.RedirectionHandler;

@SuppressWarnings("AbstractClassNeverImplemented")
@Mixin(net.minecraft.block.AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin
{
    protected AbstractBlockStateMixin()
    {}

    @Shadow protected abstract BlockState getSelf();

    // AbstractBlockState#onEntityCollision patch to handle collision with configured blocks.
    @Inject(at = @At("HEAD"), cancellable = true, method = "onEntityCollision(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;)V")
    private void onEntityCollision(final World world, final BlockPos pos, final Entity entity, final CallbackInfo callback)
    {
        RedirectionHandler.handleCollision(world, pos, entity, this.getSelf(), callback);
    }

    /**
     *  This redirect prevents entities from being pushed out of configured blocks that have full a {@link VoxelShape}.
     *  {@link AbstractBlock.AbstractBlockState#hasOpaqueCollisionShape} has to be targetted instead of
     *  {@link AbstractBlock.AbstractBlockState#getCollisionShape} because of the relevant VoxelShape being cached within
     *  the AbstractBlock.AbstractBlockState#cache.
     */
    @Inject(at = @At("HEAD"), cancellable = true, method = "hasOpaqueCollisionShape(Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;)Z")
    public void hasOpaqueCollisionShape(final IBlockReader reader, final BlockPos pos, final CallbackInfoReturnable<Boolean> callback)
    {
        if (FallThru.BLOCK_CONFIG_MAP.hasKey(this.getSelf().getBlock())) {
            callback.setReturnValue(false);
        }
    }

    // Patch for pathfinding. TODO: Requires verification that this actually works properly.
    @Inject(at = @At("HEAD"), cancellable = true, method = "allowsMovement(Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/pathfinding/PathType;)Z")
    private void allowsMovement(final IBlockReader world, final BlockPos pos, final PathType type, final CallbackInfoReturnable<Boolean> callback)
    {
        if (FallThru.BLOCK_CONFIG_MAP.hasKey(this.getSelf().getBlock())) {
            callback.setReturnValue(true);
        }
    }
}
