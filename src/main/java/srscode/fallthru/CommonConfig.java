/*
 * Project      : FallThru
 * File         : CommonConfig.java
 * Last Modified: 20190914-03:05:53-0400
 *
 * Copyright (c) 2019 srsCode, srs-bsns (forfrdm [at] gmail.com)
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.tags.BlockTags;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

import srscode.fallthru.BlockConfigMap.BlockConfig;

/**
 * The main configuration class for FallThru.
 */
final class CommonConfig
{
    private static final String LANGKEY_CONFIG                  = "config";
    private static final String LANGKEY_SETTING_BLOCKBREAK      = "doBlockBreaking";
    private static final String LANGKEY_SETTING_DAMAGETHRESHOLD = "damageThreshold";
    private static final String LANGKEY_SETTING_PASSABLEBLOCKS  = "passableBlocks";
    private static final String LANGKEY_SETTING_BLACKLISTBLOCKS = "blacklistBlocks";
    private static final Predicate<Object> RESLOC_VALIDATOR     = s -> {
        if (s instanceof String && ResourceLocation.tryCreate((String)s) != null) {
            return true;
        } else {
            FallThru.LOGGER.error(FallThru.MARKER_CONFIG, "Invalid ResourceLocation for blacklist: {}", s);
            return false;
        }
    };

    final IntValue     damageThreshold;
    final BooleanValue doBlockBreaking;

    final ConfigValue<List<? extends CharSequence>> passableBlocks;
    final ConfigValue<List<? extends CharSequence>> blacklistBlocks;

    CommonConfig(final Builder builder)
    {
        final String passableBlocksDefault = "tag/" + BlockTags.LEAVES.getId() + "[0.8, 0.8]";

        builder.comment("  FallThru Config").push(FallThru.MOD_ID);

        this.damageThreshold = builder
            .comment(
                "",
                "  This setting controls the extra distance that an entity needs to fall before taking damage from falling into passable",
                "  blocks. This is *in addition to* the 3-block distance of vanilla, before factoring in the Jump Boost effect applied",
                "  from a potion or enchanted item. The formula for calculating the full damage threshold is: (VT + DT) * ((VT + EB) / VT)",
                "  Where:",
                "         VT is the vanilla default fall damage threshold of 3 blocks.",
                "         DT is the value of this setting.",
                "         EB is the effect bonus from the number of levels of Jump Boost applied.",
                "",
                "  The value from this calculation will be removed from the fall distance before calculating damage.",
                "  If this setting is disabled (setting it to 0), then the vanilla formula will apply: VT + EB"
            )
            .translation(getLangKey(LANGKEY_CONFIG, LANGKEY_SETTING_DAMAGETHRESHOLD))
            .defineInRange(LANGKEY_SETTING_DAMAGETHRESHOLD, 7, 0, 27);

        this.doBlockBreaking = builder
            .comment(
                "",
                "  Allow random block breaking when falling into passable blocks from a height that would cause fall damage,",
                "  dropping their normal harvest items. (eg: Falling into leaves would drop saplings and/or sticks.)",
                "",
                "  Note: Entities with the levitation or slow_falling potion effect will never break blocks."
            )
            .translation(getLangKey(LANGKEY_CONFIG, LANGKEY_SETTING_BLOCKBREAK))
            .define(LANGKEY_SETTING_BLOCKBREAK, true);

        this.passableBlocks = builder
            .comment(
                "",
                "  A list of configurations for Blocks to allow passing through. A configuration is formatted as fallows:",
                "",
                "    <block|tag>/<ResLoc>[speedmult, damagemult, true]",
                "",
                "  Where:",
                "    <block|tag> - signifies if this is an entry for a single Block, or for a Tag representing multiple Blocks.",
                "    <ResLoc>    - is an legitimate ResourceLocation for a Block or Tag (eg: 'minecraft:oak_leaves' (Block),",
                "                  or: 'minecraft:leaves' (Tag for all leaves).",
                "    speedMult   - The speed reduction multiplier expressed in decimal. Valid range: 0.05 - 1.0",
                "                  How much entities slow down when moving through this Block.",
                "    damageMult  - The damage reduction multiplier expressed in decimal. Valid range: 0.05 - 1.0",
                "                  The percentage of damage dealt when falling into the Block(s).",
                "    true        - (Optional) Add 'true' to also execute the native collision handling",
                "                  for this block. Default: false (omitted).",
                "",
                "  Example entries:",
                "    \"block/biomesoplenty:orange_autumn_leaves[0.75, 0.6, true]\"",
                "    \"tag/minecraft:leaves[0.8, 0.6]\"",
                "",
                "  Notes:",
                "    1. Tags are processed first so that you can override the configuration of a specific Block that has",
                "       already been configured as a member of a Tag.",
                "    2. You can add blocks that are already passable, such as tall grass, in order to apply a speed reduction,",
                "       and to also have the block's natural sound play when moving through it.",
                "    3. Strings that don't match this pattern will be invalidated and removed from the config.",
                "",
                "  Default: [\"" + passableBlocksDefault + "\"]"
            )
            .translation(getLangKey(LANGKEY_CONFIG, LANGKEY_SETTING_PASSABLEBLOCKS))
            .defineList(LANGKEY_SETTING_PASSABLEBLOCKS, Arrays.asList(passableBlocksDefault), BlockConfig.CONFIGSTR_VALIDATOR);

        this.blacklistBlocks = builder
            .comment(
                "",
                "  An additional list of blocks to blacklist and never allow passing through.",
                "  This list is in addition to blocks that are blacklisted by default.",
                "",
                "  The default blacklisted Blocks are as fallows:",
                "    All blocks of the Material types: AIR, BUBBLE_COLUMN, FIRE, PISTON, PORTAL, STRUCTURE_VOID",
                "    Other Blocks: minecraft:bedrock, minecraft:end_portal_frame, minecraft:hay_block, minecraft:slime_block, all beds"
            )
            .translation(getLangKey(LANGKEY_CONFIG, LANGKEY_SETTING_BLACKLISTBLOCKS))
            .defineList(LANGKEY_SETTING_BLACKLISTBLOCKS, Collections.emptyList(), RESLOC_VALIDATOR);

        builder.pop();
    }

    /**
     * A I18n key helper.
     *
     * @param  keys A list of key elements to be joined.
     * @return      A full I18n key.
     */
    private static String getLangKey(final String... keys)
    {
        return (keys.length > 0) ? String.join(".", FallThru.MOD_ID, String.join(".", keys)) : FallThru.MOD_ID;
    }

    Collection<String> getPassableBlocks()
    {
        return passableBlocks.get().stream().map(CharSequence::toString).collect(Collectors.toList());
    }

    Collection<String> getBlacklistBlocks()
    {
        return blacklistBlocks.get().stream().map(CharSequence::toString).collect(Collectors.toList());
    }
}
