package net.mcreator.MCreatorMCP.engine.model;

import java.util.*;

public class ElementNode {
    private final String name;
    private final String type;
    private final Map<String, Object> properties;
    private final Set<String> referencedElements;
    private final Set<String> linkedProcedures;
    private final Set<String> boundTextures;
    private final Set<String> boundModels;
    private final Set<String> boundSounds;
    private final Set<String> tags;
    private boolean locked;
    private long lastModified;

    public ElementNode(String name, String type) {
        this.name = name;
        this.type = type;
        this.properties = new HashMap<>();
        this.referencedElements = new HashSet<>();
        this.linkedProcedures = new HashSet<>();
        this.boundTextures = new HashSet<>();
        this.boundModels = new HashSet<>();
        this.boundSounds = new HashSet<>();
        this.tags = new HashSet<>();
        this.locked = false;
        this.lastModified = System.currentTimeMillis();
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public Map<String, Object> getProperties() { return properties; }
    public Set<String> getReferencedElements() { return referencedElements; }
    public Set<String> getLinkedProcedures() { return linkedProcedures; }
    public Set<String> getBoundTextures() { return boundTextures; }
    public Set<String> getBoundModels() { return boundModels; }
    public Set<String> getBoundSounds() { return boundSounds; }
    public Set<String> getTags() { return tags; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("locked", locked);
        m.put("referencedElements", new ArrayList<>(referencedElements));
        m.put("linkedProcedures", new ArrayList<>(linkedProcedures));
        m.put("boundTextures", new ArrayList<>(boundTextures));
        m.put("boundModels", new ArrayList<>(boundModels));
        m.put("boundSounds", new ArrayList<>(boundSounds));
        m.put("tags", new ArrayList<>(tags));
        m.put("propertiesCount", properties.size());
        return m;
    }
}
