package io.github.rain156.svcaddon.fabric;

import io.github.rain156.svcaddon.minecraft.AddonCommands;
import io.github.rain156.svcaddon.minecraft.MinecraftServerAdapter;
import io.github.rain156.svcaddon.voicechat.RuntimeHost;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SvcAddonFabric implements ModInitializer {
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> AddonCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> MinecraftServerAdapter.start(server, FabricLoader.getInstance().getConfigDir()));
        ServerTickEvents.END_SERVER_TICK.register(MinecraftServerAdapter::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RuntimeHost.stop());
    }
}
