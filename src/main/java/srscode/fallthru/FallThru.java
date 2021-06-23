/*
 * Project      : FallThru
 * File         : FallThru.java
 * Last Modified: 20200912-09:40:27-0400
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

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ForgeI18n;
import net.minecraftforge.fml.Logging;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;


@SuppressWarnings("WeakerAccess")
@Mod(FallThru.MOD_ID)
public final class FallThru
{
    public static final String         MOD_ID           = "fallthru";
    public static final Logger         LOGGER           = LogManager.getLogger(MOD_ID);
    public static final BlockConfigMap BLOCK_CONFIG_MAP = new BlockConfigMap(); /*This has to be public for access by mixin injections*/

    private static FallThru     instance;
    private static CommonConfig commonConfig;

    public FallThru()
    {
        LOGGER.debug(MarkerManager.getMarker("LIFECYCLE").addParents(Logging.LOADING), "Creating an instance of FallThru!");
        instance = this;
        final Pair<CommonConfig, ForgeConfigSpec> config = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        commonConfig = config.getLeft();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, config.getRight(), MOD_ID + ".toml");
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(NetworkHandler.INSTANCE::registerPackets);
        modEventBus.addListener(commonConfig::onConfigUpdate);
    }

    /**
     * @return The FallThru instance.
     */
    static FallThru getInstance()
    {
        return instance;
    }

    /**
     * @return The FallThru config.
     */
    static CommonConfig config()
    {
        return commonConfig;
    }

    ITextComponent getTranslation(@Nullable final ITextComponent textComponent, final String key, final Object... objs)
    {
        return ForgeI18n.getPattern(key).equals(key)
            ? textComponent != null ? textComponent.copyRaw().appendString(Arrays.toString(objs)) : new StringTextComponent(Arrays.toString(objs))
            : textComponent != null ? new TranslationTextComponent(key, textComponent) : new TranslationTextComponent(key, objs);
    }
}
