package net.mcreator.MCreatorMCP.engine;

import net.mcreator.MCreatorMCP.engine.context.LiveContextProvider;
import net.mcreator.MCreatorMCP.engine.graph.GraphQueryEngine;
import net.mcreator.MCreatorMCP.engine.graph.ImpactAnalyzer;
import net.mcreator.MCreatorMCP.engine.graph.SemanticProjectGraph;
import net.mcreator.MCreatorMCP.engine.index.IncrementalChangeTracker;
import net.mcreator.MCreatorMCP.engine.index.ProjectIndexer;
import net.mcreator.MCreatorMCP.engine.logging.EngineLogger;
import net.mcreator.MCreatorMCP.engine.model.ProjectModel;
import net.mcreator.MCreatorMCP.engine.resolver.CapabilityRegistry;
import net.mcreator.MCreatorMCP.engine.resolver.OperationResolver;
import net.mcreator.MCreatorMCP.engine.task.IntentParser;
import net.mcreator.MCreatorMCP.engine.task.TaskEngine;
import net.mcreator.MCreatorMCP.engine.task.TaskPlan;
import net.mcreator.MCreatorMCP.engine.transaction.TransactionManager;
import net.mcreator.MCreatorMCP.engine.ui.UISynchronizer;
import net.mcreator.MCreatorMCP.engine.validator.ValidationEngine;
import net.mcreator.MCreatorMCP.engine.validator.ValidationReport;
import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;

import java.util.*;

public class ProjectIntelligenceEngine {

    private static volatile ProjectIntelligenceEngine instance;

    public static ProjectIntelligenceEngine getInstance() {
        if (instance == null) {
            synchronized (ProjectIntelligenceEngine.class) {
                if (instance == null) {
                    instance = new ProjectIntelligenceEngine();
                }
            }
        }
        return instance;
    }

    private MCreator mcreatorInstance;
    private Workspace currentWorkspace;
    private final ProjectModel model;
    private final SemanticProjectGraph graph;
    private final CapabilityRegistry capabilityRegistry;
    private OperationResolver operationResolver;
    private GraphQueryEngine queryEngine;
    private ImpactAnalyzer impactAnalyzer;
    private IncrementalChangeTracker changeTracker;
    private TransactionManager transactionManager;
    private TaskEngine taskEngine;
    private LiveContextProvider liveContextProvider;

    private ProjectIntelligenceEngine() {
        this.model = new ProjectModel();
        this.graph = new SemanticProjectGraph();
        this.capabilityRegistry = new CapabilityRegistry();
        this.operationResolver = new OperationResolver(capabilityRegistry);
        this.queryEngine = new GraphQueryEngine(graph, model);
        this.impactAnalyzer = new ImpactAnalyzer(graph);
        this.changeTracker = new IncrementalChangeTracker(model, graph);
    }

    public synchronized void initialize(Workspace workspace, net.mcreator.MCreatorMCP.mcp.McpServer server, MCreator mcreator) {
        this.mcreatorInstance = mcreator;
        this.currentWorkspace = workspace;
        this.liveContextProvider = new LiveContextProvider(mcreator);
        this.transactionManager = new TransactionManager(workspace);
        this.taskEngine = new TaskEngine(workspace, model, graph, operationResolver, transactionManager, changeTracker);

        // Bind all registered server handlers into capability registry
        if (server != null) {
            for (var entry : server.getHandlers().entrySet()) {
                String toolName = entry.getKey();
                var handler = entry.getValue();
                capabilityRegistry.registerCapability(toolName, handler::handle);
            }
            EngineLogger.project("Bound {} internal capabilities to OperationResolver", capabilityRegistry.getCapabilityCount());
        }

        reindex();
        EngineLogger.project("Native MCreator Intelligence Engine initialized for workspace: {}",
                workspace != null ? workspace.getWorkspaceSettings().getModName() : "null");
    }

    public synchronized void initialize(Workspace workspace, net.mcreator.MCreatorMCP.mcp.McpServer server) {
        initialize(workspace, server, null);
    }

