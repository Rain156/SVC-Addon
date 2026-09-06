package io.github.rain156.svcaddon.voicechat;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import java.io.IOException;
import java.nio.file.Path;

/** One integrated/dedicated server session; reset completely between world loads. */
public final class RuntimeHost {
    private static volatile VoiceRuntime runtime;
    private static volatile VoicechatServerApi voiceApi;
    private RuntimeHost() { }

    public static synchronized void start(Path configDirectory, Path worldDirectory) throws IOException {
        VoiceRuntime previous = runtime;
        runtime = null;
        if (previous != null) previous.close();
        runtime = new VoiceRuntime(configDirectory.resolve("svcaddon/server.properties"),
                worldDirectory.resolve("data/svcaddon/players.properties"));
    }

    public static VoiceRuntime current() { return runtime; }
    public static VoicechatServerApi voiceApi() { return voiceApi; }
    static void voiceStarted(VoicechatServerApi api) { voiceApi = api; }
    static void voiceStopped() {
        voiceApi = null;
        VoiceRuntime current = runtime;
        if (current != null) current.resetVoice();
    }

    public static synchronized void stop() {
        VoiceRuntime previous = runtime;
        runtime = null;
        voiceApi = null;
        if (previous != null) previous.close();
    }
}
