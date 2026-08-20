package net.mcreator.MCreatorMCP.engine.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class VariableNode {
    private final String name;
    private String type;
    private String scope;
    private String value;

    public VariableNode(String name, String type, String scope, String value) {
        this.name = name;
        this.type = type;
        this.scope = scope;
        this.value = value;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("scope", scope);
        m.put("value", value);
        return m;
    }
}
