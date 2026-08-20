package net.mcreator.MCreatorMCP.engine.resolver;

import net.mcreator.MCreatorMCP.mcp.McpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CapabilityRegistry {

    private static final Logger LOG = LogManager.getLogger("CapabilityRegistry");

    @FunctionalInterface
    public interface CapabilityHandler {
        Object execute(Map<String, Object> params) throws Exception;
    }

    private final Map<String, CapabilityHandler> capabilities;

    public CapabilityRegistry() {
        this.capabilities = new ConcurrentHashMap<>();
    }

    public void registerCapability(String name, CapabilityHandler handler) {
        if (name != null && handler != null) {
            capabilities.put(name, handler);
        }
    }

    public boolean hasCapability(String name) {
        return capabilities.containsKey(name);
    }

    public Object invoke(String name, Map<String, Object> params) throws Exception {
        CapabilityHandler handler = capabilities.get(name);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown internal capability: " + name);
        }
        return handler.execute(params);
    }

    public int getCapabilityCount() {
        return capabilities.size();
    }
}