    public synchronized void initialize(Workspace workspace) {
        initialize(workspace, null, null);
    }

    public synchronized void reindex() {
        if (currentWorkspace != null) {
            ProjectIndexer.indexWorkspace(currentWorkspace, model, graph);
        }
    }

    public MCreator getMCreator() { return mcreatorInstance; }
    public ProjectModel getModel() { return model; }
    public SemanticProjectGraph getGraph() { return graph; }
    public CapabilityRegistry getCapabilityRegistry() { return capabilityRegistry; }
    public GraphQueryEngine getQueryEngine() { return queryEngine; }
    public ImpactAnalyzer getImpactAnalyzer() { return impactAnalyzer; }
    public IncrementalChangeTracker getChangeTracker() { return changeTracker; }
    public TaskEngine getTaskEngine() { return taskEngine; }
    public LiveContextProvider getLiveContextProvider() { return liveContextProvider; }

    public Map<String, Object> analyzeProject() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("overview", model.getOverview());
        if (liveContextProvider != null) {
            res.put("liveMCreatorContext", liveContextProvider.captureLiveContext());
        }
        res.put("graphSummary", graph.exportGraphSummary());
        ValidationReport report = ValidationEngine.validateWorkspace(currentWorkspace, model, graph);
        res.put("validation", report.toMap());
        return res;
    }

    public Map<String, Object> inspectProject(String targetName, String queryType) {
        Map<String, Object> res = new LinkedHashMap<>();

        // Handle "current" keyword to resolve currently active editing element
        String resolvedTarget = targetName;
        if (resolvedTarget == null || resolvedTarget.trim().isEmpty() || "current".equalsIgnoreCase(resolvedTarget.trim())) {
            if (liveContextProvider != null) {
                String activeEditing = liveContextProvider.getActiveEditingElementName();
                if (activeEditing != null) {
                    resolvedTarget = activeEditing;
                    res.put("resolvedFromLiveContext", "Current active editor element: " + resolvedTarget);
                }
            }
        }

        if (resolvedTarget != null && !resolvedTarget.trim().isEmpty() && !"current".equalsIgnoreCase(resolvedTarget.trim())) {
            res.put("elementContext", queryEngine.queryElementContext(resolvedTarget));
            res.put("deletionImpact", impactAnalyzer.analyzeDeletionImpact(resolvedTarget));
        } else {
            res.put("allElements", model.getElements().keySet());
            res.put("allProcedures", model.getProcedures().keySet());
            res.put("allVariables", model.getVariables().keySet());
            res.put("totalResources", model.getResources().size());
        }

        if (liveContextProvider != null) {
            res.put("liveContext", liveContextProvider.captureLiveContext());
        }
        return res;
    }

    public Map<String, Object> executeTask(String intent, Map<String, Object> parameters) {
        if (taskEngine == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("message", "TaskEngine is not initialized (no workspace loaded)");
            return err;
        }
        TaskPlan plan = IntentParser.parseIntent(intent, parameters);
        Map<String, Object> result = taskEngine.executePlan(plan);

        // Sync UI on completion
        if (mcreatorInstance != null) {
            UISynchronizer.refreshWorkspaceUI(mcreatorInstance);
        }

        return result;
    }

    public Map<String, Object> getProjectContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("modInfo", model.getOverview());

        if (liveContextProvider != null) {
            context.put("liveMCreatorContext", liveContextProvider.captureLiveContext());
        }

        List<Map<String, Object>> elementSummaries = new ArrayList<>();
        for (var el : model.getElements().values()) {
            elementSummaries.add(el.toMap());
        }
        context.put("elements", elementSummaries);

        List<Map<String, Object>> procSummaries = new ArrayList<>();
        for (var pr : model.getProcedures().values()) {
            procSummaries.add(pr.toMap());
        }
        context.put("procedures", procSummaries);
        context.put("variables", model.getVariables().keySet());
        context.put("graph", graph.exportGraphSummary());
        return context;
    }
}
