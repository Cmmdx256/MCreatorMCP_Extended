package net.mcreator.MCreatorMCP.engine.transaction;

public class RollbackException extends RuntimeException {
    public RollbackException(String message) {
        super(message);
    }
    public RollbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
