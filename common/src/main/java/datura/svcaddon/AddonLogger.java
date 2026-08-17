package datura.svcaddon;

public interface AddonLogger {
    void info(String message);

    void warn(String message);

    void error(String message, Throwable throwable);

    void debug(String message);
}
