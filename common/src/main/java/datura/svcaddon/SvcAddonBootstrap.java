package datura.svcaddon;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class SvcAddonBootstrap {
    private static final AtomicReference<SvcAddonRuntime> RUNTIME = new AtomicReference<>();

    private SvcAddonBootstrap() {
    }

    public static SvcAddonRuntime install(Path configDirectory, AddonLogger logger) {
        SvcAddonRuntime created = new SvcAddonRuntime(configDirectory, logger);
        if (RUNTIME.compareAndSet(null, created)) {
            return created;
        }
        return RUNTIME.get();
    }

    public static SvcAddonRuntime runtime() {
        return RUNTIME.get();
    }
}
