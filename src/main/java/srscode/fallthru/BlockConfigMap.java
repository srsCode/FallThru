/*
 * Project      : FallThru
 * File         : BlockConfigMap.java
 * Last Modified: 20210703-10:12:49-0400
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

package srscode.fallthru;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.tags.Tag;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.ForgeRegistries;

import srscode.fallthru.BlockConfigMap.BlockConfig;
import srscode.fallthru.mixin.Accessors;

/**
 * This {@link Map} class stores {@link BlockConfig} objects using the hashcode of their {@link Block} as keys.
 * This is an extension of {@link Int2ObjectArrayMap} for better efficiency, since this map is likely to be quite small,
 * and it will have quite a high level of accesses.
 */
@SuppressWarnings("WeakerAccess")
public final class BlockConfigMap extends Int2ObjectArrayMap<BlockConfig>
{
    private static final long serialVersionUID = 4723499327562438886L;

    private static final Marker MARKER_BLOCKCFG = MarkerManager.getMarker("BLOCK CONFIG");
    private static final String NBT_CONFIG_TAG  = "blocklist";

    private static final Function<String, Block> BLOCK_RESOLVER = cfgblock -> {
        final ResourceLocation resloc;
        if ((resloc = ResourceLocation.tryParse(cfgblock)) != null && ForgeRegistries.BLOCKS.containsKey(resloc)) {
            return ForgeRegistries.BLOCKS.getValue(resloc);
        } else {
            FallThru.LOGGER.error(MARKER_BLOCKCFG, "Block in blacklist does not exist: {}", resloc);
            return null;
        }
    };

    private static final transient Collection<Block> BLACKLIST_BLOCKS = new HashSet<>();

    private static final Predicate<Block> BLACKLISTED_MATERIALS = block -> {
        final Material material = block.defaultBlockState().getMaterial();
        return material == Material.AIR    || material == Material.BUBBLE_COLUMN
            || material == Material.FIRE   || material == Material.PISTON
            || material == Material.PORTAL || material == Material.STRUCTURAL_AIR;
    };

    BlockConfigMap() {}

    /**
     * This will initialize the {@link Block} blacklist wih hardcoded entries and from
     * the{@link CommonConfig#blacklistBlocks} config setting.
     */
    void refreshBlacklist()
    {
        BLACKLIST_BLOCKS.clear();

        // Hard blacklist Blocks with specific Material types that should only be handled by vanilla.
        ForgeRegistries.BLOCKS
            .getValues()
            .stream()
            .filter(BLACKLISTED_MATERIALS)
            .forEach(BLACKLIST_BLOCKS::add);

        // Hard blacklist blocks with unique uses, or properties such as unique damage multipliers, that should be handled by vanilla only.
        BLACKLIST_BLOCKS.addAll(Arrays.asList(
            Blocks.BEDROCK,    Blocks.END_PORTAL_FRAME, Blocks.HAY_BLOCK,      Blocks.SLIME_BLOCK,    Blocks.HONEY_BLOCK,
            Blocks.BLACK_BED,  Blocks.BLUE_BED,         Blocks.BROWN_BED,      Blocks.CYAN_BED,
            Blocks.GRAY_BED,   Blocks.GREEN_BED,        Blocks.LIGHT_BLUE_BED, Blocks.LIGHT_GRAY_BED,
            Blocks.LIME_BED,   Blocks.MAGENTA_BED,      Blocks.ORANGE_BED,     Blocks.PINK_BED,
            Blocks.PURPLE_BED, Blocks.RED_BED,          Blocks.WHITE_BED,      Blocks.YELLOW_BED
        ));

        /*
         * Populate the blacklist from CommonConfig#blacklistBlocks
         */
        BLACKLIST_BLOCKS.addAll(FallThru.config().getBlacklistBlocks().stream().map(BLOCK_RESOLVER).collect(Collectors.toSet()));
    }

