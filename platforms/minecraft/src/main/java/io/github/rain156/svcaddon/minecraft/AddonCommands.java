package io.github.rain156.svcaddon.minecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import io.github.rain156.svcaddon.core.audio.EffectPreset;
import io.github.rain156.svcaddon.core.player.VoiceSettings;
import io.github.rain156.svcaddon.voicechat.RuntimeHost;
import io.github.rain156.svcaddon.voicechat.VoiceRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.UnaryOperator;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class AddonCommands {
    private AddonCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = literal("svcaddon").requires(MinecraftVersion::canManage);
        root.executes(context -> status(context.getSource()));
        root.then(literal("status").executes(context -> status(context.getSource())));
        root.then(literal("reload").executes(context -> {
            try { runtime().reload(); }
            catch (IOException | IllegalArgumentException e) { throw error("Configuration was not changed: " + e.getMessage()); }
            context.getSource().sendSuccess(() -> text("reloaded", "SVC Addon configuration reloaded."), true);
            return 1;
        }));
        root.then(rangeCommand("transmit", true));
        root.then(rangeCommand("receive", false));
        root.then(literal("broadcast").then(argument("targets", EntityArgument.players())
                .then(argument("enabled", BoolArgumentType.bool()).executes(context -> update(context,
                        settings -> settings.withBroadcast(BoolArgumentType.getBool(context, "enabled")))))));
        root.then(literal("effect").then(argument("targets", EntityArgument.players())
                .then(argument("preset", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("none", "radio", "robot"), builder))
                        .executes(context -> {
                            EffectPreset preset;
                            try { preset = EffectPreset.valueOf(StringArgumentType.getString(context, "preset").toUpperCase(Locale.ROOT)); }
                            catch (IllegalArgumentException e) { throw error("Unknown preset. Use none, radio or robot."); }
                            return update(context, settings -> settings.withEffect(preset));
                        }))));
        root.then(literal("reset").then(argument("targets", EntityArgument.players())
                .executes(context -> update(context, ignored -> VoiceSettings.DEFAULT))));
        root.then(literal("level").then(argument("target", EntityArgument.player()).executes(context -> {
            var player = EntityArgument.getPlayer(context, "target");
            String value = String.format(Locale.ROOT, "%.1f", runtime().level(player.getUUID()));
            context.getSource().sendSuccess(() -> text("level", "%s: %s dBFS", player.getDisplayName(), value), false);
            return 1;
        })));
        root.then(groups());
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rangeCommand(String name, boolean transmit) {
        return literal(name).then(argument("targets", EntityArgument.players())
                .then(argument("blocks", DoubleArgumentType.doubleArg(0, 1024)).executes(context -> update(context,
                        settings -> transmit ? settings.withTransmit(DoubleArgumentType.getDouble(context, "blocks"))
                                : settings.withReceive(DoubleArgumentType.getDouble(context, "blocks")))))
                .then(literal("default").executes(context -> update(context,
                        settings -> transmit ? settings.withTransmit(null) : settings.withReceive(null)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> groups() {
        var root = literal("group");
        root.then(literal("list").executes(context -> {
            var groups = voice().getGroups();
            for (Group group : groups) context.getSource().sendSuccess(() -> Component.literal(group.getName() + "  " + group.getId()), false);
            if (groups.isEmpty()) context.getSource().sendSuccess(() -> text("no_groups", "No voice groups."), false);
            return groups.size();
        }));
        root.then(literal("create").then(argument("name", StringArgumentType.string())
                .then(argument("type", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("isolated", "normal", "open"), builder))
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            if (name.isBlank() || name.length() > 48) throw error("Group name must contain 1 to 48 characters.");
                            VoicechatServerApi api = voice();
                            if (api.getGroups().stream().anyMatch(group -> group.getName().equalsIgnoreCase(name))) throw error("Group name already exists.");
                            Group.Type type = switch (StringArgumentType.getString(context, "type")) {
                                case "isolated" -> Group.Type.ISOLATED;
                                case "normal" -> Group.Type.NORMAL;
                                case "open" -> Group.Type.OPEN;
                                default -> throw error("Unknown group type. Use isolated, normal or open.");
                            };
                            Group group = api.groupBuilder().setName(name).setType(type).setPersistent(true).build();
                            context.getSource().sendSuccess(() -> text("group_created", "Created group %s (%s).", name, group.getId().toString()), true);
                            return 1;
                        }))));
        root.then(literal("join").then(argument("targets", EntityArgument.players())
                .then(argument("group", UuidArgument.uuid()).executes(context -> {
                    Group group = voice().getGroup(UuidArgument.getUuid(context, "group"));
                    if (group == null) throw error("Voice group does not exist.");
                    return setGroup(context, group);
                }))));
        root.then(literal("leave").then(argument("targets", EntityArgument.players()).executes(context -> setGroup(context, null))));
        root.then(literal("remove").then(argument("group", UuidArgument.uuid()).executes(context -> {
            UUID id = UuidArgument.getUuid(context, "group");
            if (!voice().removeGroup(id)) throw error("Group could not be removed; move its members out first.");
            context.getSource().sendSuccess(() -> text("group_removed", "Removed group %s.", id.toString()), true);
            return 1;
        })));
        return root;
    }

    private static int setGroup(CommandContext<CommandSourceStack> context, Group group) throws CommandSyntaxException {
        var targets = EntityArgument.getPlayers(context, "targets");
        var api = voice();
        var connections = targets.stream().map(player -> api.getConnectionOf(player.getUUID())).toList();
        if (connections.stream().anyMatch(connection -> connection == null || !connection.isConnected())) {
            throw error("Every selected player must be connected to Simple Voice Chat.");
        }
        connections.forEach(connection -> connection.setGroup(group));
        context.getSource().sendSuccess(() -> text("updated", "Updated voice settings for %s player(s).", targets.size()), true);
        return targets.size();
    }

    private static int update(CommandContext<CommandSourceStack> context, UnaryOperator<VoiceSettings> change) throws CommandSyntaxException {
        var targets = EntityArgument.getPlayers(context, "targets");
        try { runtime().players().update(targets.stream().map(player -> player.getUUID()).toList(), change); }
        catch (IOException | IllegalArgumentException e) { throw error("Player settings were not changed: " + e.getMessage()); }
        context.getSource().sendSuccess(() -> text("updated", "Updated voice settings for %s player(s).", targets.size()), true);
        return targets.size();
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        var config = runtime().config();
        source.sendSuccess(() -> text("status", "SVC Addon: voice=%s; dynamic=%s; range=%s..%s blocks.",
                RuntimeHost.voiceApi() != null, config.dynamicRange(), config.minimumRange(), config.maximumRange()), false);
        return 1;
    }

    private static VoiceRuntime runtime() throws CommandSyntaxException {
        VoiceRuntime runtime = RuntimeHost.current();
        if (runtime == null) throw error("SVC Addon server is not ready.");
        return runtime;
    }

    private static VoicechatServerApi voice() throws CommandSyntaxException {
        var api = RuntimeHost.voiceApi();
        if (api == null) throw error("Simple Voice Chat server is not ready.");
        return api;
    }

    private static Component text(String key, String fallback, Object... args) {
        return Component.translatableWithFallback("command.svcaddon." + key, fallback, args);
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}
