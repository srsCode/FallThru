/*
 * Project      : FallThru
 * File         : AbstractBlockStateMixin.java
 * Last Modified: 20200912-06:18:15-0400
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

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.pathfinding.PathType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import srscode.fallthru.FallThru;
import srscode.fallthru.RedirectionHandler;

/**
 *  This mixin provides:
 *  1. The redirect to the FallThru RedirectionHandler to handle collision for configured blocks.
 *  2. A patch to help entity AI pathfind so that entities will avoid configured blocks.
 */
@SuppressWarnings("AbstractClassNeverImplemented")
@Mixin(net.minecraft.block.AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin
{
    protected AbstractBlockStateMixin()
    {}

    @Shadow protected abstract BlockState getSelf();

    /**
     *  A patch for <tt>net.minecraft.block.AbstractBlock.AbstractBlockState#onEntityCollision</tt>
     *  to redirect collision handing to the FallThru RedirectionHandler.
     *
     *  @param callback will be set to cancel unless the native collision handling should also execute.
     */
    @Inject(at = @At("HEAD"), cancellable = true, method = "onEntityCollision(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;)V")
    private void onEntityCollision(final World world, final BlockPos pos, final Entity entity, final CallbackInfo callback)
    {
        if (entity instanceof LivingEntity) {
            FallThru.BLOCK_CONFIG_MAP.getConfig(this.getSelf().getBlock())
                .ifPresent(blockConfig -> RedirectionHandler.handleCollision(world, pos, (LivingEntity)entity, this.getSelf(), blockConfig, callback));
        }
    }

    /**
     *  A patch for pathfinding to prevent entities from pathfinding through passable blocks.
     *
     *  @param callback will always be set to <tt>true</tt> for configured blocks, preventing native functionality.
     */
    // TODO: Suggested by Deximus-Maximus. Requires verification that this actually works properly.
    @Inject(at = @At("HEAD"), cancellable = true, method = "allowsMovement(Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/pathfinding/PathType;)Z")
    private void allowsMovement(final IBlockReader world, final BlockPos pos, final PathType type, final CallbackInfoReturnable<Boolean> callback)
    {
        if (FallThru.BLOCK_CONFIG_MAP.hasKey(this.getSelf().getBlock())) {
            callback.setReturnValue(true);
        }
    }
}
