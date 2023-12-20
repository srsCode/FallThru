/*
 * Project      : FallThru
 * File         : FallThru.java
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

import org.slf4j.Logger;
import org.slf4j.MarkerFactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import de.srsco.srslib.util.Util;


@SuppressWarnings({"WeakerAccess", "checkstyle:HideUtilityClassConstructor"})
@Mod(FallThru.MOD_ID)
public final class FallThru
{
    public  static final String MOD_ID = "fallthru";

    private static final Logger          LOGGER        = Util.getLogger(FallThru.class);
    private static final BlockConfigMap  BLOCK_CONFIGS = new BlockConfigMap();
    private static final CommonConfig    CONFIG;
    private static final ModConfigSpec   CONFIG_SPEC;

    private static FallThru instance;

    static
    {
        final var config = new ModConfigSpec.Builder().configure(CommonConfig::new);
        CONFIG = config.getLeft();
        CONFIG_SPEC = config.getRight();
    }

    public FallThru(final IEventBus modEventBus)
    {
        instance = this;
        logger().debug(MarkerFactory.getMarker("LIFECYCLE"), "Creating an instance of FallThru!");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, modId() + ".toml");
        modEventBus.addListener(NetworkHandler.INSTANCE::registerPackets);
        modEventBus.addListener(CONFIG::onConfigUpdate);
        NeoForge.EVENT_BUS.addListener(FallThru::onRegisterCommand);
    }

    /**
     * @return the Mod instance of FallThru
     */
    public static FallThru instance()
    {
        return instance;
    }

    /**
     * @return the FallThru mod ID
     */
    public static String modId()
    {
        return MOD_ID;
    }

    /**
     * @return the FallThru main logger
     */
    public static Logger logger()
    {
        return LOGGER;
    }

    /**
     * @return The FallThru config.
     */
    public static CommonConfig config()
    {
        return CONFIG;
    }

    /**
     *  Gets the BlockConfigMap.
     *  This has to be public for access by mixin injections.
     *
     * @return the BlockConfigMap
     */
    public static BlockConfigMap blockConfigs()
    {
        return BLOCK_CONFIGS;
    }

    /**
     * This will register the /fallthru console command.
     *
     * @param event the event, duh.
     */
    public static void onRegisterCommand(final RegisterCommandsEvent event)
    {
        logger().debug(MarkerFactory.getMarker("EVENTS"), "Registering /fallthru command");
        FTCommand.register(event.getDispatcher());
    }
}
