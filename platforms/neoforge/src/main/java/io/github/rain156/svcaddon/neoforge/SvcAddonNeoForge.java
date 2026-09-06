package io.github.rain156.svcaddon.neoforge;

import io.github.rain156.svcaddon.minecraft.AddonCommands;
import io.github.rain156.svcaddon.minecraft.MinecraftServerAdapter;
import io.github.rain156.svcaddon.voicechat.RuntimeHost;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("svcaddon")
public final class SvcAddonNeoForge {
    public SvcAddonNeoForge() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> AddonCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> MinecraftServerAdapter.start(event.getServer(), FMLPaths.CONFIGDIR.get()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> MinecraftServerAdapter.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> RuntimeHost.stop());
    }
}
