package net.mcreator.MCreatorMCP.engine.resolver;

import net.mcreator.MCreatorMCP.mcp.McpTypes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

public class OperationResolver {

    private static final Logger LOG = LogManager.getLogger("OperationResolver");

    private final CapabilityRegistry registry;

    public OperationResolver(CapabilityRegistry registry) {
        this.registry = registry;
    }

    public ExecutionResult executeOperation(String operationName, Map<String, Object> arguments) {
        LOG.info("Resolving and executing operation: {}", operationName);
        try {
            Object res = registry.invoke(operationName, arguments);
            if (res instanceof McpTypes.ToolResult) {
                McpTypes.ToolResult tr = (McpTypes.ToolResult) res;
                Map<String, Object> data = new LinkedHashMap<>();
                StringBuilder text = new StringBuilder();
                if (tr.getContent() != null) {
                    for (McpTypes.ToolContent c : tr.getContent()) {
                        text.append(c.getText()).append("\n");
                    }
                }
                data.put("output", text.toString().trim());
                return new ExecutionResult(!tr.getIsError(), text.toString().trim(), data);
            } else if (res instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) res;
                return ExecutionResult.ok("Operation completed", map);
            } else {
                return ExecutionResult.ok(String.valueOf(res), new LinkedHashMap<>());
            }
        } catch (Exception e) {
            LOG.error("Operation failed: " + operationName, e);
            return ExecutionResult.error("Operation failed [" + operationName + "]: " + e.getMessage());
        }
    }
}
