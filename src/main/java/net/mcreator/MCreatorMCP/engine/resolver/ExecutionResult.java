package net.mcreator.MCreatorMCP.engine.resolver;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExecutionResult {
    private final boolean success;
    private final String message;
    private final Map<String, Object> data;

    public ExecutionResult(boolean success, String message, Map<String, Object> data) {
        this.success = success;
        this.message = message;
        this.data = data != null ? data : new LinkedHashMap<>();
    }

    public static ExecutionResult ok(String message, Map<String, Object> data) {
        return new ExecutionResult(true, message, data);
    }

    public static ExecutionResult error(String message) {
        return new ExecutionResult(false, message, new LinkedHashMap<>());
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Map<String, Object> getData() { return data; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", success);
        m.put("message", message);
        m.putAll(data);
        return m;
    }
}
