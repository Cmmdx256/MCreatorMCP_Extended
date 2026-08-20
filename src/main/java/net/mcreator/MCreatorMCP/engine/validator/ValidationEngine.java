package net.mcreator.MCreatorMCP.engine.validator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.MCreatorMCP.engine.graph.SemanticProjectGraph;
import net.mcreator.MCreatorMCP.engine.model.ProjectModel;
import net.mcreator.MCreatorMCP.engine.model.ProcedureNode;
import net.mcreator.element.GeneratableElement;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;
import net.mcreator.workspace.elements.ModElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class ValidationEngine {

    private static final Logger LOG = LogManager.getLogger("ValidationEngine");

    public static ValidationReport validateWorkspace(Workspace workspace, ProjectModel model, SemanticProjectGraph graph) {
        long start = System.currentTimeMillis();
        ValidationReport report = new ValidationReport();

        if (workspace == null) {
            report.addIssue(new ValidationIssue(ValidationIssue.Severity.ERROR, ValidationIssue.Category.VERSION, "workspace", "No active workspace loaded"));
            report.setDurationMs(System.currentTimeMillis() - start);
            return report;
        }

        // 1. Tick Performance & Loop hazards
        if (model != null) {
            for (ProcedureNode pn : model.getProcedures().values()) {
                if (pn.hasPerformanceHazard()) {
                    report.addIssue(new ValidationIssue(
                            ValidationIssue.Severity.WARNING,
                            ValidationIssue.Category.TICK_PERFORMANCE,
                            pn.getName(),
                            "Procedure runs every tick (" + pn.getTrigger() + ") and contains heavy loop or entity search operations which may cause severe TPS lag."
                    ));
                }
            }
        }

        // 2. Element GeneratableElement & JSON Definition Invariant Checks
        for (ModElement element : workspace.getModElements()) {
            try {
                GeneratableElement ge = element.getGeneratableElement();
                if (ge == null) {
                    report.addIssue(new ValidationIssue(
                            ValidationIssue.Severity.ERROR,
                            ValidationIssue.Category.FREEMARKER,
                            element.getName(),
                            "GeneratableElement could not be instantiated for " + element.getName()
                    ));
                    continue;
                }

                String json = WorkspaceFileManager.gson.toJson(ge);
                JsonObject defObj = JsonParser.parseString(json).getAsJsonObject();
                if (defObj.size() == 0) {
                    report.addIssue(new ValidationIssue(
                            ValidationIssue.Severity.WARNING,
                            ValidationIssue.Category.SCHEMA,
                            element.getName(),
                            "Definition for element " + element.getName() + " is empty."
                    ));
                }
            } catch (Throwable t) {
                report.addIssue(new ValidationIssue(
                        ValidationIssue.Severity.ERROR,
                        ValidationIssue.Category.FREEMARKER,
                        element.getName(),
                        "Element generation simulation failed: " + t.getMessage()
                ));
            }
        }

        // 3. Broken References
        if (graph != null) {
            for (ModElement element : workspace.getModElements()) {
                for (String dep : graph.getDependenciesOf(element.getName())) {
                    if (workspace.getModElementByName(dep) == null && !dep.startsWith("texture:") && !dep.startsWith("model:") && !dep.startsWith("sound:")) {
                        // Check if it's a variable
                        if (workspace.getVariableElements().stream().noneMatch(v -> v.getName().equals(dep))) {
                            report.addIssue(new ValidationIssue(
                                    ValidationIssue.Severity.WARNING,
                                    ValidationIssue.Category.DEPENDENCY,
                                    element.getName(),
                                    "Element references '" + dep + "' which is not found in mod elements or variables."
                            ));
                        }
                    }
                }
            }
        }

        report.setDurationMs(System.currentTimeMillis() - start);
        LOG.info("Validation completed in {}ms. Valid: {}, Errors: {}, Warnings: {}",
                report.getDurationMs(), report.isValid(), report.getErrorCount(), report.getWarningCount());
        return report;
    }
}
