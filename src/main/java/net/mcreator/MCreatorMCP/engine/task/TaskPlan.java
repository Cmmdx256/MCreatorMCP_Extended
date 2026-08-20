package net.mcreator.MCreatorMCP.engine.task;

import java.util.*;

public class TaskPlan {
    private final String planId;
    private final String intent;
    private final List<TaskStep> steps;
    private final long createdAt;

    public TaskPlan(String planId, String intent) {
        this.planId = planId;
        this.intent = intent;
        this.steps = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }

    public void addStep(TaskStep step) {
        if (step != null) steps.add(step);
    }

    public String getPlanId() { return planId; }
    public String getIntent() { return intent; }
    public List<TaskStep> getSteps() { return steps; }
    public long getCreatedAt() { return createdAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planId", planId);
        m.put("intent", intent);
        m.put("totalSteps", steps.size());
        List<Map<String, Object>> stepList = new ArrayList<>();
        for (TaskStep s : steps) {
            stepList.add(s.toMap());
        }
        m.put("steps", stepList);
        return m;
    }
}