    /**
     * A parser for {@link CommonConfig#passableBlocks} that will convert the config string entries into
     * instances of {@link BlockConfig}. The strings are validated via the RegExps found in {@link BlockConfig}
     * and all errors are logged.
     *
     * @param configBlocks A List of string entries from {@link CommonConfig#passableBlocks}.
     * @return             A Collection (List in case there are dupes) of all valid BlockConfig objects.
     */
    Collection<BlockConfig> parseConfig(final Collection<String> configBlocks)
    {
        return configBlocks.stream()
            // The CONFIGSTR_VALIDATOR is used to filter the list in CommonConfig.passableBlocks but can remain here as added safety.
            .filter(BlockConfig.CONFIGSTR_VALIDATOR)
            .sorted(BlockConfig.CFGSTR_SORTER)
            .sequential()
            .map(BlockConfig.BLOCKCONFIG_BUILDER)
            .flatMap(Collection::stream)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * When this method is called, it will resync this BlockConfigMap with {@link CommonConfig#passableBlocks}
     * If this is a dedicated server, dispatch a {@link S2CFallThruUpdatePacket}.
     */
    void syncLocal()
    {
        refreshBlacklist();
        FallThru.LOGGER.debug(MARKER_BLOCKCFG, "Syncing local config");
        clear();
        addAll(parseConfig(FallThru.config().getPassableBlocks()));
        // if this is a dedicated server, dispatch a S2CFallThruUpdatePacket.
        DistExecutor.safeRunWhenOn(Dist.DEDICATED_SERVER, () -> NetworkHandler.INSTANCE::updateAll);
    }

    /**
     * Syncs this BlockConfigMap from a {@link Collection} of {@link BlockConfig}s passed
     * from {@link S2CFallThruUpdatePacket#handle}.
     *
     * @param blockConfigs A Collection of BlockConfigs.
     */
    void syncFromRemote(final CompoundNBT blockConfigs)
    {
        FallThru.LOGGER.debug(MARKER_BLOCKCFG, "Syncing from remote config");
        clear();
        fromNBT(blockConfigs);
    }

    /**
     * A serializer to convert all of the {@link BlockConfig}s in this map to a CompoundNBT tag used for network traversal.
     *
     * @return A {@link CompoundNBT} representation of this {@code BlockConfigMap}s contents.
     */
    CompoundNBT toNBT()
    {
        final CompoundNBT ret = new CompoundNBT();
        ret.put(NBT_CONFIG_TAG, values().stream().map(BlockConfig::toNBT).collect(Collectors.toCollection(ListNBT::new)));
        return ret;
    }

    /**
     * A deserializer to deserialize a {@code BlockConfigMap}s contents received from a remote server.
     *
     * @param blocklist A {@link CompoundNBT} representation of a {@code BlockConfigMap}.
     */
    void fromNBT(final CompoundNBT blocklist)
    {
        addAll(
            blocklist
                .getList(NBT_CONFIG_TAG, Constants.NBT.TAG_COMPOUND)
                .stream()
                .map(CompoundNBT.class::cast)
                .map(BlockConfig::fromNBT)
                .collect(Collectors.toList())
        );
    }

    /**
     * A shortcut for {@link #put} that takes a {@link BlockConfig}.
     *
     * @param blockConfig The BlockConfig object to be added.
     */
    void add(@Nonnull final BlockConfig blockConfig)
    {
        put(getHash(blockConfig.getBlock()), blockConfig);
        updateBlock(blockConfig, true);
    }

    /**
     * A shortcut to add all {@link BlockConfig} objects from a {@link Collection} proxied through {@link #add}.
     *
     * @param blockConfigList A Collection of BlockConfig objects.
     */
    void addAll(@Nonnull final Collection<BlockConfig> blockConfigList)
    {
        blockConfigList.forEach(this::add);
    }

    /**
     * A helper method to remove a {@link BlockConfig} from the map by it's {@link Block}.
     * This will also reset the relevant Block to its initial state.
     *
     * @param block The Block of a BlockConfig to be removed.
     */
    Optional<BlockConfig> remove(@Nonnull final Block block)
    {
        final BlockConfig removed = super.remove(getHash(block));
        if (removed != null) {
            updateBlock(removed, false);
            return Optional.of(removed);
        }
        return Optional.empty();
    }

    /**
     * A proxy to {@link BlockConfig#remove(Block)}.
     *
     * @param blockConfig The BlockConfig to remove.
     */
    @SuppressWarnings("unused")
    void remove(final BlockConfig blockConfig)
    {
        remove(blockConfig.getBlock());
    }

    /**
     *  A helper method for updating blocks upon being added or removed. This is dependent on the {@link Accessors.AbstractBlockStateAccessor} mixin.
     *
     *  @param blockConfig The BlockConfig of the block being added or removed.
     *  @param adding      A boolean signifying if this BlockConfig is being added or removed. (true = added, false = removed)
     */
    private void updateBlock(final BlockConfig blockConfig, final boolean adding)
    {
        final Block block = blockConfig.getBlock();
        ((Accessors.AbstractBlockAccessor)block).setHasCollision(!adding && blockConfig.hasCollision());
        ((Accessors.AbstractBlockStateAccessor)block.defaultBlockState()).setCanOcclude(!adding && blockConfig.canOcclude());
    }

    /**
     * A helper method to determine if a key exists in the BlockConfigMap by a {@link Block}.
     *
     * @param  block The {@link Block} of a BlockConfig to find a key for.
     * @return Whether or not a key exists.
     */
    public boolean hasKey(final Block block)
    {
        return containsKey(getHash(block));
    }

    /**
     * A shortcut to get a {@link BlockConfig} directly from its associated {@link Block}.
     *
     * @param  block The Block to retrieve a BlockConfig for.
     * @return An {@link Optional} of the BlockConfig if present, otherwise {@link Optional#empty}
     */
    public Optional<BlockConfig> getConfig(@Nonnull final Block block)
    {
        return Optional.ofNullable(get(getHash(block)));
    }

    /**
     * A helper to get the hashCode of a {@link Block}.
     *
     * @param  block The Block to get a {@link #hashCode} for.
     * @return       The hashcode.
     */
    private static int getHash(@Nonnull final Block block)
    {
        return Objects.requireNonNull(block.getRegistryName()).hashCode();
    }

    /**
     * A class to store the configuration of passable blocks.
     * With the exception of the {@link Block} referenced, all of the class members are immutable.
     */
    public static final class BlockConfig
    {
        /**
         * An enum for discerning if a configuration string from {@link CommonConfig#passableBlocks}
         * is for a {@link Tag} or a {@link Block}.
         */
        enum ItemType
        {
            BLOCK, TAG;

            @Nullable
            static BlockConfig.ItemType get(final String type)
            {
                // LOOKUPSWITCH.. sheesh.
                switch (type.toLowerCase(Locale.ROOT)) {
                    case "block": return BLOCK;
                    case "tag"  : return TAG;
                    default     : return null;
                }
            }
        }

        // NBT name constants
        static final String ORIG_HAS_COLLISION = "hasCollision";
        static final String ORIG_CAN_OCCLUDE   = "canOcclude";

        // named group constants
        static final String GROUP_ITEMTYPE    = "itemtype";
        static final String GROUP_RESLOC      = "resloc";
        static final String GROUP_ITEM        = "item";
        static final String GROUP_VALUES      = "values";
        static final String GROUP_DMGMULT     = "dmgmult";
        static final String GROUP_SPMULT      = "spmult";
        static final String GROUP_ALLOWNATIVE = "allowdef";

        // regexp patterns
        static final String PATTERN_ITEMTYPE = "(?<" + GROUP_ITEMTYPE + ">([bB][lL][oO][cC][kK]|[tT][aA][gG]))";
        static final String PATTERN_RESLOC   = "(?<" + GROUP_RESLOC + ">\\w+:\\w+)";
        static final String PATTERN_ITEM     = "(?<" + GROUP_ITEM + ">" + PATTERN_ITEMTYPE + "\\s?\\/\\s?" + PATTERN_RESLOC + ")";
        static final String PATTERN_FLOAT    = "(\\d+(\\.\\d+)?)";
        static final String PATTERN_SPMULT   = "(?<" + GROUP_SPMULT + ">" + PATTERN_FLOAT + ")";
        static final String PATTERN_DMGMULT  = "(?<" + GROUP_DMGMULT + ">" + PATTERN_FLOAT + ")";
        static final String PATTERN_ALLOWDEF = "(\\s*\\,\\s*(?<" + GROUP_ALLOWNATIVE + ">([tT][rR][uU][eE]|[fF][aA][lL][sS][eE])))?";
        static final String PATTERN_VALUES   = "(?<" + GROUP_VALUES + ">(\\[\\s*" + PATTERN_SPMULT + "\\s*\\,\\s*" + PATTERN_DMGMULT + PATTERN_ALLOWDEF + "\\s*\\]))";
        static final String PATTERN_CFGSTR   = "^\\s*" + PATTERN_ITEM + "\\s*" + PATTERN_VALUES + "\\s*$";

        /**
         * A config string validator using the {@link BlockConfig#PATTERN_CFGSTR} RegExp pattern as a {@link Predicate}.
         */
        static final Predicate<Object> CONFIGSTR_VALIDATOR = cfgstr -> {
            if (cfgstr instanceof String && Pattern.compile(BlockConfig.PATTERN_CFGSTR).asPredicate().test((String) cfgstr)) {
                return true;
            }
            FallThru.LOGGER.error(MARKER_BLOCKCFG, "Erroneous config entry for passableBlocks: {}", cfgstr);
            return false;
        };

        /**
         * A {@link Comparator} to sort the config strings so that all {@link Tag} entries are processed before {@link Block}
         * entries, so that if a Block entry is also the member of a Tag entry, the singular Block entry will take precedence
         * and override the Tag entry. This is so that users can special-case some blocks if they want to.
         */
        static final Comparator<String> CFGSTR_SORTER = (cfgstr1, cfgstr2) -> {
            final Pattern pattern = Pattern.compile("^\\s*" + BlockConfig.PATTERN_ITEMTYPE + "\\/\\w.*$", Pattern.CASE_INSENSITIVE);
            final Matcher matcher1 = pattern.matcher(cfgstr1);
            final Matcher matcher2 = pattern.matcher(cfgstr2);
            // No sense in verifying as the strings have already gone through the validator.
            matcher1.matches();
            matcher2.matches();
            final ItemType type1 = Objects.requireNonNull(ItemType.get(matcher1.group(BlockConfig.GROUP_ITEMTYPE)));
            final ItemType type2 = Objects.requireNonNull(ItemType.get(matcher2.group(BlockConfig.GROUP_ITEMTYPE)));
            return Integer.compare(type2.ordinal(), type1.ordinal());
        };

        /**
         * This is a {@link Function} to process a validated, sorted, list of config string entries and
         * return a Set of BlockConfigs to be added to the {@link BlockConfigMap}.
         */
        static final Function<String, Collection<BlockConfig>> BLOCKCONFIG_BUILDER = cfgstr -> {

            final Matcher matcher = Pattern.compile(BlockConfig.PATTERN_CFGSTR, Pattern.CASE_INSENSITIVE).matcher(cfgstr);
            matcher.matches();

            final BlockConfig.ItemType type = BlockConfig.ItemType.get(matcher.group(BlockConfig.GROUP_ITEMTYPE));
            if (type == null) {
                FallThru.LOGGER.error(MARKER_BLOCKCFG, "Illegal entry type. Entry must begin with 'block/' or 'tag/'; Skipping: {}", matcher.group(BlockConfig.GROUP_ITEMTYPE));
                return Collections.emptySet();
            }
            final ResourceLocation resloc = ResourceLocation.tryParse(matcher.group(BlockConfig.GROUP_RESLOC));
            if (resloc == null) {
                FallThru.LOGGER.error(MARKER_BLOCKCFG, "Illegal ResourceLocation for config entry; Skipping: {}", matcher.group(BlockConfig.GROUP_RESLOC));
                return Collections.emptySet();
            }
            final double  spmult   = Double.parseDouble(matcher.group(BlockConfig.GROUP_SPMULT));
            final double  dmgmult  = Double.parseDouble(matcher.group(BlockConfig.GROUP_DMGMULT));
            // If a boolean value wasn't provided for native handling, default to 'false' (prevent native handling).
            final boolean allowdef = matcher.group(BlockConfig.GROUP_ALLOWNATIVE) != null && Boolean.parseBoolean(matcher.group(BlockConfig.GROUP_ALLOWNATIVE));

            final Collection<Block> blocks = Sets.newHashSet();
            switch (type) {
                case BLOCK:
                    final Block cfgblock;
                    if (!ForgeRegistries.BLOCKS.containsKey(resloc)) {
                        FallThru.LOGGER.error(MARKER_BLOCKCFG, "Block not found in the Block registry; Skipping: {}", resloc);
                    } else if (BLACKLIST_BLOCKS.contains((cfgblock = ForgeRegistries.BLOCKS.getValue(resloc)))) {
                        FallThru.LOGGER.error(MARKER_BLOCKCFG, "Block is blacklisted; Skipping: {}", resloc);
                    } else {
                        blocks.add(cfgblock);
                    }
                    break;

                case TAG:
                    final Collection<Block> tagBlocks = ForgeRegistries.BLOCKS.getValues().stream()
                        .filter(tagBlock -> tagBlock.getTags().contains(resloc))
                        .filter(tagBlock -> {
                            if (BLACKLIST_BLOCKS.contains(tagBlock)) {
                                FallThru.LOGGER.error(MARKER_BLOCKCFG, "Block is blacklisted; Skipping: {}", tagBlock.getRegistryName());
                                return false;
                            } else {
                                return true;
                            }
                        })
                        .collect(Collectors.toSet());
                    if (tagBlocks.isEmpty()) {
                        FallThru.LOGGER.error(MARKER_BLOCKCFG, "Could not find any blocks for Tag, or Tag does not exist: {}", resloc);
                    }
                    blocks.addAll(tagBlocks);
                    break;
            }

            return blocks
                .stream()
                .map(block -> BlockConfig.create(block, spmult, dmgmult, allowdef))
                .collect(Collectors.toSet());
        };

        private final Block   block;
        private final double  speedMult;
        private final double  damageMult;
        private final boolean allowNative;
        private final boolean hasCollision;
        private final boolean canOcclude;

        private BlockConfig(@Nonnull final Block block, final double speedMult, final double damageMult, final boolean allowNative, final boolean hasCollision, final boolean canOcclude)
        {
            this.block        = Objects.requireNonNull(block, "Can not create a BlockConfig without a Block.");
            this.speedMult    = speedMult;
            this.damageMult   = damageMult;
            this.allowNative  = allowNative;
            this.hasCollision = hasCollision;
            this.canOcclude   = canOcclude;
        }

        /**
         * A getter for the {@link Block} of this BlockConfig.
         *
         * @return The Block
         */
        public Block getBlock()
        {
            return block;
        }

        /**
         * A getter for the speed multiplier setting for the {@link Block} of this BlockConfig.
         *
         * @return the speed multiplier
         */
        public double getSpeedMult()
        {
            return speedMult;
        }

        /**
         * A getter for the damage multiplier setting for the {@link Block} of this BlockConfig.
         *
         * @return the damage multiplier
         */
        public double getDamageMult()
        {
            return damageMult;
        }

        /**
         *  Allow the native collision handling for this block also execute.
         */
        boolean allowNative()
        {
            return allowNative;
        }

        /**
         *  The original value of <tt>AbstractBlock#hasCollision</tt> for this block.
         */
        boolean hasCollision()
        {
            return hasCollision;
        }

        /**
         *  The original value of <tt>AbstractBlockState#canOcclude</tt> for the default state of this block.
         */
        boolean canOcclude()
        {
            return canOcclude;
        }

        /**
         * A factory method for creating BlockConfigs.
         *
         * @param  block       The Block.
         * @param  speedMult   The speed multiplier.
         * @param  damageMult  The damage multiplier.
         * @param  allowNative Allow default collision handling.
         * @return             A new BlockConfig.
         */
        static BlockConfig create(@Nonnull final Block block, final double speedMult, final double damageMult, final boolean allowNative)
        {
            return new BlockConfig(block, speedMult, damageMult, allowNative,
                ((Accessors.AbstractBlockAccessor)block).getHasCollision(),
                ((Accessors.AbstractBlockStateAccessor)block.defaultBlockState()).getCanOcclude());
        }

        /**
         * A factory method for creating BlockConfigs.
         *
         * @param  block        The Block.
         * @param  speedMult    The speed multiplier.
         * @param  damageMult   The damage multiplier.
         * @param  allowNative  Allow default collision handling.
         * @param  hasCollision The original block value of hasCollision.
         * @param  canCollude   The original blockstate value of canCollude.
         * @return              A new BlockConfig.
         */
        static BlockConfig create(@Nonnull final Block block, final double speedMult, final double damageMult, final boolean allowNative, final boolean hasCollision, final boolean canCollude)
        {
            return new BlockConfig(block, speedMult, damageMult, allowNative, hasCollision, canCollude);
        }

        /**
         * A serializer to convert this BlockConfig into a {@link CompoundNBT} for network traversal.
         *
         * @return the CompoundNBT tag of this BlockConfig
         */
        CompoundNBT toNBT()
        {
            final CompoundNBT ret = new CompoundNBT();
            ret.putString(GROUP_RESLOC, Objects.requireNonNull(getBlock().getRegistryName()).toString());
            ret.putDouble(GROUP_SPMULT, getSpeedMult());
            ret.putDouble(GROUP_DMGMULT, getDamageMult());
            ret.putBoolean(GROUP_ALLOWNATIVE, allowNative());
            ret.putBoolean(ORIG_HAS_COLLISION, hasCollision());
            ret.putBoolean(ORIG_CAN_OCCLUDE, canOcclude());
            return ret;
        }

        /**
         * A deserializer to create a BlockConfig instance from a {@link CompoundNBT}.
         *
         * @param  nbt The serialized BlockConfig,
         * @return     The deserialized BlockConfig.
         */
        static BlockConfig fromNBT(final CompoundNBT nbt)
        {
            final Block cfgblock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(nbt.getString(GROUP_RESLOC)));
            return BlockConfig.create(
                Objects.requireNonNull(cfgblock, "Block can not be null. Possible client-server mismatch."),
                nbt.getDouble(GROUP_SPMULT),
                nbt.getDouble(GROUP_DMGMULT),
                nbt.getBoolean(GROUP_ALLOWNATIVE),
                nbt.getBoolean(ORIG_HAS_COLLISION),
                nbt.getBoolean(ORIG_CAN_OCCLUDE)
            );
        }

        /**
         * We only care about the identity of the {@link Block} in this BlockConfig.
         *
         * @param  o Some object.
         * @return equality
         */
        @Override
        public boolean equals(final Object o)
        {
            return this == o || o != null && getClass() == o.getClass()
                && Objects.equals(getBlock().getRegistryName(), ((BlockConfig) o).getBlock().getRegistryName());
        }

        /**
         * We only care about the identity of the {@link Block} in this BlockConfig.
         *
         * @return The hashcode of the Block.
         */
        @Override
        public int hashCode()
        {
            return Objects.requireNonNull(getBlock().getRegistryName()).hashCode();
        }

        @Override
        public String toString()
        {
            return "BlockConfig{" + getBlock().getRegistryName() + "[" + getSpeedMult() + ", " + getDamageMult() + ", " + allowNative() + "]}";
        }
    }
}
