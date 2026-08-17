package datura.svcaddon;

public record CommandReply(boolean success, String message) {
    public static CommandReply success(String message) {
        return new CommandReply(true, message);
    }

    public static CommandReply failure(String message) {
        return new CommandReply(false, message);
    }
}
