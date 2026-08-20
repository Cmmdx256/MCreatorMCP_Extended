package net.mcreator.MCreatorMCP.engine.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProjectModel {
    private String modName;
    private String modId;
    private String version;
    private String author;
    private String packageName;
    private String mcreatorVersion;
    private String generatorName;
    private String workspacePath;

    private final Map<String, ElementNode> elements;
    private final Map<String, ProcedureNode> procedures;
    private final Map<String, VariableNode> variables;
    private final Map<String, ResourceNode> resources;
    private final Map<String, Set<String>> tags;
    private final Map<String, Map<String, String>> localizations;
    private long lastIndexTimestamp;

    public ProjectModel() {
        this.elements = new ConcurrentHashMap<>();
        this.procedures = new ConcurrentHashMap<>();
        this.variables = new ConcurrentHashMap<>();
        this.resources = new ConcurrentHashMap<>();
        this.tags = new ConcurrentHashMap<>();
        this.localizations = new ConcurrentHashMap<>();
        this.lastIndexTimestamp = System.currentTimeMillis();
    }

    public String getModName() { return modName; }
    public void setModName(String modName) { this.modName = modName; }
    public String getModId() { return modId; }
    public void setModId(String modId) { this.modId = modId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getMcreatorVersion() { return mcreatorVersion; }
    public void setMcreatorVersion(String mcreatorVersion) { this.mcreatorVersion = mcreatorVersion; }
    public String getGeneratorName() { return generatorName; }
    public void setGeneratorName(String generatorName) { this.generatorName = generatorName; }
    public String getWorkspacePath() { return workspacePath; }
    public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }

    public Map<String, ElementNode> getElements() { return elements; }
    public Map<String, ProcedureNode> getProcedures() { return procedures; }
    public Map<String, VariableNode> getVariables() { return variables; }
    public Map<String, ResourceNode> getResources() { return resources; }
    public Map<String, Set<String>> getTags() { return tags; }
    public Map<String, Map<String, String>> getLocalizations() { return localizations; }
    public long getLastIndexTimestamp() { return lastIndexTimestamp; }
    public void setLastIndexTimestamp(long lastIndexTimestamp) { this.lastIndexTimestamp = lastIndexTimestamp; }

    public ElementNode getElement(String name) {
        if (name == null) return null;
        return elements.get(name.trim());
    }

    public ProcedureNode getProcedure(String name) {
        if (name == null) return null;
        return procedures.get(name.trim());
    }

    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("modName", modName);
        overview.put("modId", modId);
        overview.put("version", version);
        overview.put("author", author);
        overview.put("packageName", packageName);
        overview.put("mcreatorVersion", mcreatorVersion);
        overview.put("generator", generatorName);
        overview.put("workspacePath", workspacePath);
        overview.put("totalElements", elements.size());
        overview.put("totalProcedures", procedures.size());
        overview.put("totalVariables", variables.size());
        overview.put("totalResources", resources.size());
        overview.put("totalTags", tags.size());
        overview.put("totalLanguages", localizations.size());
        overview.put("lastIndexed", new Date(lastIndexTimestamp).toString());
        return overview;
    }
}
