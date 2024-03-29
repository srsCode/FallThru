/*
 * Project      : FallThru
 * File         : BlockBehaviourPropertiesMixin.java
 *
 * Copyright (c) 2019-2023 srsCode, srs-bsns (forfrdm [at] gmail.com)
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


import net.minecraft.world.level.block.state.BlockBehaviour;

import srscode.fallthru.FallThru;


/**
 *  This mixin is only required on the client as the changes made are only used for rendering.
 */
@Mixin(net.minecraft.world.level.block.state.BlockBehaviour.Properties.class)
public class BlockBehaviourPropertiesMixin
{
    /**
     *  Keep the native IPositionPredicates in order to wrap them.
     *  These will be set in clinit prior to the original fields being rewritten by the wrappers below.
     */
    private final BlockBehaviour.StatePredicate nativeIsSuffocating  = this.isSuffocating;
    private final BlockBehaviour.StatePredicate nativeIsViewBlocking = this.isViewBlocking;

    /**
     *  This IPositionPredicate prevents entities from being pushed out of blocks (required for 1.16.1).
     *  SRG name: field_235816_r_, Official name: isSuffocating
     */
    @Shadow BlockBehaviour.StatePredicate isSuffocating = (blockState, world, pos) ->
        !FallThru.blockConfigs().containsKey(blockState) && this.nativeIsSuffocating.test(blockState, world, pos);

    /**
     *  This IPositionPredicate changes the rendering test so that players will be able to see through the block their head is in.
     *  SRG name: field_235817_s_, Official name: isViewBlocking
     */
    @Shadow BlockBehaviour.StatePredicate isViewBlocking = (blockState, world, pos) ->
        !FallThru.blockConfigs().containsKey(blockState) && this.nativeIsViewBlocking.test(blockState, world, pos);
}
