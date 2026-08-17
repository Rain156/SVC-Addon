package datura.svcaddon.fabric;

import datura.svcaddon.AddonLogger;
import org.slf4j.Logger;

final class FabricAddonLogger implements AddonLogger {
    private final Logger delegate;

    FabricAddonLogger(Logger delegate) {
        this.delegate = delegate;
    }

    @Override
    public void info(String message) {
        delegate.info(message);
    }

    @Override
    public void warn(String message) {
        delegate.warn(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        delegate.error(message, throwable);
    }

    @Override
    public void debug(String message) {
        delegate.info("[debug] {}", message);
    }
}
