package net.mcreator.MCreatorMCP.engine.model;

import java.util.*;

public class ResourceNode {
    public enum ResourceType {
        TEXTURE,
        MODEL,
        SOUND,
        STRUCTURE,
        ANIMATION
    }

    private final String name;
    private final ResourceType type;
    private final String subType;
    private final String filePath;
    private final long size;
    private final Set<String> referencedByElements;

    public ResourceNode(String name, ResourceType type, String subType, String filePath, long size) {
        this.name = name;
        this.type = type;
        this.subType = subType;
        this.filePath = filePath;
        this.size = size;
        this.referencedByElements = new HashSet<>();
    }

    public String getName() { return name; }
    public ResourceType getType() { return type; }
    public String getSubType() { return subType; }
    public String getFilePath() { return filePath; }
    public long getSize() { return size; }
    public Set<String> getReferencedByElements() { return referencedByElements; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type.name());
        m.put("subType", subType);
        m.put("filePath", filePath);
        m.put("sizeBytes", size);
        m.put("referencedByCount", referencedByElements.size());
        m.put("referencedBy", new ArrayList<>(referencedByElements));
        return m;
    }
}
