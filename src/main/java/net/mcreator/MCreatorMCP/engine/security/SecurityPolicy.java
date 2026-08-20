package net.mcreator.MCreatorMCP.engine.security;

public enum SecurityPolicy {
    READ_ONLY,
    SAFE_WRITE,
    PROJECT_WRITE,
    ADVANCED;

    private static SecurityPolicy currentPolicy = PROJECT_WRITE;

    public static SecurityPolicy getCurrentPolicy() {
        return currentPolicy;
    }

    public static void setCurrentPolicy(SecurityPolicy policy) {
        if (policy != null) {
            currentPolicy = policy;
        }
    }

    public static boolean allowsWrite() {
        return currentPolicy != READ_ONLY;
    }

    public static boolean allowsSystemExecution() {
        return currentPolicy == ADVANCED;
    }
}
