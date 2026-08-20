package net.mcreator.MCreatorMCP.engine.validator;

import java.util.*;

public class ValidationReport {
    private final List<ValidationIssue> issues;
    private long durationMs;

    public ValidationReport() {
        this.issues = new ArrayList<>();
        this.durationMs = 0;
    }

    public void addIssue(ValidationIssue issue) {
        if (issue != null) issues.add(issue);
    }

    public List<ValidationIssue> getIssues() { return issues; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public boolean isValid() {
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity() == ValidationIssue.Severity.ERROR) {
                return false;
            }
        }
        return true;
    }

    public int getErrorCount() {
        int count = 0;
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity() == ValidationIssue.Severity.ERROR) count++;
        }
        return count;
    }

    public int getWarningCount() {
        int count = 0;
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity() == ValidationIssue.Severity.WARNING) count++;
        }
        return count;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valid", isValid());
        m.put("errorCount", getErrorCount());
        m.put("warningCount", getWarningCount());
        m.put("durationMs", durationMs);
        List<Map<String, Object>> issueList = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            issueList.add(issue.toMap());
        }
        m.put("issues", issueList);
        return m;
    }
}
