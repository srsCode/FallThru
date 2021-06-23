/*
 * Project      : FallThru
 * File         : Accessors.java
 * Last Modified: 20201117-03:04:36-0500
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
import org.spongepowered.asm.mixin.gen.Accessor;


/**
 *  This class contains various {@link Accessor}s used to access/modify private class members.
 *  This is a better alternative to using access transformers as artifacts don't require being rebuilt.
 */
public final class Accessors
{
    private Accessors()
    {}

    /**
     *  An accessor for the protected field <tt>net.minecraft.block.AbstractBlock#canCollide</tt> which allows
     *  setting it so that entities can pass through the block.
     */
    @Mixin(net.minecraft.block.AbstractBlock.class)
    public interface AbstractBlockAccessor
    {
        @Accessor boolean getCanCollide();

        @Accessor void setCanCollide(final boolean val);
    }

    /**
     *  An accessor for the private field <tt>net.minecraft.block.AbstractBlock.AbstractBlockState#isSolid</tt>.
     *  This fixes the rendering issue where players passing through 'solid' blocks would have an x-ray
     *  effect with other connected solid blocks.
     *  Note: This mixin is prefered over an injection into <tt>Block#shouldSideBeRendered</tt> as that would
     *  have undesirable rendering effects with some blocks, such as glass/stained glass blocks, which users
     *  are highly unlikely to make passable.
     */
    @Mixin(net.minecraft.block.AbstractBlock.AbstractBlockState.class)
    public interface AbstractBlockStateAccessor
    {
        @Accessor boolean getIsSolid();

        @Accessor void setIsSolid(final boolean solid);
    }

    /**
     *  An accessor for the protected field <tt>net.minecraft.entity.LivingEntity#isJumping</tt>.
     *  This gives the FallThru RedirectionHandler access to be able to modify an entity's vertical speed
     *  reduction differently while jumping so that the entity is able to jump atleast a full block height.
     */
    @Mixin(net.minecraft.entity.LivingEntity.class)
    public interface LivingEntityAccessor
    {
        @Accessor boolean getIsJumping();
    }
}
