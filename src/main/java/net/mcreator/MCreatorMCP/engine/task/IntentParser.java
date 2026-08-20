package net.mcreator.MCreatorMCP.engine.task;

import java.util.*;

public class IntentParser {

    public static TaskPlan parseIntent(String intent, Map<String, Object> parameters) {
        String planId = "plan_" + UUID.randomUUID().toString().substring(0, 8);
        TaskPlan plan = new TaskPlan(planId, intent != null ? intent : "Execute Custom Task");

        if (parameters == null) parameters = Collections.emptyMap();

        // 1. If explicit steps are provided
        if (parameters.containsKey("steps") && parameters.get("steps") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stepsList = (List<Map<String, Object>>) parameters.get("steps");
            int idx = 1;
            for (Map<String, Object> stepMap : stepsList) {
                String op = (String) stepMap.getOrDefault("operation", (String) stepMap.get("tool"));
                String desc = (String) stepMap.getOrDefault("description", "Execute " + op);
                @SuppressWarnings("unchecked")
                Map<String, Object> args = (Map<String, Object>) stepMap.getOrDefault("arguments", Collections.emptyMap());
                plan.addStep(new TaskStep("step_" + idx++, op, args, desc));
            }
            return plan;
        }

        // 2. High-level intent decomposition
        String lower = intent != null ? intent.toLowerCase(Locale.ROOT) : "";

        // Intent: Create item with procedure
        if (lower.contains("create") && (lower.contains("sword") || lower.contains("item") || lower.contains("tool"))) {
            String itemName = (String) parameters.getOrDefault("name", "CustomItem");
            Map<String, Object> createArgs = new HashMap<>();
            createArgs.put("name", itemName);
            createArgs.put("type", "item");
            plan.addStep(new TaskStep("step_1", "createElement", createArgs, "Create item element: " + itemName));

            if (lower.contains("speed") || lower.contains("effect") || lower.contains("procedure") || lower.contains("click")) {
                String procName = itemName + "RightClicked";
                Map<String, Object> procArgs = new HashMap<>();
                procArgs.put("name", procName);
                plan.addStep(new TaskStep("step_2", "createProcedure", procArgs, "Create procedure: " + procName));

                Map<String, Object> linkArgs = new HashMap<>();
                linkArgs.put("elementName", itemName);
                linkArgs.put("eventName", "onRightClicked");
                linkArgs.put("procedureName", procName);
                plan.addStep(new TaskStep("step_3", "linkProcedureToElement", linkArgs, "Link " + procName + " to " + itemName));
            }

            Map<String, Object> patchArgs = new HashMap<>();
            patchArgs.put("elementName", itemName);
            patchArgs.put("propertyKey", "name");
            patchArgs.put("propertyValue", itemName);
            plan.addStep(new TaskStep("step_4", "patchElementProperty", patchArgs, "Configure item properties"));
            return plan;
        }

        // Default single step execution if single operation
        String singleOp = (String) parameters.get("operation");
        if (singleOp != null) {
            plan.addStep(new TaskStep("step_1", singleOp, parameters, "Execute operation: " + singleOp));
        }

        return plan;
    }
}
