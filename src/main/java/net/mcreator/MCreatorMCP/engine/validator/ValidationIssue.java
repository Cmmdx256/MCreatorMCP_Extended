package net.mcreator.MCreatorMCP.engine.validator;

import java.util.LinkedHashMap;
import java.util.Map;

public class ValidationIssue {
    public enum Severity { ERROR, WARNING, INFO }
    public enum Category { SCHEMA, FREEMARKER, TICK_PERFORMANCE, SECURITY, DEPENDENCY, VERSION }

    private final Severity severity;
    private final Category category;
    private final String targetElement;
    private final String message;

    public ValidationIssue(Severity severity, Category category, String targetElement, String message) {
        this.severity = severity;
        this.category = category;
        this.targetElement = targetElement;
        this.message = message;
    }

    public Severity getSeverity() { return severity; }
    public Category getCategory() { return category; }
    public String getTargetElement() { return targetElement; }
    public String getMessage() { return message; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", severity.name());
        m.put("category", category.name());
        m.put("targetElement", targetElement);
        m.put("message", message);
        return m;
    }
}
