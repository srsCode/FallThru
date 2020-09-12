/*
 * Project      : FallThru
 * File         : AbstractBlockPropertiesMixin.java
 * Last Modified: 20200911-22:38:37-0400
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

import net.minecraft.block.AbstractBlock;

import srscode.fallthru.FallThru;


/**
 *  This mixin is only required on the client as the changes made are only used for rendering.
 */
@Mixin(net.minecraft.block.AbstractBlock.Properties.class)
public class AbstractBlockPropertiesMixin
{
    /**
     *  Keep the native IPositionPredicates in order to wrap them.
     *  These will be set in clinit prior to the original fields being rewritten by the wrappers below.
     */
    private AbstractBlock.IPositionPredicate nativeSuffocates   = this.suffocates;
    private AbstractBlock.IPositionPredicate nativeBlocksVision = this.blocksVision;

    /**
     *  This IPositionPredicate prevents entities from being pushed out of blocks (required for 1.16.1).
     */
    @Shadow private AbstractBlock.IPositionPredicate suffocates = (blockState, world, pos) ->
        !FallThru.BLOCK_CONFIG_MAP.hasKey(blockState.getBlock()) && this.nativeSuffocates.test(blockState, world, pos);

    /**
     *  This IPositionPredicate changes the rendering test so that players will be able to see through the block their head is in.
     */
    @Shadow private AbstractBlock.IPositionPredicate blocksVision = (blockState, world, pos) ->
        !FallThru.BLOCK_CONFIG_MAP.hasKey(blockState.getBlock()) && this.nativeBlocksVision.test(blockState, world, pos);
}
