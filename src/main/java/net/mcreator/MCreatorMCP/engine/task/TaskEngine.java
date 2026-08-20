package net.mcreator.MCreatorMCP.engine.task;

import net.mcreator.MCreatorMCP.engine.graph.SemanticProjectGraph;
import net.mcreator.MCreatorMCP.engine.index.IncrementalChangeTracker;
import net.mcreator.MCreatorMCP.engine.model.ProjectModel;
import net.mcreator.MCreatorMCP.engine.resolver.ExecutionResult;
import net.mcreator.MCreatorMCP.engine.resolver.OperationResolver;
import net.mcreator.MCreatorMCP.engine.transaction.TransactionManager;
import net.mcreator.MCreatorMCP.engine.transaction.WorkspaceSnapshot;
import net.mcreator.MCreatorMCP.engine.validator.ValidationEngine;
import net.mcreator.MCreatorMCP.engine.validator.ValidationReport;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class TaskEngine {

    private static final Logger LOG = LogManager.getLogger("TaskEngine");

    private final Workspace workspace;
    private final ProjectModel model;
    private final SemanticProjectGraph graph;
    private final OperationResolver resolver;
    private final TransactionManager transactionManager;
    private final IncrementalChangeTracker tracker;

    public TaskEngine(Workspace workspace, ProjectModel model, SemanticProjectGraph graph,
                      OperationResolver resolver, TransactionManager transactionManager,
                      IncrementalChangeTracker tracker) {
        this.workspace = workspace;
        this.model = model;
        this.graph = graph;
        this.resolver = resolver;
        this.transactionManager = transactionManager;
        this.tracker = tracker;
    }

    public Map<String, Object> executePlan(TaskPlan plan) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (plan == null || plan.getSteps().isEmpty()) {
            response.put("success", false);
            response.put("message", "TaskPlan is empty or null");
            return response;
        }

        LOG.info("Executing TaskPlan {} with {} steps for intent: {}",
                plan.getPlanId(), plan.getSteps().size(), plan.getIntent());

        List<Map<String, Object>> executedSteps = new ArrayList<>();
        WorkspaceSnapshot snapshot = transactionManager.beginTransaction();

        try {
            for (TaskStep step : plan.getSteps()) {
                LOG.info("[TASK STEP] Executing {}: {}", step.getStepId(), step.getDescription());

                // Track snapshot target
                if (step.getArguments().containsKey("elementName")) {
                    snapshot.captureElement(workspace, (String) step.getArguments().get("elementName"));
                } else if (step.getArguments().containsKey("name")) {
                    snapshot.captureElement(workspace, (String) step.getArguments().get("name"));
                    if ("createElement".equals(step.getOperation()) || "createProcedure".equals(step.getOperation())) {
                        snapshot.markElementCreated((String) step.getArguments().get("name"));
                    }
                }

                ExecutionResult res = resolver.executeOperation(step.getOperation(), step.getArguments());
                Map<String, Object> stepRecord = new LinkedHashMap<>();
                stepRecord.put("stepId", step.getStepId());
                stepRecord.put("operation", step.getOperation());
                stepRecord.put("description", step.getDescription());
                stepRecord.put("success", res.isSuccess());
                stepRecord.put("message", res.getMessage());
                stepRecord.put("data", res.getData());
                executedSteps.add(stepRecord);

                if (!res.isSuccess()) {
                    throw new RuntimeException("Step " + step.getStepId() + " failed: " + res.getMessage());
                }

                // Incremental index update if element modified
                if (step.getArguments().containsKey("elementName") || step.getArguments().containsKey("name")) {
                    String elName = (String) step.getArguments().getOrDefault("elementName", step.getArguments().get("name"));
                    ModElement el = workspace.getModElementByName(elName);
                    if (el != null && tracker != null) {
                        tracker.onElementModified(workspace, el);
                    }
                }
            }

            // Post-execution validation
            ValidationReport valReport = ValidationEngine.validateWorkspace(workspace, model, graph);
            if (!valReport.isValid()) {
                throw new RuntimeException("Post-execution validation failed with " + valReport.getErrorCount() + " errors: " + valReport.getIssues().get(0).getMessage());
            }

            transactionManager.commit();
            response.put("success", true);
            response.put("message", "Task executed successfully across " + executedSteps.size() + " steps.");
            response.put("plan", plan.toMap());
            response.put("stepsResult", executedSteps);
            response.put("validation", valReport.toMap());
            return response;

        } catch (Throwable t) {
            LOG.error("TaskPlan execution failed, rolling back", t);
            transactionManager.rollback();
            response.put("success", false);
            response.put("message", "Task execution failed and was rolled back: " + t.getMessage());
            response.put("stepsResult", executedSteps);
            return response;
        }
    }
}
