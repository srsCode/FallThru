/*
 * Project      : FallThru
 * File         : RedirectionHandler.java
 * Last Modified: 20200802-09:34:22-0400
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

package srscode.fallthru;

import java.util.Optional;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import net.minecraftforge.fml.ForgeI18n;

import srscode.fallthru.BlockConfigMap.BlockConfig;

/**
 * A class to handle the collision of {@link Entity}s with {@link BlockState}s in the world.
 */
@SuppressWarnings("unused")
public final class RedirectionHandler
{
    private RedirectionHandler() {}

    private static final String DAMAGETYPE_LEAVES = "fallintoleaves";
    private static final String DAMAGETYPE_SNOW   = "fallintosnow";
    private static final DamageSource DMGSRC_LEAVES = new DamageSource(DAMAGETYPE_LEAVES) {
        private static final String LANG_KEY = FallThru.MOD_ID + ".death." + DAMAGETYPE_LEAVES;
        @Nonnull
        @Override public ITextComponent getDeathMessage(@Nonnull final LivingEntity entity)
        {
            return ForgeI18n.getPattern(LANG_KEY).equals(LANG_KEY)
                   ? entity.getDisplayName().copyRaw().appendString(" fell into leaves and was impaled by branches")
                   : new TranslationTextComponent(LANG_KEY, entity.getDisplayName());
        }
    }.setDamageBypassesArmor();
    private static final DamageSource DMGSRC_SNOW = new DamageSource(DAMAGETYPE_SNOW) {
        private static final String LANG_KEY = FallThru.MOD_ID + ".death." + DAMAGETYPE_SNOW;
        @Nonnull
        @Override public ITextComponent getDeathMessage(@Nonnull final LivingEntity entity)
        {
            return ForgeI18n.getPattern(LANG_KEY).equals(LANG_KEY)
                   ? entity.getDisplayName().copyRaw().appendString(" got packed into a snowball")
                   : new TranslationTextComponent(LANG_KEY, entity.getDisplayName());
        }
    }.setDamageBypassesArmor();

    private static final BiPredicate<IBlockReader, BlockPos> BREAKABLE_FILTER = (world, filterpos) -> {
        final Block b = world.getBlockState(filterpos).getBlock();
        return b != Blocks.AIR && FallThru.BLOCK_CONFIG_MAP.hasKey(b);
    };
    private static final Predicate<Integer> GET_RANDOM = bound -> new Random().nextInt(Math.max(bound, 1)) == 0;

    /**
     * This method determines if an {@link LivingEntity} is moving based on the Vec3d from {@link Entity#getMotion}, adjusting
     * for a y-level drift inherent to players when looking around the mouse axis.
     *
     * This is used to determine whether or not to play a sound when the entity is moving through certain blocks.
     *
     * @param  motion The motion of the entity from {@link Entity#getMotion}.
     * @return        <b>true</b> if the entity is moving, <b>false</b> if not.
     */
    private static boolean isMoving(final Vector3d motion)
    {
        return Double.compare(motion.x, 0.0) != 0 || Double.compare(motion.z, 0.0) != 0 || (motion.y > 0.07 || motion.y < -0.07);
    }

    /**
     * This method calculates the possible damage from falling into a passable block using the formula:
     *
     *      {@code (FD - (VT + DT) * ((VT + EB) / VT)) * DM}
     *
     * Where:
     *  <b>FD</b> is the {@link Entity#fallDistance} being checked.
     *  <b>VT</b> is the vanilla default fall damage threshold of 3 blocks.
     *  <b>DT</b> is the value of {@link CommonConfig#damageThreshold}.
     *  <b>EB</b> is the effect bonus from the number of levels of Jump Boost applied.
     *  <b>DM</b> is the damage multiplier of the current {@link BlockState} being fallen into.
     *
     * If <b>DT <= 0</b> (disabled), then the vanilla formula is used: {@code VT + EB + 1}
     *
     * @param  fallDistance    The distance that the entity being checked has fallen.
     * @param  damageThreshold The value of the damageThreshold config setting.
     * @param  effectAddition  The effect level of Jump Boost potion effect to be applied, if any.
     * @param  damageMult      The configured damage multiplier for the block being fallen into.
     * @return                 The calculated damage. (may be <= 0)
     */
    private static double getDamage(final double fallDistance, final int damageThreshold, final double effectAddition, final double damageMult)
    {
        if (damageThreshold <= 0) {
            return fallDistance - (3 + effectAddition);
        }
        return (fallDistance - (3 + damageThreshold) * ((3 + effectAddition) / 3)) * damageMult;
    }

    /**
     * This method determines which {@link DamageSource} should be used when damaging an {@link LivingEntity} that has fallen into a passable Block.
     *
     * @param  blockState The {@link BlockState} of the block fallen into.
     * @return            The determined DamageSource.
     */
    private static DamageSource getDamageSource(final AbstractBlock.AbstractBlockState blockState)
    {
        final Block block = blockState.getBlock();
        final Material material = blockState.getMaterial();
        if (block.isIn(BlockTags.LEAVES)) {
            return DMGSRC_LEAVES;
        } else if (material == Material.SNOW || material == Material.SNOW_BLOCK) {
            return DMGSRC_SNOW;
        } else {
            return DamageSource.FALL;
        }
    }

