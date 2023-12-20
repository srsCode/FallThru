/*
 * Project      : FallThru
 * File         : RedirectionHandler.java
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

package srscode.fallthru;

import java.util.Random;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import srscode.fallthru.BlockConfigMap.BlockConfig;
import srscode.fallthru.mixin.Accessors;

/**
 * A class to handle the collision of {@link Entity}s with {@link BlockState}s in the world.
 */
public final class RedirectionHandler
{
    private RedirectionHandler() {}

    private static final Random RANDOM       = new Random();
    private static final int    BREAK_CHANCE = 3;

    /**
     * This method determines if an {@link LivingEntity} is moving based on the Vec3d from {@link Entity#getDeltaMovement}, adjusting
     * for a y-level drift inherent to players when looking around the mouse axis.
     *
     * This is used to determine whether or not to play a sound when the entity is moving through certain blocks.
     *
     * @param  motion The motion of the entity from {@link Entity#getDeltaMovement}.
     * @return        <b>true</b> if the entity is moving, <b>false</b> if not.
     */
    private static boolean isMoving(final Vec3 motion)
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
     * The Collision handler.
     * This method is called from the mixin injection point in {@link BlockBehaviour.BlockStateBase#entityInside}.
     *
     * @param  level      The {@link Level} of the Block being collided.
     * @param  pos        The {@link Vec3} coordinates of the BlockState being collided with.
     * @param  entity     The {@link Entity} colliding with the BlockState.
     * @param  blockState The {@link BlockState} of the block being collided with.
     * @param  callback   The {@link CallbackInfo} object to handle the return state
     */
    public static void handleCollision(final Level level, final BlockPos pos, final LivingEntity entity, final BlockState blockState, final BlockConfig blockConfig, final CallbackInfo callback)
    {
        final var entitybb = entity.getBoundingBox();
        final var blockvs  = blockState.getShape(level, pos);
        final var blockbb  = blockvs.isEmpty() ? Shapes.block().bounds() : blockvs.bounds().move(pos);
        // shrink the entity bounding box a bit so that it has to be more inside of a block to trigger collisions
        if (entitybb.inflate(-0.1, 0, -0.1).intersects(blockbb)) {
            // handle falling into blocks
            final var soundtype = blockState.getSoundType();
            if (entity.fallDistance > 3f) {
                entity.playSound(soundtype.getBreakSound(), soundtype.getVolume(), soundtype.getPitch() * 0.65f);
                final int dmgThresh = FallThru.config().damageThreshold.get();
                if (entity.fallDistance > 3f + dmgThresh) {
                    final var jumpeffect = entity.getEffect(MobEffects.JUMP);
                    final var damage = getDamage(entity.fallDistance, dmgThresh, (jumpeffect == null ? 0.0 : jumpeffect.getAmplifier() + 1), blockConfig.damageMult());
                    if (damage > 0) {
                        entity.hurt(entity.damageSources().fall(), (float) damage);
                        if (entity.isVehicle()) {
                            // recursive call for riders
                            entity.getIndirectPassengers().forEach(rider -> rider.hurt(rider.damageSources().fall(), (float) (damage * 0.8)));
                        }
                    }

                    // handle block breaking
                    final var creative = entity instanceof Player && ((Player) entity).isCreative();
                    final var negationEffect = entity.getEffect(MobEffects.LEVITATION) != null | entity.getEffect(MobEffects.SLOW_FALLING) != null;
                    if (!level.isClientSide && !creative && FallThru.config().doBlockBreaking.get() && !negationEffect) {
                        // slightly attenuate the entity bounding box to below the entity for block breaking
                        final var breakbb = entitybb.move(0, -0.2, 0);
                        BlockPos.betweenClosedStream(breakbb)
                            .filter(filtpos -> {
                                final var blockstate = level.getBlockState(filtpos);
                                return !blockstate.isAir()
                                    && FallThru.blockConfigs().containsKey(blockstate.getBlock())
                                    && RANDOM.nextInt(BREAK_CHANCE) == 0;
                            })
                            .forEach(despos -> level.destroyBlock(despos, true));
                    }
                }
            }

            // decay fall distance
            final var speedMult = blockConfig.speedMult();
            entity.fallDistance = entity.fallDistance > 3.0f ? entity.fallDistance * (float) speedMult : 0f;

            // handle collision sounds; Only play sounds if the entity is moving; once every 5 ticks.
            final var motion = entity.getDeltaMovement();
            if (isMoving(motion) && level.getGameTime() % 5 == 0) {
                entity.playSound(soundtype.getStepSound(), soundtype.getVolume(), soundtype.getPitch() * 0.75f);
            }

            // reduce motion based on BlockConfig properties; allow jumping entities to be able to jump 1 block high
            if (((Accessors.LivingEntityAccessor) entity).getJumping()) {
                final var sqrtmult = Math.sqrt(speedMult);
                entity.setDeltaMovement(motion.multiply(sqrtmult, 1.0, sqrtmult));
            } else {
                entity.setDeltaMovement(motion.multiply(speedMult, speedMult, speedMult));
            }
        }

        // Set the callback to cancel if the Block is configured to override the default behaviour.
        if (!blockConfig.allowNative()) {
            callback.cancel();
        }
    }
}
