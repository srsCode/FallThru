/*
 * Project      : FallThru
 * File         : CommonConfig.java
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

import srscode.fallthru.BlockConfigMap.BlockConfig;

/**
 * The main configuration class for FallThru.
 */
public final class CommonConfig
{
    private static final Marker MARKER_CONFIG                     = MarkerFactory.getMarker("CONFIG");
    private static final String LANGKEY_CONFIG                    = "config";
    private static final String LANGKEY_SETTING_BLOCKBREAK        = "doBlockBreaking";
    private static final String LANGKEY_SETTING_DAMAGETHRESHOLD   = "damageThreshold";
    private static final String LANGKEY_SETTING_PASSABLEBLOCKS    = "passableBlocks";
    private static final String LANGKEY_SETTING_BLACKLISTBLOCKS   = "blacklistBlocks";
    private static final Predicate<Object> RESLOC_VALIDATOR       = s -> s instanceof String rl && ResourceLocation.tryParse(rl) != null;
    private static final Supplier<String> PASSABLE_DEFAULT        = () -> "#" + BlockTags.LEAVES.location() + "[0.8, 0.8]";
    private static final Supplier<Registry<Block>> BLOCK_REGISTRY = () -> BuiltInRegistries.BLOCK;

    final IntValue     damageThreshold;
    final BooleanValue doBlockBreaking;

    final ConfigValue<List<? extends CharSequence>> passableBlocks;
    final ConfigValue<List<? extends CharSequence>> blacklistBlocks;

    CommonConfig(final Builder builder)
    {
        builder.comment("  FallThru Config").push(FallThru.modId());

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

        final var passableBlocksDefault = PASSABLE_DEFAULT.get();
        this.passableBlocks = builder
            .comment(
                "",
                "  A list of configurations for Blocks to allow passing through. A configuration is formatted as fallows:",
                "",
                "    #<ResLoc>[speedmult, damagemult, true]",
                "",
                "  Where:",
                "    #           - (Optional) Prepending the resource location with a '#' signifies that the resource location is a reference",
                "                  to a Tag representing multiple Blocks. Without a '#' the reference is assumed to be for a single Block.",
                "    <ResLoc>    - is an legitimate ResourceLocation for a Tag or Block (eg: '#minecraft:leaves' (Tag for all leaves),",
                "                  or: 'minecraft:oak_leaves' (Block).",
                "    speedMult   - The speed reduction multiplier expressed in decimal. Valid range: 0.05 - 1.0",
                "                  How much entities slow down when moving through the Block(s).",
                "    damageMult  - The damage reduction multiplier expressed in decimal. Valid range: 0.05 - 1.0",
                "                  The percentage of damage dealt when falling into the Block(s).",
                "    true        - (Optional) Add 'true' to also execute the native collision handling",
                "                  for this block. Default: false (omitted).",
                "",
                "  Example entries:",
                "    \"biomesoplenty:orange_autumn_leaves[0.75, 0.6, true]\"",
                "    \"#minecraft:leaves[0.8, 0.6]\"",
                "",
                "  Notes:",
                "    1. Entries for Blocks have precedence over Tags so that you can override the configuration of a specific Block that is also",
                "       the member of a Tag. Entries for Blocks are processed first and any subsequent entry for the same block will be ignored.",
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
                "  This list is in addition to blocks that are permanently blacklisted by default.",
                "",
                "  The default permanently blacklisted Blocks are as fallows:",
                "    Blocks: " + BlockConfigMap.BLACKLIST_BLOCKS.stream().map(b -> "" + BLOCK_REGISTRY.get().getResourceKey(b).map(ResourceKey::location)).collect(Collectors.joining(", ")),
                "    All blocks in the BlockTags: " + BlockConfigMap.BLACKLIST_TAGS.stream().map(t -> "#" + t.location()).collect(Collectors.joining(", "))
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
        return (keys.length > 0) ? String.join(".", FallThru.modId(), String.join(".", keys)) : FallThru.modId();
    }

    Collection<String> getPassableBlocks()
    {
        return passableBlocks.get().stream().map(CharSequence::toString).map(String::trim).collect(Collectors.toSet());
    }

    Collection<String> getBlacklistBlocks()
    {
        return blacklistBlocks.get().stream().map(CharSequence::toString).map(String::trim).collect(Collectors.toSet());
    }

    /**
     * This Event handler syncs the {@link BlockConfigMap} with {@link CommonConfig#passableBlocks}.
     * This fires when the config file has been changed on disk and only updates on the client if
     * the client is <b>not</b> connected to a remote server (the client should <b>not</b> override
     * it's current BlockConfigMap that was sent to it from the remote server), or if the integrated
     * server <b>is</b> running, since Tags won't be populated from datapacks atleast until the server
     * is starting, at which time the syncing will occur.
     * This will always cause syncing on a dedicated server that will propogate to clients.
     *
     * @param event The {@link ModConfigEvent.Reloading} event
     */
    void onConfigUpdate(final ModConfigEvent.Reloading event)
    {
        if (event.getConfig().getModId().equals(FallThru.modId())) {
            if (FMLEnvironment.dist == Dist.CLIENT && (Minecraft.getInstance().getSingleplayerServer() == null || Minecraft.getInstance().getConnection() != null)) {
                FallThru.logger().debug(CommonConfig.MARKER_CONFIG, "The config file has changed but the integrated server is not running. Nothing to do.");
            } else {
                FallThru.logger().debug(CommonConfig.MARKER_CONFIG, "The config file has changed and the server is running. Resyncing config.");
                FallThru.blockConfigs().syncLocal();
            }
        }
    }

}
