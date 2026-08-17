package datura.svcaddon.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import datura.svcaddon.CommandReply;
import datura.svcaddon.SvcAddonRuntime;
import datura.svcaddon.config.PlayerPathPreference;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class FabricCommands {
    private FabricCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, SvcAddonRuntime runtime) {
        dispatcher.register(Commands.literal("svcaddon")
                .then(Commands.literal("pathtracing")
                        .then(Commands.literal("on").executes(context ->
                                playerPreference(context.getSource(), runtime, PlayerPathPreference.ON)))
                        .then(Commands.literal("off").executes(context ->
                                playerPreference(context.getSource(), runtime, PlayerPathPreference.OFF)))
                        .then(Commands.literal("default").executes(context ->
                                playerPreference(context.getSource(), runtime, PlayerPathPreference.DEFAULT)))
                        .then(Commands.literal("status").executes(context ->
                                playerStatus(context.getSource(), runtime)))
                        .then(Commands.literal("global")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.literal("on").executes(context ->
                                        reply(context.getSource(), runtime.setGlobalPathTracing(true))))
                                .then(Commands.literal("off").executes(context ->
                                        reply(context.getSource(), runtime.setGlobalPathTracing(false))))
                                .then(Commands.literal("status").executes(context ->
                                        reply(context.getSource(), runtime.globalPathStatus())))))
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> reply(context.getSource(), runtime.reload()))));
    }

    private static int playerPreference(
            CommandSourceStack source,
            SvcAddonRuntime runtime,
            PlayerPathPreference preference
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return reply(source, runtime.setPlayerPathPreference(player.getUUID(), preference));
    }

    private static int playerStatus(CommandSourceStack source, SvcAddonRuntime runtime) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return reply(source, runtime.playerPathStatus(player.getUUID()));
    }

    private static int reply(CommandSourceStack source, CommandReply reply) {
        Component message = Component.literal(reply.message());
        if (reply.success()) {
            source.sendSuccess(() -> message, false);
            return 1;
        }
        source.sendFailure(message);
        return 0;
    }
}