    /**
     * The Collision handler.
     * This method is called from the mixin injection point in {@link AbstractBlock.AbstractBlockState#onEntityCollision} (func_196950_a).
     *
     * @param  world      The {@link World} of the Block being collided.
     * @param  pos        The {@link Vector3d} coordinates of the BlockState being collided with.
     * @param  entity     The {@link Entity} colliding with the BlockState.
     * @param  blockState The {@link BlockState} of the block being collided with.
     * @param  callback   The {@link CallbackInfo} object to handle the return state
     */
    public static void handleCollision(final World world, final BlockPos pos, final Entity entity, final BlockState blockState, final CallbackInfo callback)
    {
        if (entity instanceof LivingEntity) {
            final Optional<BlockConfig> maybeBlockcfg = FallThru.BLOCK_CONFIG_MAP.getConfig(blockState.getBlock());
            if (maybeBlockcfg.isPresent()) {

                final BlockConfig   blockcfg = maybeBlockcfg.get();
                final LivingEntity  living   = (LivingEntity) entity;
                final AxisAlignedBB entitybb = entity.getBoundingBox();
                final VoxelShape    blockvs  = blockState.getShape(world, pos);
                final AxisAlignedBB blockbb  = blockvs.isEmpty() ? VoxelShapes.fullCube().getBoundingBox() : blockvs.getBoundingBox().offset(pos);
                // shrink the entity bounding box a bit so that it has to be more inside of a block to trigger collisions
                if (entitybb.grow(-0.1, 0, -0.1).intersects(blockbb)) {
                    // handle falling into blocks
                    final SoundType soundtype = blockState.getSoundType();
                    if (living.fallDistance > 3f) {
                        living.playSound(soundtype.getBreakSound(), soundtype.getVolume(), soundtype.getPitch() * 0.65f);
                        final int dmgThresh = FallThru.COMMON_CONFIG.damageThreshold.get();
                        if (living.fallDistance > 3f + dmgThresh) {
                            final EffectInstance jbeffect = living.getActivePotionEffect(Effects.JUMP_BOOST);
                            final double jumpBoost = jbeffect == null ? 0.0 : jbeffect.getAmplifier() + 1;
                            final double dmgMult   = blockcfg.getDamageMult();
                            final double damage    = getDamage(living.fallDistance, dmgThresh, jumpBoost, dmgMult);
                            if (damage > 0) {
                                living.attackEntityFrom(getDamageSource(blockState), (float)damage);
                                if (living.isBeingRidden()) {
                                    // recursive call for riders
                                    living.getRecursivePassengers().forEach(ent ->
                                        ent.attackEntityFrom(getDamageSource(blockState), (float)(damage * 0.8)));
                                }
                            }

                            // handle block breaking
                            final boolean creative = living instanceof PlayerEntity && ((PlayerEntity)living).isCreative();
                            final boolean negationEffect = living.getActivePotionEffect(Effects.LEVITATION) != null | living.getActivePotionEffect(Effects.SLOW_FALLING) != null;
                            if (!world.isRemote && !creative && FallThru.COMMON_CONFIG.doBlockBreaking.get() && !negationEffect) {
                                // slightly attenuate the entity bounding box to below the entity for block breaking
                                final AxisAlignedBB breakbb = entitybb.offset(0, -0.5, 0);
                                BlockPos.getAllInBox(new BlockPos(breakbb.minX, breakbb.minY, breakbb.minZ), new BlockPos(breakbb.maxX, breakbb.maxY, breakbb.maxZ))
                                    .filter(filtpos -> BREAKABLE_FILTER.test(world, filtpos))
                                    // TODO: Maybe add a config setting for the chance to break blocks
                                    .filter(ignored -> GET_RANDOM.test(3))
                                    .forEach(despos -> world.destroyBlock(despos, true));
                            }
                        }
                    }

                    // decay fall distance
                    final double speedMult = blockcfg.getSpeedMult();
                    living.fallDistance = living.fallDistance > 3.0f ? living.fallDistance * (float) speedMult : 0f;

                    // handle collision sounds; Only play sounds if the entity is moving; once every 5 ticks.
                    final Vector3d motion = living.getMotion();
                    if (isMoving(motion) && world.getGameTime() % 5 == 0) {
                        living.playSound(soundtype.getStepSound(), soundtype.getVolume(), soundtype.getPitch() * 0.75f);
                    }

                    // reduce motion based on BlockConfig properties; allow jumping entities to be able to jump 1 block high
                    if (living.isJumping) {
                        living.setMotion(motion.mul(Math.sqrt(speedMult), 1.0, Math.sqrt(speedMult)));
                    } else {
                        living.setMotion(motion.mul(speedMult, speedMult, speedMult));
                    }
                }

                // Set the callback to cancel if the Block is configured to override the default behaviour.
                if (!blockcfg.allowNative()) {
                    callback.cancel();
                }
            }
        }
    }
}
