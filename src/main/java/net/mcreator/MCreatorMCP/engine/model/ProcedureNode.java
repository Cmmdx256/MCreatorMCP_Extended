package net.mcreator.MCreatorMCP.engine.model;

import java.util.*;

public class ProcedureNode {
    private final String name;
    private String trigger;
    private final Set<String> modifiedEntities;
    private final Set<String> appliedEffects;
    private final Set<String> executedCommands;
    private final Set<String> accessedVariables;
    private final Set<String> spawnedParticles;
    private final Set<String> playedSounds;
    private final Set<String> referencedElements;
    private boolean runsEveryTick;
    private boolean containsLoops;
    private boolean containsAreaSearches;
    private String summary;

    public ProcedureNode(String name) {
        this.name = name;
        this.trigger = "no_trigger";
        this.modifiedEntities = new HashSet<>();
        this.appliedEffects = new HashSet<>();
        this.executedCommands = new HashSet<>();
        this.accessedVariables = new HashSet<>();
        this.spawnedParticles = new HashSet<>();
        this.playedSounds = new HashSet<>();
        this.referencedElements = new HashSet<>();
        this.runsEveryTick = false;
        this.containsLoops = false;
        this.containsAreaSearches = false;
        this.summary = "";
    }

    public String getName() { return name; }
    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) {
        this.trigger = trigger;
        if (trigger != null && (trigger.contains("player_ticks") || trigger.contains("world_ticks") || trigger.contains("entity_ticks"))) {
            this.runsEveryTick = true;
        }
    }

    public Set<String> getModifiedEntities() { return modifiedEntities; }
    public Set<String> getAppliedEffects() { return appliedEffects; }
    public Set<String> getExecutedCommands() { return executedCommands; }
    public Set<String> getAccessedVariables() { return accessedVariables; }
    public Set<String> getSpawnedParticles() { return spawnedParticles; }
    public Set<String> getPlayedSounds() { return playedSounds; }
    public Set<String> getReferencedElements() { return referencedElements; }
    public boolean isRunsEveryTick() { return runsEveryTick; }
    public void setRunsEveryTick(boolean runsEveryTick) { this.runsEveryTick = runsEveryTick; }
    public boolean isContainsLoops() { return containsLoops; }
    public void setContainsLoops(boolean containsLoops) { this.containsLoops = containsLoops; }
    public boolean isContainsAreaSearches() { return containsAreaSearches; }
    public void setContainsAreaSearches(boolean containsAreaSearches) { this.containsAreaSearches = containsAreaSearches; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public boolean hasPerformanceHazard() {
        return runsEveryTick && (containsLoops || containsAreaSearches);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("trigger", trigger);
        m.put("runsEveryTick", runsEveryTick);
        m.put("hasPerformanceHazard", hasPerformanceHazard());
        m.put("appliedEffects", new ArrayList<>(appliedEffects));
        m.put("modifiedEntities", new ArrayList<>(modifiedEntities));
        m.put("executedCommands", new ArrayList<>(executedCommands));
        m.put("accessedVariables", new ArrayList<>(accessedVariables));
        m.put("referencedElements", new ArrayList<>(referencedElements));
        m.put("summary", summary);
        return m;
    }
}
