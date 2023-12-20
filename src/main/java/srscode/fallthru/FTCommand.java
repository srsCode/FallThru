/*
 * Project      : FallThru
 * File         : FTCommand.java
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

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

import de.srsco.srslib.util.Util;
import srscode.fallthru.BlockConfigMap.BlockConfig;
import srscode.fallthru.mixin.Accessors;

import static srscode.fallthru.FTCommand.Constants.ARG_ALLOWNATIVE;
import static srscode.fallthru.FTCommand.Constants.ARG_BLOCK;
import static srscode.fallthru.FTCommand.Constants.ARG_DAMAGEMULT;
import static srscode.fallthru.FTCommand.Constants.ARG_SPEEDMULT;
import static srscode.fallthru.FTCommand.Constants.BLOCK_ARGUMENT;
import static srscode.fallthru.FTCommand.Constants.DOUBLE_ARGUMENT;
import static srscode.fallthru.FTCommand.Constants.DYNAMIC_COMMAND_EXCEPTION;
import static srscode.fallthru.FTCommand.Constants.LANGKEY_ROOT;
import static srscode.fallthru.FTCommand.Constants.OPTIONAL_ARGUMENT;

enum FTCommand
{
    sync(command -> Commands.literal(command.name()).executes(context -> {
        NetworkHandler.INSTANCE.updateAll();
        context.getSource().sendSuccess(() -> Component.translatable(command.langkey()), false);
        return Command.SINGLE_SUCCESS;
    })),

    list(command -> Commands.literal(command.name()).executes(context -> {
        final var output = FallThru.blockConfigs().stream().map(Objects::toString).collect(Collectors.joining("\n", "\n", ""));
        context.getSource().sendSuccess(() -> Component.translatable(command.langkey(), output), false);
        return Command.SINGLE_SUCCESS;
    })),

    add(command -> Commands.literal(command.name())
        .then(BLOCK_ARGUMENT.suggests((context, builder) -> SharedSuggestionProvider.suggestResource(FallThru.blockConfigs().getValidBlockOrTagSuggestions(), builder))
            .then(DOUBLE_ARGUMENT.apply(ARG_SPEEDMULT)
                .then(DOUBLE_ARGUMENT.apply(ARG_DAMAGEMULT)
                    .executes(OPTIONAL_ARGUMENT.apply(command, false)))
                .then(Commands.argument(ARG_ALLOWNATIVE, BoolArgumentType.bool())
                    .executes(context -> OPTIONAL_ARGUMENT.apply(command, BoolArgumentType.getBool(context, ARG_ALLOWNATIVE)).run(context)))))),

    remove(command -> Commands.literal(command.name())
        .then(BLOCK_ARGUMENT.suggests((context, builder) -> SharedSuggestionProvider.suggestResource(FallThru.blockConfigs().getRemoveSuggestions(), builder))
            .executes(context -> FallThru.blockConfigs().removeFromRemote(ResourceOrTagKeyArgument.getResourceOrTagKey(context, ARG_BLOCK, Registries.BLOCK,
                    DYNAMIC_COMMAND_EXCEPTION.createDynamic(Result.FAIL.getErrorkey(command, "fail"))).unwrap()))));

    private final ArgumentBuilder<CommandSourceStack, ?> argumentBuilder;

    FTCommand(final Function<FTCommand, ArgumentBuilder<CommandSourceStack, ?>> argumentBuilder)
    {
        this.argumentBuilder = argumentBuilder.apply(this);
    }

    static void register(final CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("fallthru")
            .requires(css -> css.hasPermission(Commands.LEVEL_ADMINS))
            .then(FTCommand.sync.getCommand())
            .then(FTCommand.list.getCommand())
            .then(FTCommand.add.getCommand())
            .then(FTCommand.remove.getCommand()));
    }

    private ArgumentBuilder<CommandSourceStack, ?> getCommand()
    {
        return argumentBuilder;
    }

    private String langkey()
    {
        return LANGKEY_ROOT + "." + name();
    }

    @SuppressWarnings("unused")
    record FallThruCommandExceptionType(Function<String, Function<Object, Message>> function) implements CommandExceptionType
    {
        CommandSyntaxException create(final String langkey, final Object obj)
        {
            return new CommandSyntaxException(this, function().apply(langkey).apply(obj));
        }

        DynamicCommandExceptionType createDynamic(final String langkey)
        {
            return new DynamicCommandExceptionType(function().apply(langkey));
        }
    }

    enum Result
    {
        FAIL((command, error) -> command.langkey() + ".error." +  error),
        SUCCESS((command, ignored) -> command.langkey() + ".success");

        private final BiFunction<FTCommand, String, String> langkey;

        Result(final BiFunction<FTCommand, String, String> langkey)
        {
            this.langkey = langkey;
        }

        String getErrorkey(final FTCommand command, final String error)
        {
            return langkey.apply(command, error);
        }

        String getSuccesskey(final FTCommand command)
        {
            return langkey.apply(command, "DUMMY");
        }
    }

    static final class Constants
    {
        static final String LANGKEY_ROOT   = "fallthru.command";
        static final String ARG_BLOCK      = "block";
        static final String ARG_SPEEDMULT  = "speedMult";
        static final String ARG_DAMAGEMULT = "damageMult";
        static final String ARG_ALLOWNATIVE = "allowDef";

        static final Registry<Block>                        BLOCK_REGISTRY     = BuiltInRegistries.BLOCK;
        static final ResourceKey<? extends Registry<Block>> BLOCK_REGISTRY_KEY = BLOCK_REGISTRY.key();

        static final RequiredArgumentBuilder<CommandSourceStack, ResourceOrTagKeyArgument.Result<Block>> BLOCK_ARGUMENT =
            Commands.argument(ARG_BLOCK, ResourceOrTagKeyArgument.resourceOrTagKey(BLOCK_REGISTRY_KEY));

        static final Function<String, RequiredArgumentBuilder<CommandSourceStack, Double>> DOUBLE_ARGUMENT = name ->
            Commands.argument(name, DoubleArgumentType.doubleArg(0.05, 1.0));

        static final FallThruCommandExceptionType DYNAMIC_COMMAND_EXCEPTION = new FallThruCommandExceptionType(langkey -> obj -> Component.translatable(langkey, obj));

        static final BiFunction<FTCommand, Boolean, Command<CommandSourceStack>> OPTIONAL_ARGUMENT = (command, allowNative) -> context -> {
            final var result     = ResourceOrTagKeyArgument.getResourceOrTagKey(context, ARG_BLOCK, Registries.BLOCK, DYNAMIC_COMMAND_EXCEPTION.createDynamic(LANGKEY_ROOT + ".error.bad_resloc"));
            final var speedMult  = DoubleArgumentType.getDouble(context, ARG_SPEEDMULT);
            final var damageMult = DoubleArgumentType.getDouble(context, ARG_DAMAGEMULT);

            final var blockConfigs = result.unwrap()
                .map(
                    k -> BLOCK_REGISTRY.getHolder(k).map(Holder::value).stream(),
                    k -> BLOCK_REGISTRY.getTag(k).flatMap(n -> n.unwrap().right()).stream().flatMap(l -> l.stream().map(Holder::value))
                )
                .filter(Objects::nonNull)
                .filter(block -> !BlockConfigMap.BLOCK_BLACKLIST.contains(block))
                .map(block -> new BlockConfig(
                    block,
                    Util.getResLoc(block).orElseThrow(),
                    speedMult,
                    damageMult,
                    allowNative,
                    ((Accessors.BlockBehaviourAccessor) block).getHasCollision(),
                    block.defaultBlockState().canOcclude()))
                .toList();

            if (blockConfigs.isEmpty()) {
                context.getSource().sendFailure(Component.translatable(Result.FAIL.getErrorkey(command, "blacklisted"), result.asPrintable()));
                return 0;
            }

            blockConfigs.forEach(FallThru.blockConfigs()::add);
            context.getSource().sendSuccess(() -> Component.translatable(Result.SUCCESS.getSuccesskey(command)), true);
            return 1;
        };

        private Constants()
        {}
    }
}
