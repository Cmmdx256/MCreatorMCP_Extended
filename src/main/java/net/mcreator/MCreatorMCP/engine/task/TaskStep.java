package net.mcreator.MCreatorMCP.engine.task;

import java.util.LinkedHashMap;
import java.util.Map;

public class TaskStep {
    private final String stepId;
    private final String operation;
    private final Map<String, Object> arguments;
    private final String description;

    public TaskStep(String stepId, String operation, Map<String, Object> arguments, String description) {
        this.stepId = stepId;
        this.operation = operation;
        this.arguments = arguments != null ? arguments : new LinkedHashMap<>();
        this.description = description;
    }

    public String getStepId() { return stepId; }
    public String getOperation() { return operation; }
    public Map<String, Object> getArguments() { return arguments; }
    public String getDescription() { return description; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stepId", stepId);
        m.put("operation", operation);
        m.put("description", description);
        m.put("arguments", arguments);
        return m;
    }
}
