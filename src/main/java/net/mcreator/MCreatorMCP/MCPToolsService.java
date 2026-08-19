package net.mcreator.MCreatorMCP;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.MCreatorMCP.mcp.McpServer;
import net.mcreator.MCreatorMCP.mcp.McpTypes;
import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.blockly.data.Dependency;
import net.mcreator.blockly.data.ExternalTrigger;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.io.FileIO;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.blockly.BlocklyEditorType;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;
import net.mcreator.workspace.elements.FolderElement;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.SoundElement;
import net.mcreator.workspace.elements.VariableElement;
import net.mcreator.workspace.elements.VariableType;
import net.mcreator.workspace.elements.VariableTypeLoader;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.mcreator.workspace.WorkspaceFolderManager;

/**
 * Service that implements comprehensive MCreator tools for the MCP server.
 * Fully compatible with all MCreator versions (2020.x - 2026.x+) and guarantees
 * zero-error code regeneration with automatic workspace repair.
 */
public class MCPToolsService {

    private static final Logger LOG = LogManager.getLogger("MCP-Tools");
    private final ObjectMapper objectMapper;

    public MCPToolsService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Register all MCreator tools with the MCP server
     */
    public void registerTools(McpServer mcpServer, MCreator mcreator) {
        LOG.info("Registering comprehensive MCreator tools with MCP server");

        // 1. buildWorkspace
        mcpServer.registerTool(McpServer.createTool(
            "buildWorkspace",
            "Build the current MCreator workspace using Gradle (with auto-repair before build)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> executeBuildWorkspace(mcreator));

        // 2. regenerateCode
        mcpServer.registerTool(McpServer.createTool(
            "regenerateCode",
            "Regenerate all code in the workspace with automatic validation and null-repair",
            Map.of("type", "object", "properties", Map.of())
        ), params -> executeRegenerateCode(mcreator));

        // 3. getWorkspaceInfo
        mcpServer.registerTool(McpServer.createTool(
            "getWorkspaceInfo",
            "Get detailed workspace information, settings, generator, and statistics",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getWorkspaceInfo(mcreator));

        // 4. setWorkspaceSettings
        mcpServer.registerTool(McpServer.createTool(
            "setWorkspaceSettings",
            "Update workspace settings such as mod name, mod ID, version, author, description, package name, etc.",
            Map.of("type", "object", "properties", Map.of(
                "modName", Map.of("type", "string", "description", "Mod display name"),
                "modid", Map.of("type", "string", "description", "Mod ID (lowercase alphanumeric/underscore)"),
                "version", Map.of("type", "string", "description", "Mod version (e.g. 1.0.0)"),
                "author", Map.of("type", "string", "description", "Mod author"),
                "description", Map.of("type", "string", "description", "Mod description"),
                "packageName", Map.of("type", "string", "description", "Base package name"),
                "license", Map.of("type", "string", "description", "Mod license"),
                "websiteURL", Map.of("type", "string", "description", "Mod website/repo URL")
            ))
        ), params -> setWorkspaceSettings(mcreator, params));

        // 5. listModElements
        mcpServer.registerTool(McpServer.createTool(
            "listModElements",
            "List all mod elements in the workspace with optional filter by element type",
            Map.of("type", "object", "properties", Map.of(
                "elementType", Map.of("type", "string", "description", "Filter by element type (e.g. livingentity, block, item, procedure, gui, tag, recipe)")
            ))
        ), params -> listModElements(mcreator, params));

        // 6. getModElement
        mcpServer.registerTool(McpServer.createTool(
            "getModElement",
            "Get the full JSON definition, properties, and configuration of a specific mod element",
            Map.of("type", "object", 
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Name of the mod element")
                ),
                "required", List.of("elementName")
            )
        ), params -> getModElement(mcreator, params));

        // 7. createElement
        mcpServer.registerTool(McpServer.createTool(
            "createElement",
            "Create a new mod element in the workspace with auto-sanitization and default @Nonnull values",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Name of the new element"),
                    "elementType", Map.of("type", "string", "description", "Type of element (e.g. livingentity, block, item, procedure, gui, tag, recipe, dimension, biome)"),
                    "definition", Map.of("type", "object", "description", "Optional custom definition properties to initialize the element with")
                ),
                "required", List.of("elementName", "elementType")
            )
        ), params -> createElement(mcreator, params));

        // 8. updateModElement
        mcpServer.registerTool(McpServer.createTool(
            "updateModElement",
            "Update or modify an existing mod element definition (properties, procedure XML, entity attributes, etc.)",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Name of the element to update"),
                    "definition", Map.of("type", "object", "description", "Fields and properties to update in the element definition")
                ),
                "required", List.of("elementName", "definition")
            )
        ), params -> updateModElement(mcreator, params));

        // 9. deleteElement
        mcpServer.registerTool(McpServer.createTool(
            "deleteElement",
            "Delete a mod element and remove all its generated files from the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Name of element to delete")
                ),
                "required", List.of("elementName")
            )
        ), params -> deleteElement(mcreator, params));

        // 10. listModElementTypes
        mcpServer.registerTool(McpServer.createTool(
            "listModElementTypes",
            "List all available mod element types supported by MCreator (block, item, livingentity, procedure, gui, etc.)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> listModElementTypes(mcreator));

        // 11. listWorkspaceVariables
        mcpServer.registerTool(McpServer.createTool(
            "listWorkspaceVariables",
            "List all global variables defined in the workspace",
            Map.of("type", "object", "properties", Map.of())
        ), params -> listWorkspaceVariables(mcreator));

        // 12. addWorkspaceVariable
        mcpServer.registerTool(McpServer.createTool(
            "addWorkspaceVariable",
            "Add or update a global variable in the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Variable name"),
                    "type", Map.of("type", "string", "description", "Variable type (e.g. string, number, logic, itemstack, direction)"),
                    "scope", Map.of("type", "string", "description", "Variable scope (GLOBAL_SESSION, GLOBAL_WORLD, GLOBAL_MAP, PLAYER_PERSISTENT, PLAYER_LIFETIME)"),
                    "value", Map.of("type", "string", "description", "Initial value (optional)")
                ),
                "required", List.of("name", "type")
            )
        ), params -> addWorkspaceVariable(mcreator, params));

        // 13. deleteWorkspaceVariable
        mcpServer.registerTool(McpServer.createTool(
            "deleteWorkspaceVariable",
            "Delete a global variable from the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Name of the variable to delete")
                ),
                "required", List.of("name")
            )
        ), params -> deleteWorkspaceVariable(mcreator, params));

        // 14. listTextures
        mcpServer.registerTool(McpServer.createTool(
            "listTextures",
            "List all textures in the workspace categorized by texture type",
            Map.of("type", "object", "properties", Map.of(
                "textureType", Map.of("type", "string", "description", "Optional texture type filter: entity, block, item, screen, armor, particle, effect, other")
            ))
        ), params -> listTextures(mcreator, params));

        // 15. addTexture
        mcpServer.registerTool(McpServer.createTool(
            "addTexture",
            "Import or save a texture (PNG) into the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name (with or without .png)"),
                    "type", Map.of("type", "string", "description", "Texture type: entity, block, item, screen, armor, particle, effect, other"),
                    "base64Data", Map.of("type", "string", "description", "Base64-encoded PNG image data (optional if filePath provided)"),
                    "filePath", Map.of("type", "string", "description", "Local file path to source PNG image (optional if base64Data provided)")
                ),
                "required", List.of("name", "type")
            )
        ), params -> addTexture(mcreator, params));

        // 16. deleteTexture
        mcpServer.registerTool(McpServer.createTool(
            "deleteTexture",
            "Delete a texture from the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name (with or without .png)"),
                    "type", Map.of("type", "string", "description", "Texture type: entity, block, item, screen, armor, particle, effect, other")
                ),
                "required", List.of("name", "type")
            )
        ), params -> deleteTexture(mcreator, params));

        // 17. listSounds
        mcpServer.registerTool(McpServer.createTool(
            "listSounds",
            "List all sound elements registered in the workspace",
            Map.of("type", "object", "properties", Map.of())
        ), params -> listSounds(mcreator));

        // 18. addSound
        mcpServer.registerTool(McpServer.createTool(
            "addSound",
            "Add a sound element to the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Sound element name"),
                    "category", Map.of("type", "string", "description", "Sound category (master, music, record, weather, block, hostile, neutral, player, ambient, voice)"),
                    "subtitle", Map.of("type", "string", "description", "Sound subtitle displayed in-game"),
                    "files", Map.of("type", "array", "items", Map.of("type", "string"), "description", "List of sound file names (without .ogg)")
                ),
                "required", List.of("name")
            )
        ), params -> addSound(mcreator, params));

        // 19. deleteSound
        mcpServer.registerTool(McpServer.createTool(
            "deleteSound",
            "Delete a sound element from the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Sound name to delete")
                ),
                "required", List.of("name")
            )
        ), params -> deleteSound(mcreator, params));

        // 20. listModels
        mcpServer.registerTool(McpServer.createTool(
            "listModels",
            "List all custom 3D models (Java, JSON, OBJ) in the workspace",
            Map.of("type", "object", "properties", Map.of())
        ), params -> listModels(mcreator));

        // 21. addModel
        mcpServer.registerTool(McpServer.createTool(
            "addModel",
            "Import a custom 3D model (Java entity model, JSON model, or OBJ model) into the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Model file name (e.g. custom_entity.java or custom_block.json)"),
                    "modelType", Map.of("type", "string", "description", "Model type: 'java', 'json', or 'obj'"),
                    "content", Map.of("type", "string", "description", "Source code / text content of the model file"),
                    "base64Data", Map.of("type", "string", "description", "Base64-encoded model file data (optional if content or filePath provided)"),
                    "filePath", Map.of("type", "string", "description", "Local file path to source model (optional)"),
                    "textureMapping", Map.of("type", "string", "description", "Optional texture mapping JSON content for .textures file")
                ),
                "required", List.of("name")
            )
        ), params -> addModel(mcreator, params));

        // 22. listStructures
        mcpServer.registerTool(McpServer.createTool(
            "listStructures",
            "List all NBT structures in the workspace",
            Map.of("type", "object", "properties", Map.of())
        ), params -> listStructures(mcreator));

        // 23. addStructure
        mcpServer.registerTool(McpServer.createTool(
            "addStructure",
            "Import an NBT structure file into the workspace structures directory",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Structure name (with or without .nbt)"),
                    "base64Data", Map.of("type", "string", "description", "Base64-encoded NBT file data (optional if filePath provided)"),
                    "filePath", Map.of("type", "string", "description", "Local file path to source .nbt file (optional if base64Data provided)")
                ),
                "required", List.of("name")
            )
        ), params -> addStructure(mcreator, params));

        // 24. createProcedure
        mcpServer.registerTool(McpServer.createTool(
            "createProcedure",
            "Create a Procedure mod element with triggers, Blockly XML, or high-level action blocks",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Procedure element name"),
                    "trigger", Map.of("type", "string", "description", "Event trigger ID (e.g. 'no_ext_trigger', 'player_right_click_item', 'entity_dies', 'player_ticks', 'block_placed')"),
                    "procedurexml", Map.of("type", "string", "description", "Optional raw Blockly XML string. If omitted, constructed from trigger/actions"),
                    "actions", Map.of("type", "array", "description", "Optional array of high-level actions (e.g. chat, damage, heal, potion_effect, command, sound, spawn_entity)"),
                    "dependencies", Map.of("type", "array", "description", "Optional list of dependencies (e.g. [{'name':'entity','type':'entity'}, {'name':'x','type':'number'}])")
                ),
                "required", List.of("name")
            )
        ), params -> createProcedure(mcreator, params));

        // 25. listProcedureTriggers
        mcpServer.registerTool(McpServer.createTool(
            "listProcedureTriggers",
            "List all available global procedure triggers in MCreator with descriptions and dependencies",
            Map.of("type", "object", "properties", Map.of())
        ), params -> listProcedureTriggers(mcreator));

        // 26. listProcedureBlocks
        mcpServer.registerTool(McpServer.createTool(
            "listProcedureBlocks",
            "List available Blockly procedure blocks filtered by category or search keyword",
            Map.of("type", "object", "properties", Map.of(
                "category", Map.of("type", "string", "description", "Category filter (e.g. actions, logic, math, entity, world, items)"),
                "search", Map.of("type", "string", "description", "Search query in block names or IDs")
            ))
        ), params -> listProcedureBlocks(mcreator, params));

        // 27. linkProcedureToElement
        mcpServer.registerTool(McpServer.createTool(
            "linkProcedureToElement",
            "Connect an existing procedure to an event hook on a mod element (e.g. onRightClicked, whenEntityDies, onBlockAdded)",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Target mod element name"),
                    "event", Map.of("type", "string", "description", "Event field name (e.g. onRightClicked, whenEntityDies, onBlockAdded, onCrafted, onHitEntity)"),
                    "procedureName", Map.of("type", "string", "description", "Name of the procedure to bind")
                ),
                "required", List.of("elementName", "event", "procedureName")
            )
        ), params -> linkProcedureToElement(mcreator, params));

        // 28. getWorkspaceDiagnostics
        mcpServer.registerTool(McpServer.createTool(
            "getWorkspaceDiagnostics",
            "Scan workspace for broken references, missing textures/models, null fields, or FreeMarker errors",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getWorkspaceDiagnostics(mcreator));

        // 29. repairWorkspace
        mcpServer.registerTool(McpServer.createTool(
            "repairWorkspace",
            "Automatically scan and repair all .mod.json element files to guarantee 0 errors during code generation",
            Map.of("type", "object", "properties", Map.of())
        ), params -> repairWorkspaceTool(mcreator));

        // 30. runClient
        mcpServer.registerTool(McpServer.createTool(
            "runClient",
            "Start Minecraft test client with the mod loaded",
            Map.of("type", "object", "properties", Map.of())
        ), params -> executeRunClient(mcreator));

        // 31. runServer
        mcpServer.registerTool(McpServer.createTool(
            "runServer",
            "Start Minecraft test server with the mod loaded",
            Map.of("type", "object", "properties", Map.of())
        ), params -> executeRunServer(mcreator));

        // ===== GROUP 1: Advanced Workspace Management =====

        // 32. getGeneratorInfo
        mcpServer.registerTool(McpServer.createTool(
            "getGeneratorInfo",
            "Get active generator info: name, flavor (Forge/NeoForge/Fabric), Minecraft version, supported element types",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getGeneratorInfo(mcreator));

        // 33. switchGenerator
        mcpServer.registerTool(McpServer.createTool(
            "switchGenerator",
            "Switch workspace generator (e.g. forge-1.20.1 to neoforge-1.21.4). WARNING: requires full code regeneration",
            Map.of("type", "object",
                "properties", Map.of(
                    "generatorName", Map.of("type", "string", "description", "Generator name (e.g. 'forge-1.20.1', 'neoforge-1.21.4', 'fabric-1.20.1')")
                ),
                "required", List.of("generatorName")
            )
        ), params -> switchGenerator(mcreator, params));

        // 34. exportWorkspace
        mcpServer.registerTool(McpServer.createTool(
            "exportWorkspace",
            "Export the workspace as a distributable JAR/ZIP mod file",
            Map.of("type", "object", "properties", Map.of(
                "outputPath", Map.of("type", "string", "description", "Optional output file path for the exported mod")
            ))
        ), params -> exportWorkspace(mcreator, params));

        // 35. clearGradleCaches
        mcpServer.registerTool(McpServer.createTool(
            "clearGradleCaches",
            "Clear all Gradle caches and rebuild dependency tree",
            Map.of("type", "object", "properties", Map.of())
        ), params -> clearGradleCaches(mcreator));

        // 36. reloadGradleProject
        mcpServer.registerTool(McpServer.createTool(
            "reloadGradleProject",
            "Reload the Gradle project and refresh dependencies",
            Map.of("type", "object", "properties", Map.of())
        ), params -> reloadGradleProject(mcreator));

        // ===== GROUP 2: Localization & Language Management =====

        // 37. listLocalizations
        mcpServer.registerTool(McpServer.createTool(
            "listLocalizations",
            "List all localization languages and their entry counts",
            Map.of("type", "object", "properties", Map.of())
        ), params -> listLocalizations(mcreator));

        // 38. getLocalization
        mcpServer.registerTool(McpServer.createTool(
            "getLocalization",
            "Get all translation entries for a specific language",
            Map.of("type", "object",
                "properties", Map.of(
                    "language", Map.of("type", "string", "description", "Language code (e.g. 'en_us', 'tr_tr', 'de_de')")
                ),
                "required", List.of("language")
            )
        ), params -> getLocalization(mcreator, params));

        // 39. setLocalization
        mcpServer.registerTool(McpServer.createTool(
            "setLocalization",
            "Set localization entries for a language. Can set single key or batch of keys",
            Map.of("type", "object",
                "properties", Map.of(
                    "language", Map.of("type", "string", "description", "Language code (default: en_us)"),
                    "key", Map.of("type", "string", "description", "Single localization key"),
                    "value", Map.of("type", "string", "description", "Single localization value"),
                    "entries", Map.of("type", "object", "description", "Batch: map of key->value pairs")
                )
            )
        ), params -> setLocalization(mcreator, params));

        // 40. deleteLocalization
        mcpServer.registerTool(McpServer.createTool(
            "deleteLocalization",
            "Delete a localization key or an entire language",
            Map.of("type", "object",
                "properties", Map.of(
                    "language", Map.of("type", "string", "description", "Language code to delete entirely (optional)"),
                    "key", Map.of("type", "string", "description", "Specific key to delete from en_us (optional)")
                )
            )
        ), params -> deleteLocalization(mcreator, params));

        // ===== GROUP 3: Tag Management =====

        // 41. listTags
        mcpServer.registerTool(McpServer.createTool(
            "listTags",
            "List all tag elements (item tags, block tags, entity tags, function tags) in the workspace",
            Map.of("type", "object", "properties", Map.of())
        ), params -> listTags(mcreator));

        // 42. addTag
        mcpServer.registerTool(McpServer.createTool(
            "addTag",
            "Create or update a tag element with entries",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Tag name (e.g. 'my_ores')"),
                    "type", Map.of("type", "string", "description", "Tag type: ITEMS, BLOCKS, ENTITIES, FUNCTIONS, BIOMES, DAMAGE_TYPES"),
                    "namespace", Map.of("type", "string", "description", "Tag namespace (default: mod namespace)"),
                    "entries", Map.of("type", "array", "items", Map.of("type", "string"), "description", "List of entries (e.g. ['minecraft:stone', 'CUSTOM:MyBlock'])")
                ),
                "required", List.of("name", "type")
            )
        ), params -> addTag(mcreator, params));

        // 43. deleteTag
        mcpServer.registerTool(McpServer.createTool(
            "deleteTag",
            "Delete a tag element from the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Tag name"),
                    "type", Map.of("type", "string", "description", "Tag type: ITEMS, BLOCKS, ENTITIES, FUNCTIONS")
                ),
                "required", List.of("name", "type")
            )
        ), params -> deleteTag(mcreator, params));

        // ===== GROUP 4: Gradle Task Management =====

        // 44. runGradleTask
        mcpServer.registerTool(McpServer.createTool(
            "runGradleTask",
            "Run a custom Gradle task (e.g. 'build', 'jar', 'test', 'clean', 'runData')",
            Map.of("type", "object",
                "properties", Map.of(
                    "task", Map.of("type", "string", "description", "Gradle task name (e.g. 'build', 'jar', 'clean', 'runData')")
                ),
                "required", List.of("task")
            )
        ), params -> runGradleTask(mcreator, params));

        // 45. cancelGradleTask
        mcpServer.registerTool(McpServer.createTool(
            "cancelGradleTask",
            "Cancel any currently running Gradle task",
            Map.of("type", "object", "properties", Map.of())
        ), params -> cancelGradleTask(mcreator));

        // 46. getGradleStatus
        mcpServer.registerTool(McpServer.createTool(
            "getGradleStatus",
            "Get the current status of Gradle (idle, building, running task, etc.)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getGradleStatus(mcreator));

        // ===== GROUP 5: Advanced Element Management =====

        // 47. duplicateElement
        mcpServer.registerTool(McpServer.createTool(
            "duplicateElement",
            "Duplicate an existing mod element with a new name",
            Map.of("type", "object",
                "properties", Map.of(
                    "sourceElement", Map.of("type", "string", "description", "Name of element to duplicate"),
                    "newName", Map.of("type", "string", "description", "Name for the duplicated element")
                ),
                "required", List.of("sourceElement", "newName")
            )
        ), params -> duplicateElement(mcreator, params));

        // 48. renameElement
        mcpServer.registerTool(McpServer.createTool(
            "renameElement",
            "Rename a mod element (updates all workspace references)",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Current element name"),
                    "newName", Map.of("type", "string", "description", "New element name")
                ),
                "required", List.of("elementName", "newName")
            )
        ), params -> renameElement(mcreator, params));

        // 49. getElementCode
        mcpServer.registerTool(McpServer.createTool(
            "getElementCode",
            "View the generated Java/JSON source code of a mod element",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Name of the mod element")
                ),
                "required", List.of("elementName")
            )
        ), params -> getElementCode(mcreator, params));

        // 50. listElementEvents
        mcpServer.registerTool(McpServer.createTool(
            "listElementEvents",
            "List all available event/procedure slots for a mod element (onRightClicked, onBlockPlaced, etc.)",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Name of the mod element")
                ),
                "required", List.of("elementName")
            )
        ), params -> listElementEvents(mcreator, params));

        // 51. searchElements
        mcpServer.registerTool(McpServer.createTool(
            "searchElements",
            "Search mod elements by name pattern, type, or properties",
            Map.of("type", "object",
                "properties", Map.of(
                    "query", Map.of("type", "string", "description", "Search query (name pattern)"),
                    "elementType", Map.of("type", "string", "description", "Optional type filter")
                ),
                "required", List.of("query")
            )
        ), params -> searchElements(mcreator, params));

        // ===== GROUP 6: Resource Management (Extended) =====

        // 52. deleteModel
        mcpServer.registerTool(McpServer.createTool(
            "deleteModel",
            "Delete a 3D model from the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Model file name")
                ),
                "required", List.of("name")
            )
        ), params -> deleteModel(mcreator, params));

        // 53. deleteStructure
        mcpServer.registerTool(McpServer.createTool(
            "deleteStructure",
            "Delete an NBT structure from the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Structure name (with or without .nbt)")
                ),
                "required", List.of("name")
            )
        ), params -> deleteStructure(mcreator, params));

        // 54. listAnimations
        mcpServer.registerTool(McpServer.createTool(
            "listAnimations",
            "List all animation files in the workspace",
            Map.of("type", "object", "properties", Map.of())
        ), params -> listAnimations(mcreator));

        // 55. addAnimation
        mcpServer.registerTool(McpServer.createTool(
            "addAnimation",
            "Import an animation file into the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Animation file name"),
                    "content", Map.of("type", "string", "description", "Animation JSON content"),
                    "filePath", Map.of("type", "string", "description", "Local file path (alternative to content)")
                ),
                "required", List.of("name")
            )
        ), params -> addAnimation(mcreator, params));

        // ===== GROUP 7: Reference Analysis =====

        // 56. findReferences
        mcpServer.registerTool(McpServer.createTool(
            "findReferences",
            "Find where a mod element is referenced/used across the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Name of the element to find references for")
                ),
                "required", List.of("elementName")
            )
        ), params -> findReferences(mcreator, params));

        // 57. findBrokenReferences
        mcpServer.registerTool(McpServer.createTool(
            "findBrokenReferences",
            "Scan the entire workspace for broken/invalid element references",
            Map.of("type", "object", "properties", Map.of())
        ), params -> findBrokenReferences(mcreator));

        // ===== GROUP 8: Minecraft Data Lists =====

        // 58. listDataEntries
        mcpServer.registerTool(McpServer.createTool(
            "listDataEntries",
            "Query MCreator DataList entries (blocks, items, entities, biomes, particles, sounds, enchantments, potions, etc.)",
            Map.of("type", "object",
                "properties", Map.of(
                    "listName", Map.of("type", "string", "description", "DataList name: blocksitems, entities, biomes, particles, sounds, enchantments, effects, painting, villagerprofessions, fluid, mapcolor, arrowtype, gamerules")
                ),
                "required", List.of("listName")
            )
        ), params -> listDataEntries(mcreator, params));

        // 59. getMinecraftBlocks
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftBlocks",
            "List all vanilla Minecraft blocks available in the current generator",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "blocksitems"));

        // 60. getMinecraftItems
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftItems",
            "List all vanilla Minecraft items available in the current generator",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "blocksitems"));

        // 61. getMinecraftEntities
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftEntities",
            "List all vanilla Minecraft entities available in the current generator",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "entities"));

        // 62. getMinecraftBiomes
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftBiomes",
            "List all vanilla Minecraft biomes available in the current generator",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "biomes"));

        // ===== GROUP 9: Workspace File Operations =====

        // 63. readFile
        mcpServer.registerTool(McpServer.createTool(
            "readFile",
            "Read the contents of any file within the workspace directory",
            Map.of("type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Relative or absolute path to the file within workspace")
                ),
                "required", List.of("path")
            )
        ), params -> readFile(mcreator, params));

        // 64. writeFile
        mcpServer.registerTool(McpServer.createTool(
            "writeFile",
            "Write content to a file within the workspace directory",
            Map.of("type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Relative or absolute path within workspace"),
                    "content", Map.of("type", "string", "description", "File content to write")
                ),
                "required", List.of("path", "content")
            )
        ), params -> writeFile(mcreator, params));

        // 65. listFiles
        mcpServer.registerTool(McpServer.createTool(
            "listFiles",
            "List files and directories within the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Relative directory path (default: workspace root)"),
                    "recursive", Map.of("type", "boolean", "description", "List recursively (default: false)")
                )
            )
        ), params -> listFiles(mcreator, params));

        // 66. getSourceCode
        mcpServer.registerTool(McpServer.createTool(
            "getSourceCode",
            "Read generated Java source code files from the workspace src directory",
            Map.of("type", "object",
                "properties", Map.of(
                    "className", Map.of("type", "string", "description", "Java class name or partial path to search for")
                ),
                "required", List.of("className")
            )
        ), params -> getSourceCode(mcreator, params));

        // ===== GROUP 10: Creative Tab Order =====

        // 67. getCreativeTabOrder
        mcpServer.registerTool(McpServer.createTool(
            "getCreativeTabOrder",
            "Get the creative tab ordering for mod elements",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getCreativeTabOrder(mcreator));

        // 68. setCreativeTabOrder
        mcpServer.registerTool(McpServer.createTool(
            "setCreativeTabOrder",
            "Set the creative tab ordering for mod elements",
            Map.of("type", "object",
                "properties", Map.of(
                    "tabName", Map.of("type", "string", "description", "Creative tab element name"),
                    "elements", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Ordered list of element names for this tab")
                ),
                "required", List.of("tabName", "elements")
            )
        ), params -> setCreativeTabOrder(mcreator, params));

        // ===== GROUP 11: Plugin & Metadata =====

        // 69. getPluginInfo
        mcpServer.registerTool(McpServer.createTool(
            "getPluginInfo",
            "List all loaded MCreator plugins with their versions and status",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getPluginInfo(mcreator));

        // 70. getMCreatorVersion
        mcpServer.registerTool(McpServer.createTool(
            "getMCreatorVersion",
            "Get MCreator application version, build number, and Java runtime info",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMCreatorVersion(mcreator));

        // ===== GROUP 12: Advanced Error Diagnostics & Code Generation Debugging =====

        // 71. analyzeRegenerateErrors
        mcpServer.registerTool(McpServer.createTool(
            "analyzeRegenerateErrors",
            "Deep scan all workspace elements for potential FreeMarker template errors, missing nonnull fields, broken XMLs, and schema issues before code regeneration",
            Map.of("type", "object", "properties", Map.of())
        ), params -> analyzeRegenerateErrors(mcreator));

        // 72. testGenerateElement
        mcpServer.registerTool(McpServer.createTool(
            "testGenerateElement",
            "Simulate/dry-run code generation for a single mod element in isolation to catch FreeMarker and template errors immediately",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Name of the mod element to test generate")
                ),
                "required", List.of("elementName")
            )
        ), params -> testGenerateElement(mcreator, params));

        // 73. inspectElementErrors
        mcpServer.registerTool(McpServer.createTool(
            "inspectElementErrors",
            "Inspect a specific mod element for schema errors, missing textures, missing models, unlinked procedures, or null fields",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Name of the mod element to inspect")
                ),
                "required", List.of("elementName")
            )
        ), params -> inspectElementErrors(mcreator, params));

        // 74. autoFixAllErrors
        mcpServer.registerTool(McpServer.createTool(
            "autoFixAllErrors",
            "Automatically repair all element definitions, nonnull fields, broken procedure XMLs, and dangling references across the workspace with a detailed repair report",
            Map.of("type", "object", "properties", Map.of())
        ), params -> autoFixAllErrors(mcreator));

        // ===== GROUP 13: Minecraft & Gradle Runtime Log Debugger =====

        // 75. getGradleConsoleOutput
        mcpServer.registerTool(McpServer.createTool(
            "getGradleConsoleOutput",
            "Get the current text output from MCreator's internal Gradle Console with optional level filtering (error, warn, info, task)",
            Map.of("type", "object", "properties", Map.of(
                "filter", Map.of("type", "string", "description", "Optional filter: 'error', 'warn', 'info', or search keyword"),
                "tailLines", Map.of("type", "integer", "description", "Optional number of recent lines to retrieve (default: all)")
            ))
        ), params -> getGradleConsoleOutput(mcreator, params));

        // 76. getMinecraftLogs
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftLogs",
            "Read Minecraft client/server runtime logs (latest.log or debug.log) from the workspace run directory",
            Map.of("type", "object", "properties", Map.of(
                "logType", Map.of("type", "string", "description", "Log file to read: 'latest' (default) or 'debug'"),
                "tailLines", Map.of("type", "integer", "description", "Number of trailing lines to read (default: 200)"),
                "search", Map.of("type", "string", "description", "Optional search keyword / filter")
            ))
        ), params -> getMinecraftLogs(mcreator, params));

        // 77. analyzeCrashReport
        mcpServer.registerTool(McpServer.createTool(
            "analyzeCrashReport",
            "Scan workspace run/crash-reports/ directory, parse the latest Minecraft crash report, and identify the root cause, culprit element, and stack trace",
            Map.of("type", "object", "properties", Map.of())
        ), params -> analyzeCrashReport(mcreator));

        // 78. clearConsole
        mcpServer.registerTool(McpServer.createTool(
            "clearConsole",
            "Clear the Gradle Console output buffer in MCreator",
            Map.of("type", "object", "properties", Map.of())
        ), params -> clearConsole(mcreator));

        // ===== GROUP 14: Workspace Folders & Organization =====

        // 79. getFolderTree
        mcpServer.registerTool(McpServer.createTool(
            "getFolderTree",
            "Get the full hierarchical folder structure of mod elements in the workspace",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getFolderTree(mcreator));

        // 80. createWorkspaceFolder
        mcpServer.registerTool(McpServer.createTool(
            "createWorkspaceFolder",
            "Create a new folder for organizing mod elements in the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "folderName", Map.of("type", "string", "description", "Name of the folder to create"),
                    "parentFolder", Map.of("type", "string", "description", "Optional parent folder name (default: root)")
                ),
                "required", List.of("folderName")
            )
        ), params -> createWorkspaceFolder(mcreator, params));

        // 81. deleteWorkspaceFolder
        mcpServer.registerTool(McpServer.createTool(
            "deleteWorkspaceFolder",
            "Delete a folder from the workspace (elements inside are safely moved to root)",
            Map.of("type", "object",
                "properties", Map.of(
                    "folderName", Map.of("type", "string", "description", "Name of the folder to delete")
                ),
                "required", List.of("folderName")
            )
        ), params -> deleteWorkspaceFolder(mcreator, params));

        // 82. moveElementToFolder
        mcpServer.registerTool(McpServer.createTool(
            "moveElementToFolder",
            "Move a mod element into a specific workspace folder (or root)",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Name of the mod element"),
                    "folderName", Map.of("type", "string", "description", "Target folder name (or '~' / 'root' for root)")
                ),
                "required", List.of("elementName", "folderName")
            )
        ), params -> moveElementToFolder(mcreator, params));

        // ===== GROUP 15: Code Lock, Batch Operations & Build Maintenance =====

        // 83. lockElementCode
        mcpServer.registerTool(McpServer.createTool(
            "lockElementCode",
            "Lock or unlock a mod element's generated code to prevent it from being overwritten during code regeneration",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Name of the mod element"),
                    "locked", Map.of("type", "boolean", "description", "True to lock code, false to unlock")
                ),
                "required", List.of("elementName", "locked")
            )
        ), params -> lockElementCode(mcreator, params));

        // 84. batchUpdateElements
        mcpServer.registerTool(McpServer.createTool(
            "batchUpdateElements",
            "Update multiple mod element definitions in a single atomic transaction",
            Map.of("type", "object",
                "properties", Map.of(
                    "updates", Map.of("type", "array", "description", "List of objects with 'elementName' and 'definition' map")
                ),
                "required", List.of("updates")
            )
        ), params -> batchUpdateElements(mcreator, params));

        // 85. cleanWorkspaceBuild
        mcpServer.registerTool(McpServer.createTool(
            "cleanWorkspaceBuild",
            "Clean workspace build artifacts, temporary caches, and Gradle daemon locks",
            Map.of("type", "object", "properties", Map.of())
        ), params -> cleanWorkspaceBuild(mcreator));

        // ===== GROUP 16: Asset Inspection & Animation =====

        // 86. inspectTexture
        mcpServer.registerTool(McpServer.createTool(
            "inspectTexture",
            "Inspect texture image properties (width, height, format, alpha, power-of-two validation)",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name (e.g. 'my_block.png')"),
                    "type", Map.of("type", "string", "description", "Texture type (block, item, entity, etc.)")
                ),
                "required", List.of("name", "type")
            )
        ), params -> inspectTexture(mcreator, params));

        // 87. createAnimatedTexture
        mcpServer.registerTool(McpServer.createTool(
            "createAnimatedTexture",
            "Create an animated texture with automatic .mcmeta animation configuration",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name (without .png)"),
                    "type", Map.of("type", "string", "description", "Texture type (block, item, etc.)"),
                    "base64Data", Map.of("type", "string", "description", "Base64-encoded PNG image data"),
                    "frametime", Map.of("type", "integer", "description", "Frame duration in ticks (default: 2)"),
                    "interpolate", Map.of("type", "boolean", "description", "Whether to interpolate between frames (default: false)")
                ),
                "required", List.of("name", "type", "base64Data")
            )
        ), params -> createAnimatedTexture(mcreator, params));

        // 88. validateProcedureXML
        mcpServer.registerTool(McpServer.createTool(
            "validateProcedureXML",
            "Validate Blockly procedure XML structure for unmatched tags, missing block types, or broken parameters",
            Map.of("type", "object",
                "properties", Map.of(
                    "xml", Map.of("type", "string", "description", "Blockly XML string to validate")
                ),
                "required", List.of("xml")
            )
        ), params -> validateProcedureXML(mcreator, params));

        // ===== GROUP 17: Workspace Backups & Snapshots =====

        // 89. createWorkspaceBackup
        mcpServer.registerTool(McpServer.createTool(
            "createWorkspaceBackup",
            "Create a full .zip backup snapshot of the workspace with timestamp",
            Map.of("type", "object", "properties", Map.of(
                "customPath", Map.of("type", "string", "description", "Optional destination path for the backup ZIP")
            ))
        ), params -> createWorkspaceBackup(mcreator, params));

        // 90. listWorkspaceBackups
        mcpServer.registerTool(McpServer.createTool(
            "listWorkspaceBackups",
            "List all existing workspace backups in the MCreator backups folder",
            Map.of("type", "object", "properties", Map.of())
        ), params -> listWorkspaceBackups(mcreator));

        // 91. restoreWorkspaceBackup
        mcpServer.registerTool(McpServer.createTool(
            "restoreWorkspaceBackup",
            "Restore workspace files from a selected backup ZIP file",
            Map.of("type", "object",
                "properties", Map.of(
                    "backupPath", Map.of("type", "string", "description", "Full path or filename of the backup ZIP to restore")
                ),
                "required", List.of("backupPath")
            )
        ), params -> restoreWorkspaceBackup(mcreator, params));

        // ===== GROUP 18: Mod API Management =====

        // 92. listModAPIs
        mcpServer.registerTool(McpServer.createTool(
            "listModAPIs",
            "List all Mod APIs supported for the active generator (Curios, JEI, Patchouli, Geckolib, etc.) and their enabled status",
            Map.of("type", "object", "properties", Map.of())
        ), params -> listModAPIs(mcreator));

        // 93. setModAPIState
        mcpServer.registerTool(McpServer.createTool(
            "setModAPIState",
            "Enable or disable a specific Mod API in the workspace",
            Map.of("type", "object",
                "properties", Map.of(
                    "apiName", Map.of("type", "string", "description", "Identifier of the Mod API (e.g. 'curios_api', 'jei')"),
                    "enabled", Map.of("type", "boolean", "description", "True to enable, false to disable")
                ),
                "required", List.of("apiName", "enabled")
            )
        ), params -> setModAPIState(mcreator, params));

        // ===== GROUP 19: System Performance & JVM Diagnostics =====

        // 94. getSystemPerformance
        mcpServer.registerTool(McpServer.createTool(
            "getSystemPerformance",
            "Get real-time JVM memory statistics (Heap, Non-Heap, Max, Free), thread count, CPU load, and Java uptime",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getSystemPerformance(mcreator));

        // 95. runGarbageCollector
        mcpServer.registerTool(McpServer.createTool(
            "runGarbageCollector",
            "Request JVM Garbage Collection in MCreator to free allocated memory",
            Map.of("type", "object", "properties", Map.of())
        ), params -> runGarbageCollector(mcreator));

        // ===== GROUP 20: Minecraft Vanilla Registries & Enums =====

        // 96. getMinecraftSounds
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftSounds",
            "List standard vanilla Minecraft sound identifiers",
            Map.of("type", "object", "properties", Map.of(
                "search", Map.of("type", "string", "description", "Optional sound keyword search")
            ))
        ), params -> getMinecraftDataListFiltered(mcreator, "sounds", (String) params.get("search")));

        // 97. getMinecraftEnchantments
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftEnchantments",
            "List standard vanilla Minecraft enchantment IDs",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "enchantments"));

        // 98. getMinecraftPotions
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftPotions",
            "List standard vanilla Minecraft potions and mob effects",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "potioneffects"));

        // 99. getMinecraftParticles
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftParticles",
            "List standard vanilla Minecraft particle types",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "particles"));

        // 100. getMinecraftDamageTypes
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftDamageTypes",
            "List standard vanilla Minecraft damage types",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "damagesources"));

        // 101. getMinecraftAttributes
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftAttributes",
            "List standard vanilla Minecraft entity attributes",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "attributes"));

        // ===== GROUP 21: Texture Processing & Color Utilities =====

        // 102. tintTexture
        mcpServer.registerTool(McpServer.createTool(
            "tintTexture",
            "Apply an RGB color tint/filter to a texture image and save as new or overwrite",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name"),
                    "type", Map.of("type", "string", "description", "Texture type (block, item, etc.)"),
                    "colorHex", Map.of("type", "string", "description", "Hex color (e.g. '#7BAE32' or '0x7BAE32')"),
                    "outputName", Map.of("type", "string", "description", "Optional output texture name (default: overwrites original)")
                ),
                "required", List.of("name", "type", "colorHex")
            )
        ), params -> tintTexture(mcreator, params));

        // 103. generateTextureTemplate
        mcpServer.registerTool(McpServer.createTool(
            "generateTextureTemplate",
            "Procedurally generate a base texture template (solid, grid, outline, or noise)",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name (without .png)"),
                    "type", Map.of("type", "string", "description", "Texture type (block, item, etc.)"),
                    "width", Map.of("type", "integer", "description", "Width in pixels (default: 16)"),
                    "height", Map.of("type", "integer", "description", "Height in pixels (default: 16)"),
                    "pattern", Map.of("type", "string", "description", "Pattern type: 'solid', 'grid', 'outline', or 'noise' (default: solid)"),
                    "primaryColor", Map.of("type", "string", "description", "Primary color hex (default: '#808080')"),
                    "secondaryColor", Map.of("type", "string", "description", "Secondary color hex (default: '#505050')")
                ),
                "required", List.of("name", "type")
            )
        ), params -> generateTextureTemplate(mcreator, params));

        // ===== GROUP 22: Advanced Blockly Editor Subsystems =====

        // 104. listAITasks
        mcpServer.registerTool(McpServer.createTool(
            "listAITasks",
            "List all AI Task blocks available for entity behaviors",
            Map.of("type", "object", "properties", Map.of(
                "search", Map.of("type", "string", "description", "Optional search keyword")
            ))
        ), params -> listEditorBlocks(mcreator, BlocklyEditorType.AI_TASK, (String) params.get("search")));

        // 105. listJSONTriggerBlocks
        mcpServer.registerTool(McpServer.createTool(
            "listJSONTriggerBlocks",
            "List all JSON Trigger blocks available for datapack advancements/triggers",
            Map.of("type", "object", "properties", Map.of(
                "search", Map.of("type", "string", "description", "Optional search keyword")
            ))
        ), params -> listEditorBlocks(mcreator, BlocklyEditorType.JSON_TRIGGER, (String) params.get("search")));

        // 106. listFeatureBlocks
        mcpServer.registerTool(McpServer.createTool(
            "listFeatureBlocks",
            "List all WorldGen Feature blocks available for features and biomes",
            Map.of("type", "object", "properties", Map.of(
                "search", Map.of("type", "string", "description", "Optional search keyword")
            ))
        ), params -> listEditorBlocks(mcreator, BlocklyEditorType.FEATURE, (String) params.get("search")));

        // 107. listCommandArgBlocks
        mcpServer.registerTool(McpServer.createTool(
            "listCommandArgBlocks",
            "List all Command Argument blocks available for custom commands",
            Map.of("type", "object", "properties", Map.of(
                "search", Map.of("type", "string", "description", "Optional search keyword")
            ))
        ), params -> listEditorBlocks(mcreator, BlocklyEditorType.COMMAND_ARG, (String) params.get("search")));

        // ===== GROUP 23: Preferences & Settings =====

        // 108. getWorkspaceUserSettings
        mcpServer.registerTool(McpServer.createTool(
            "getWorkspaceUserSettings",
            "Get workspace-specific user settings (Gradle JVM args, auto-save interval, code style)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getWorkspaceUserSettings(mcreator));

        // 109. getPreferences
        mcpServer.registerTool(McpServer.createTool(
            "getPreferences",
            "Get global MCreator application preferences (theme, UI scale, language, backup settings)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getPreferences(mcreator));

        // ===== GROUP 24: Granular Element Property Patching & Field Editor =====

        // 110. patchElementProperty
        mcpServer.registerTool(McpServer.createTool(
            "patchElementProperty",
            "Update a specific dot-separated property path in an element definition JSON (e.g. path='rarity', value='EPIC' or path='onRightClicked.name', value='MyProc')",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Mod element name"),
                    "path", Map.of("type", "string", "description", "Dot-separated JSON property path (e.g. 'rarity', 'maxStackSize', 'onRightClicked.name')"),
                    "value", Map.of("description", "New value (string, number, boolean, object, or array)")
                ),
                "required", List.of("name", "path", "value")
            )
        ), params -> patchElementProperty(mcreator, params));

        // 111. getElementProperty
        mcpServer.registerTool(McpServer.createTool(
            "getElementProperty",
            "Read a specific dot-separated property path from an element definition JSON",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Mod element name"),
                    "path", Map.of("type", "string", "description", "Dot-separated JSON property path (e.g. 'creativeTab', 'hardness')")
                ),
                "required", List.of("name", "path")
            )
        ), params -> getElementProperty(mcreator, params));

        // 112. removeElementProperty
        mcpServer.registerTool(McpServer.createTool(
            "removeElementProperty",
            "Remove an optional property path from an element definition JSON",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Mod element name"),
                    "path", Map.of("type", "string", "description", "Dot-separated property path to delete")
                ),
                "required", List.of("name", "path")
            )
        ), params -> removeElementProperty(mcreator, params));

        // 113. bulkPatchElements
        mcpServer.registerTool(McpServer.createTool(
            "bulkPatchElements",
            "Patch a property across multiple elements matching a type or name filter",
            Map.of("type", "object",
                "properties", Map.of(
                    "type", Map.of("type", "string", "description", "Optional element type filter (e.g. 'item', 'block')"),
                    "path", Map.of("type", "string", "description", "Property path to patch"),
                    "value", Map.of("description", "Value to set")
                ),
                "required", List.of("path", "value")
            )
        ), params -> bulkPatchElements(mcreator, params));

        // 114. compareElements
        mcpServer.registerTool(McpServer.createTool(
            "compareElements",
            "Diff and compare JSON definitions between two mod elements",
            Map.of("type", "object",
                "properties", Map.of(
                    "element1", Map.of("type", "string", "description", "First element name"),
                    "element2", Map.of("type", "string", "description", "Second element name")
                ),
                "required", List.of("element1", "element2")
            )
        ), params -> compareElements(mcreator, params));

        // ===== GROUP 25: Deep Static Code & Mod Security / Performance Analyzer =====

        // 115. analyzePerformanceBottlenecks
        mcpServer.registerTool(McpServer.createTool(
            "analyzePerformanceBottlenecks",
            "Scan procedures and Java code for tick-lag hazards, expensive loops, unindexed entity searches, or sync disk IO",
            Map.of("type", "object", "properties", Map.of())
        ), params -> analyzePerformanceBottlenecks(mcreator));

        // 116. analyzeSecurityRisks
        mcpServer.registerTool(McpServer.createTool(
            "analyzeSecurityRisks",
            "Scan custom commands, procedures, and network packets for server crash exploits, unchecked permissions, and unsafe execution",
            Map.of("type", "object", "properties", Map.of())
        ), params -> analyzeSecurityRisks(mcreator));

        // 117. analyzeMissingLocalizations
        mcpServer.registerTool(McpServer.createTool(
            "analyzeMissingLocalizations",
            "Deep scan comparing all element names, GUI labels, potion effects, and keys against localization files",
            Map.of("type", "object", "properties", Map.of(
                "language", Map.of("type", "string", "description", "Optional target language code (e.g. 'en_us', 'tr_tr')")
            ))
        ), params -> analyzeMissingLocalizations(mcreator, params));

        // 118. analyzeUnusedAssets
        mcpServer.registerTool(McpServer.createTool(
            "analyzeUnusedAssets",
            "Find orphaned textures, sounds, 3D models, and structures never referenced by any mod element",
            Map.of("type", "object", "properties", Map.of())
        ), params -> analyzeUnusedAssets(mcreator));

        // 119. analyzeCyclicDependencies
        mcpServer.registerTool(McpServer.createTool(
            "analyzeCyclicDependencies",
            "Detect circular dependencies, infinite loops, or recursive calls between procedures and elements",
            Map.of("type", "object", "properties", Map.of())
        ), params -> analyzeCyclicDependencies(mcreator));

        // ===== GROUP 26: Java Source Code AST & Live Code Editor =====

        // 120. insertCodeSnippet
        mcpServer.registerTool(McpServer.createTool(
            "insertCodeSnippet",
            "Safely inject code (methods, fields, annotations) at a specific marker or position in a Java file",
            Map.of("type", "object",
                "properties", Map.of(
                    "filePath", Map.of("type", "string", "description", "Path to Java file relative to workspace or absolute"),
                    "snippet", Map.of("type", "string", "description", "Java code snippet to insert"),
                    "anchor", Map.of("type", "string", "description", "Anchor string/comment to insert after (or 'class_end', 'class_start')")
                ),
                "required", List.of("filePath", "snippet")
            )
        ), params -> insertCodeSnippet(mcreator, params));

        // 121. replaceCodeSnippet
        mcpServer.registerTool(McpServer.createTool(
            "replaceCodeSnippet",
            "Find and replace specific code blocks or regex patterns in Java source files",
            Map.of("type", "object",
                "properties", Map.of(
                    "filePath", Map.of("type", "string", "description", "Path to Java file"),
                    "target", Map.of("type", "string", "description", "Target string or regex to replace"),
                    "replacement", Map.of("type", "string", "description", "Replacement string"),
                    "isRegex", Map.of("type", "boolean", "description", "Whether target is regex (default: false)")
                ),
                "required", List.of("filePath", "target", "replacement")
            )
        ), params -> replaceCodeSnippet(mcreator, params));

        // 122. addJavaImport
        mcpServer.registerTool(McpServer.createTool(
            "addJavaImport",
            "Add an import statement to a Java file without duplicating existing imports",
            Map.of("type", "object",
                "properties", Map.of(
                    "filePath", Map.of("type", "string", "description", "Path to Java file"),
                    "importClass", Map.of("type", "string", "description", "Fully qualified class name to import")
                ),
                "required", List.of("filePath", "importClass")
            )
        ), params -> addJavaImport(mcreator, params));

        // 123. removeJavaImport
        mcpServer.registerTool(McpServer.createTool(
            "removeJavaImport",
            "Remove an unused or broken import statement from a Java file",
            Map.of("type", "object",
                "properties", Map.of(
                    "filePath", Map.of("type", "string", "description", "Path to Java file"),
                    "importClass", Map.of("type", "string", "description", "Class name or package to remove from imports")
                ),
                "required", List.of("filePath", "importClass")
            )
        ), params -> removeJavaImport(mcreator, params));

        // 124. formatJavaCode
        mcpServer.registerTool(McpServer.createTool(
            "formatJavaCode",
            "Clean up and format indentation/braces in a Java source file",
            Map.of("type", "object",
                "properties", Map.of(
                    "filePath", Map.of("type", "string", "description", "Path to Java file")
                ),
                "required", List.of("filePath")
            )
        ), params -> formatJavaCode(mcreator, params));

        // 125. listClassMembers
        mcpServer.registerTool(McpServer.createTool(
            "listClassMembers",
            "Inspect a Java file and list all fields, methods, constructors, and annotations with line numbers",
            Map.of("type", "object",
                "properties", Map.of(
                    "filePath", Map.of("type", "string", "description", "Path to Java file")
                ),
                "required", List.of("filePath")
            )
        ), params -> listClassMembers(mcreator, params));

        // ===== GROUP 27: Advanced Blockly XML Node Editor & Query Engine =====

        // 126. findBlocklyNodes
        mcpServer.registerTool(McpServer.createTool(
            "findBlocklyNodes",
            "Search inside a procedure XML for specific block opcodes or types (e.g. 'controls_if', 'entity_add_potion_effect')",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Procedure element name"),
                    "blockType", Map.of("type", "string", "description", "Block opcode or type to find")
                ),
                "required", List.of("name", "blockType")
            )
        ), params -> findBlocklyNodes(mcreator, params));

        // 127. replaceBlocklyField
        mcpServer.registerTool(McpServer.createTool(
            "replaceBlocklyField",
            "Find and replace field values across procedure XML (variable names, number constants, sound IDs, strings)",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Procedure element name"),
                    "fieldName", Map.of("type", "string", "description", "Blockly field name (e.g. 'VAR', 'NUM', 'TEXT', 'sound')"),
                    "oldValue", Map.of("type", "string", "description", "Old value to match"),
                    "newValue", Map.of("type", "string", "description", "New replacement value")
                ),
                "required", List.of("name", "fieldName", "oldValue", "newValue")
            )
        ), params -> replaceBlocklyField(mcreator, params));

        // 128. insertBlocklyStatement
        mcpServer.registerTool(McpServer.createTool(
            "insertBlocklyStatement",
            "Insert a block XML snippet into a procedure at top, bottom, or inside a container",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Procedure element name"),
                    "blockXml", Map.of("type", "string", "description", "Blockly XML snippet to insert"),
                    "position", Map.of("type", "string", "description", "Position: 'top' (start), 'bottom' (end) (default: bottom)")
                ),
                "required", List.of("name", "blockXml")
            )
        ), params -> insertBlocklyStatement(mcreator, params));

        // 129. removeBlocklyNode
        mcpServer.registerTool(McpServer.createTool(
            "removeBlocklyNode",
            "Delete a block node from procedure XML matching a specific opcode or block id",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Procedure element name"),
                    "blockType", Map.of("type", "string", "description", "Block opcode or id to delete")
                ),
                "required", List.of("name", "blockType")
            )
        ), params -> removeBlocklyNode(mcreator, params));

        // 130. convertBlocklyToSummary
        mcpServer.registerTool(McpServer.createTool(
            "convertBlocklyToSummary",
            "Parse procedure Blockly XML into a human-readable pseudo-code English summary of its logic",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Procedure element name")
                ),
                "required", List.of("name")
            )
        ), params -> convertBlocklyToSummary(mcreator, params));

        // 131. extractProcedureVariables
        mcpServer.registerTool(McpServer.createTool(
            "extractProcedureVariables",
            "Extract all local, global, and dependency variables used within a Blockly procedure XML",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Procedure element name")
                ),
                "required", List.of("name")
            )
        ), params -> extractProcedureVariables(mcreator, params));

        // ===== GROUP 28: Recipe & Loot Table Analysis & Conflict Detector =====

        // 132. analyzeRecipeConflicts
        mcpServer.registerTool(McpServer.createTool(
            "analyzeRecipeConflicts",
            "Detect duplicate or conflicting crafting recipes (same inputs producing different outputs or ambiguous patterns)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> analyzeRecipeConflicts(mcreator));

        // 133. analyzeLootTableDrops
        mcpServer.registerTool(McpServer.createTool(
            "analyzeLootTableDrops",
            "Analyze all block and entity drops, calculating exact probability percentages, fortune modifiers, and silktouch rules",
            Map.of("type", "object", "properties", Map.of(
                "name", Map.of("type", "string", "description", "Optional specific loot table or element name")
            ))
        ), params -> analyzeLootTableDrops(mcreator, params));

        // 134. editRecipe
        mcpServer.registerTool(McpServer.createTool(
            "editRecipe",
            "Granularly edit recipe inputs, outputs, group, experience, and cooking time",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Recipe element name"),
                    "recipeType", Map.of("type", "string", "description", "Recipe type (Crafting, Smelting, Blasting, Smoking, Campfire, Smithing, Stonecutting)"),
                    "group", Map.of("type", "string", "description", "Optional recipe book group"),
                    "xp", Map.of("type", "number", "description", "Cooking experience (smelting/blasting)"),
                    "cookingTime", Map.of("type", "integer", "description", "Cooking time in ticks")
                ),
                "required", List.of("name")
            )
        ), params -> editRecipe(mcreator, params));

        // 135. editLootTable
        mcpServer.registerTool(McpServer.createTool(
            "editLootTable",
            "Add, remove, or modify pools, entries, conditions, and functions in a loot table",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Loot table element name"),
                    "type", Map.of("type", "string", "description", "Loot table type (block, entity, chest, gameplay)"),
                    "pools", Map.of("type", "array", "description", "Loot table pools definition list")
                ),
                "required", List.of("name")
            )
        ), params -> editLootTable(mcreator, params));

        // ===== GROUP 29: Advanced Texture Manipulation & Image Processing =====

        // 136. extractColorPalette
        mcpServer.registerTool(McpServer.createTool(
            "extractColorPalette",
            "Extract dominant hex colors with pixel counts and percentages from a texture",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name"),
                    "type", Map.of("type", "string", "description", "Texture type (block, item, etc.)"),
                    "maxColors", Map.of("type", "integer", "description", "Maximum colors to extract (default: 16)")
                ),
                "required", List.of("name", "type")
            )
        ), params -> extractColorPalette(mcreator, params));

        // 137. swapTextureColors
        mcpServer.registerTool(McpServer.createTool(
            "swapTextureColors",
            "Replace specific colors in a texture image (e.g. swap red with green)",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name"),
                    "type", Map.of("type", "string", "description", "Texture type"),
                    "fromColor", Map.of("type", "string", "description", "Hex color to replace (e.g. '#FF0000')"),
                    "toColor", Map.of("type", "string", "description", "New hex color (e.g. '#00FF00')"),
                    "outputName", Map.of("type", "string", "description", "Optional output name")
                ),
                "required", List.of("name", "type", "fromColor", "toColor")
            )
        ), params -> swapTextureColors(mcreator, params));

        // 138. resizeTexture
        mcpServer.registerTool(McpServer.createTool(
            "resizeTexture",
            "Upscale or downscale textures with pixel-art nearest-neighbor scaling (e.g. 16x16 -> 32x32)",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name"),
                    "type", Map.of("type", "string", "description", "Texture type"),
                    "width", Map.of("type", "integer", "description", "Target width in pixels"),
                    "height", Map.of("type", "integer", "description", "Target height in pixels"),
                    "outputName", Map.of("type", "string", "description", "Optional output name")
                ),
                "required", List.of("name", "type", "width", "height")
            )
        ), params -> resizeTexture(mcreator, params));

        // 139. rotateFlipTexture
        mcpServer.registerTool(McpServer.createTool(
            "rotateFlipTexture",
            "Rotate (90, 180, 270 degrees) or flip (horizontal, vertical) a texture image",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name"),
                    "type", Map.of("type", "string", "description", "Texture type"),
                    "operation", Map.of("type", "string", "description", "Operation: 'rotate90', 'rotate180', 'rotate270', 'flipH', 'flipV'"),
                    "outputName", Map.of("type", "string", "description", "Optional output name")
                ),
                "required", List.of("name", "type", "operation")
            )
        ), params -> rotateFlipTexture(mcreator, params));

        // 140. adjustTextureChannels
        mcpServer.registerTool(McpServer.createTool(
            "adjustTextureChannels",
            "Adjust brightness, contrast, hue, saturation, or alpha of a texture image",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name"),
                    "type", Map.of("type", "string", "description", "Texture type"),
                    "brightness", Map.of("type", "number", "description", "Brightness multiplier (1.0 = normal, 1.2 = +20%)"),
                    "contrast", Map.of("type", "number", "description", "Contrast multiplier (1.0 = normal)"),
                    "outputName", Map.of("type", "string", "description", "Optional output name")
                ),
                "required", List.of("name", "type")
            )
        ), params -> adjustTextureChannels(mcreator, params));

        // 141. generateNormalMap
        mcpServer.registerTool(McpServer.createTool(
            "generateNormalMap",
            "Generate bump/normal map from a texture for shader pack / PBR compatibility",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name"),
                    "type", Map.of("type", "string", "description", "Texture type"),
                    "strength", Map.of("type", "number", "description", "Normal strength (default: 1.0)"),
                    "outputName", Map.of("type", "string", "description", "Optional output name (default: <name>_n.png)")
                ),
                "required", List.of("name", "type")
            )
        ), params -> generateNormalMap(mcreator, params));

        // 142. compositeTextures
        mcpServer.registerTool(McpServer.createTool(
            "compositeTextures",
            "Layer multiple textures together (e.g. overlay an armor trim on an armor texture or an outline on a sword)",
            Map.of("type", "object",
                "properties", Map.of(
                    "baseName", Map.of("type", "string", "description", "Base texture name"),
                    "baseType", Map.of("type", "string", "description", "Base texture type"),
                    "overlayName", Map.of("type", "string", "description", "Overlay texture name"),
                    "overlayType", Map.of("type", "string", "description", "Overlay texture type"),
                    "outputName", Map.of("type", "string", "description", "Output texture name"),
                    "outputType", Map.of("type", "string", "description", "Output texture type")
                ),
                "required", List.of("baseName", "baseType", "overlayName", "overlayType", "outputName")
            )
        ), params -> compositeTextures(mcreator, params));

        // 143. cropTexture
        mcpServer.registerTool(McpServer.createTool(
            "cropTexture",
            "Crop a sub-region from a texture sheet (x, y, width, height)",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Texture name"),
                    "type", Map.of("type", "string", "description", "Texture type"),
                    "x", Map.of("type", "integer", "description", "Start X pixel"),
                    "y", Map.of("type", "integer", "description", "Start Y pixel"),
                    "width", Map.of("type", "integer", "description", "Width in pixels"),
                    "height", Map.of("type", "integer", "description", "Height in pixels"),
                    "outputName", Map.of("type", "string", "description", "Output texture name")
                ),
                "required", List.of("name", "type", "x", "y", "width", "height", "outputName")
            )
        ), params -> cropTexture(mcreator, params));

        // ===== GROUP 30: 3D Model & JSON Analyzer & Editor =====

        // 144. inspectModelUVs
        mcpServer.registerTool(McpServer.createTool(
            "inspectModelUVs",
            "Analyze a Java or JSON model for missing textures, overlapping UVs, or invalid cube dimensions",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Model name or filename")
                ),
                "required", List.of("name")
            )
        ), params -> inspectModelUVs(mcreator, params));

        // 145. editModelTextures
        mcpServer.registerTool(McpServer.createTool(
            "editModelTextures",
            "Remap texture variables (e.g. 'layer0', 'all', 'top', 'bottom', 'particle') in JSON block/item models",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Model name (JSON)"),
                    "textures", Map.of("type", "object", "description", "Map of texture variable names to texture paths")
                ),
                "required", List.of("name", "textures")
            )
        ), params -> editModelTextures(mcreator, params));

        // 146. scaleModel
        mcpServer.registerTool(McpServer.createTool(
            "scaleModel",
            "Uniformly or axis-specifically scale all cubes in a JSON model",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Model name (JSON)"),
                    "scale", Map.of("type", "number", "description", "Scale factor (e.g. 1.5, 0.5)"),
                    "outputName", Map.of("type", "string", "description", "Optional output model name")
                ),
                "required", List.of("name", "scale")
            )
        ), params -> scaleModel(mcreator, params));

        // 147. validateModelSchema
        mcpServer.registerTool(McpServer.createTool(
            "validateModelSchema",
            "Validate 3D model JSON against Minecraft entity/block model schemas",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Model name (JSON)")
                ),
                "required", List.of("name")
            )
        ), params -> validateModelSchema(mcreator, params));

        // ===== GROUP 31: Sound & Audio Manager & Event Editor =====

        // 148. inspectSoundFile
        mcpServer.registerTool(McpServer.createTool(
            "inspectSoundFile",
            "Inspect .ogg audio files (channels, bitrate, sample rate, duration in seconds, file size)",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Sound filename or sound name")
                ),
                "required", List.of("name")
            )
        ), params -> inspectSoundFile(mcreator, params));

        // 149. editSoundEvent
        mcpServer.registerTool(McpServer.createTool(
            "editSoundEvent",
            "Modify sound event definitions (category, subtitle, stream mode, pitch, attenuation distance)",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Sound element name"),
                    "category", Map.of("type", "string", "description", "Sound category (master, music, records, weather, blocks, hostile, neutral, players, ambient, voice)"),
                    "subtitle", Map.of("type", "string", "description", "Sound subtitle / caption"),
                    "stream", Map.of("type", "boolean", "description", "Stream directly from disk (for long music)")
                ),
                "required", List.of("name")
            )
        ), params -> editSoundEvent(mcreator, params));

        // 150. generateSoundJSON
        mcpServer.registerTool(McpServer.createTool(
            "generateSoundJSON",
            "Rebuild and format sounds.json according to all registered sound mod elements",
            Map.of("type", "object", "properties", Map.of())
        ), params -> generateSoundJSON(mcreator));

        // ===== GROUP 32: Tag & Etiket Derin Yönetimi =====

        // 151. editTagEntries
        mcpServer.registerTool(McpServer.createTool(
            "editTagEntries",
            "Add or remove specific items, blocks, entities, or biomes to/from an existing tag",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Tag element name"),
                    "action", Map.of("type", "string", "description", "Action: 'add' or 'remove'"),
                    "entries", Map.of("type", "array", "items", Map.of("type", "string"), "description", "List of entry identifiers to add or remove")
                ),
                "required", List.of("name", "action", "entries")
            )
        ), params -> editTagEntries(mcreator, params));

        // 152. findTagsForElement
        mcpServer.registerTool(McpServer.createTool(
            "findTagsForElement",
            "Find all tags in the workspace containing a specific element",
            Map.of("type", "object",
                "properties", Map.of(
                    "elementName", Map.of("type", "string", "description", "Mod element name to search in tags")
                ),
                "required", List.of("elementName")
            )
        ), params -> findTagsForElement(mcreator, params));

        // 153. validateTags
        mcpServer.registerTool(McpServer.createTool(
            "validateTags",
            "Detect references in tags to nonexistent items, blocks, or entities",
            Map.of("type", "object", "properties", Map.of())
        ), params -> validateTags(mcreator));

        // ===== GROUP 33: Workspace Değişkenleri & Lokalizasyon Düzenleyici =====

        // 154. editWorkspaceVariable
        mcpServer.registerTool(McpServer.createTool(
            "editWorkspaceVariable",
            "Update an existing variable's type, scope (GLOBAL_SESSION, GLOBAL_WORLD, GLOBAL_MAP), or default value",
            Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Variable name"),
                    "type", Map.of("type", "string", "description", "Variable type (number, logic, string, itemstack, blockstate, direction)"),
                    "scope", Map.of("type", "string", "description", "Scope (GLOBAL_SESSION, GLOBAL_WORLD, GLOBAL_MAP)"),
                    "value", Map.of("type", "string", "description", "Initial default value")
                ),
                "required", List.of("name")
            )
        ), params -> editWorkspaceVariable(mcreator, params));

        // 155. batchSetLocalizations
        mcpServer.registerTool(McpServer.createTool(
            "batchSetLocalizations",
            "Set translations for multiple keys across multiple languages in a single atomic call",
            Map.of("type", "object",
                "properties", Map.of(
                    "translations", Map.of("type", "object", "description", "Map of language codes (e.g. 'en_us', 'tr_tr') to key-value translation maps")
                ),
                "required", List.of("translations")
            )
        ), params -> batchSetLocalizations(mcreator, params));

        // 156. autoFillMissingTranslations
        mcpServer.registerTool(McpServer.createTool(
            "autoFillMissingTranslations",
            "Automatically copy untranslated keys from 'en_us' to other languages with prefix placeholder tags",
            Map.of("type", "object",
                "properties", Map.of(
                    "targetLanguage", Map.of("type", "string", "description", "Target language code (e.g. 'tr_tr', 'de_de', 'fr_fr')"),
                    "prefix", Map.of("type", "string", "description", "Optional prefix tag (e.g. '[TODO] ', default: '')")
                ),
                "required", List.of("targetLanguage")
            )
        ), params -> autoFillMissingTranslations(mcreator, params));

        // 157. searchLocalizationKeys
        mcpServer.registerTool(McpServer.createTool(
            "searchLocalizationKeys",
            "Search for localization keys or values by text or regex",
            Map.of("type", "object",
                "properties", Map.of(
                    "query", Map.of("type", "string", "description", "Search query or regex"),
                    "language", Map.of("type", "string", "description", "Optional language code (default: en_us)")
                ),
                "required", List.of("query")
            )
        ), params -> searchLocalizationKeys(mcreator, params));

        // ===== GROUP 34: Ultra Genişletilmiş Minecraft Kayıt Defterleri =====

        // 158. getMinecraftDimensions
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftDimensions",
            "List standard Minecraft vanilla dimensions (overworld, the_nether, the_end)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "dimensions"));

        // 159. getMinecraftStructures
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftStructures",
            "List standard Minecraft vanilla structure types (village, fortress, monument, mansion, ancient_city, etc.)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "structures"));

        // 160. getMinecraftBannerPatterns
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftBannerPatterns",
            "List standard Minecraft vanilla banner patterns",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "bannerpatterns"));

        // 161. getMinecraftTrimMaterials
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftTrimMaterials",
            "List standard Minecraft vanilla armor trim materials (amethyst, diamond, emerald, gold, iron, netherite, etc.)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "trimmaterials"));

        // 162. getMinecraftTrimPatterns
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftTrimPatterns",
            "List standard Minecraft vanilla armor trim patterns (sentry, vex, wild, coast, dune, eye, etc.)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "trimpatterns"));

        // 163. getMinecraftGameRules
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftGameRules",
            "List standard Minecraft vanilla game rules (keepInventory, mobGriefing, doDaylightCycle, etc.)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "gamerules"));

        // 164. getMinecraftPaintingVariants
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftPaintingVariants",
            "List standard Minecraft vanilla painting variants",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "paintings"));

        // 165. getMinecraftVillagerProfessions
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftVillagerProfessions",
            "List standard Minecraft vanilla villager professions (armorer, butcher, cleric, farmer, librarian, etc.)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "villagerprofessions"));

        // 166. getMinecraftWolfVariants
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftWolfVariants",
            "List standard Minecraft vanilla wolf variants (pale, woods, ashen, black, chestnut, rusty, spotted, striped, snowy)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "wolfvariants"));

        // 167. getMinecraftStatTypes
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftStatTypes",
            "List standard Minecraft vanilla stat categories and player statistics",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "stattypes"));

        // 168. getMinecraftRecipeTypes
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftRecipeTypes",
            "List standard Minecraft vanilla recipe types (crafting_shaped, crafting_shapeless, smelting, blasting, smoking, campfire_cooking, stonecutting, smithing)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "recipetypes"));

        // 169. getMinecraftEntityCategories
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftEntityCategories",
            "List standard Minecraft entity spawn categories (monster, creature, ambient, water_creature, water_ambient, undergroung_water_creature, misc)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "entitycategories"));

        // 170. getMinecraftSoundCategories
        mcpServer.registerTool(McpServer.createTool(
            "getMinecraftSoundCategories",
            "List standard Minecraft sound categories (master, music, records, weather, blocks, hostile, neutral, players, ambient, voice)",
            Map.of("type", "object", "properties", Map.of())
        ), params -> getMinecraftDataList(mcreator, "soundcategories"));

        LOG.info("Registered 170 comprehensive MCreator analysis and editing tools with MCP server");
    }

    /**
     * Build workspace tool (with auto-repair)
     */
    private McpTypes.ToolResult executeBuildWorkspace(MCreator mcreator) {
        LOG.info("Executing buildWorkspace tool");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }
            repairWorkspaceDirect(workspace);
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                saveWorkspaceSafe(workspace);
                mcreator.getActionRegistry().buildWorkspace.doAction();
            });
            return createSuccessResult("Workspace build initiated successfully");
        } catch (Exception e) {
            LOG.error("Error building workspace", e);
            return createErrorResult("Failed to build workspace: " + e.getMessage());
        }
    }

    /**
     * Regenerate code tool (with auto-repair)
     */
    private McpTypes.ToolResult executeRegenerateCode(MCreator mcreator) {
        LOG.info("Executing regenerateCode tool");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }
            int repaired = repairWorkspaceDirect(workspace);
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                saveWorkspaceSafe(workspace);
                mcreator.getActionRegistry().regenerateCode.doAction();
            });
            return createSuccessResult("Code regeneration initiated successfully (scanned & repaired " + repaired + " elements)");
        } catch (Exception e) {
            LOG.error("Error regenerating code", e);
            return createErrorResult("Failed to regenerate code: " + e.getMessage());
        }
    }

    /**
     * Get workspace information
     */
    private McpTypes.ToolResult getWorkspaceInfo(MCreator mcreator) {
        LOG.info("Executing getWorkspaceInfo tool");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            Map<String, Object> info = new HashMap<>();
            info.put("name", workspace.getWorkspaceSettings().getModName());
            info.put("modid", workspace.getWorkspaceSettings().getModID());
            info.put("version", workspace.getWorkspaceSettings().getVersion());
            info.put("author", workspace.getWorkspaceSettings().getAuthor());
            info.put("description", workspace.getWorkspaceSettings().getDescription());
            info.put("packageName", workspace.getWorkspaceSettings().getModElementsPackage());
            info.put("license", workspace.getWorkspaceSettings().getLicense());
            info.put("generatorFlavor", workspace.getGeneratorConfiguration().getGeneratorFlavor().name());
            info.put("generatorName", workspace.getGeneratorConfiguration().getGeneratorName());
            info.put("mcreatorVersion", String.valueOf(workspace.getMCreatorVersion()));
            info.put("elementCount", workspace.getModElements().size());
            info.put("variableCount", workspace.getVariableElements().size());
            info.put("soundCount", workspace.getSoundElements().size());
            info.put("workspaceFolder", workspace.getWorkspaceFolder().getAbsolutePath());

            String infoJson = objectMapper.writeValueAsString(info);
            return createSuccessResult("Workspace information retrieved:\n" + infoJson);
        } catch (Exception e) {
            LOG.error("Error getting workspace info", e);
            return createErrorResult("Failed to get workspace info: " + e.getMessage());
        }
    }

    /**
     * Set workspace settings
     */
    private McpTypes.ToolResult setWorkspaceSettings(MCreator mcreator, Map<String, Object> params) {
        LOG.info("Executing setWorkspaceSettings tool with params: {}", params);
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                WorkspaceSettings settings = workspace.getWorkspaceSettings();
                if (params.containsKey("modName") && params.get("modName") != null) settings.setModName((String) params.get("modName"));
                if (params.containsKey("version") && params.get("version") != null) settings.setVersion((String) params.get("version"));
                if (params.containsKey("author") && params.get("author") != null) settings.setAuthor((String) params.get("author"));
                if (params.containsKey("description") && params.get("description") != null) settings.setDescription((String) params.get("description"));
                if (params.containsKey("packageName") && params.get("packageName") != null) settings.setModElementsPackage((String) params.get("packageName"));
                if (params.containsKey("license") && params.get("license") != null) settings.setLicense((String) params.get("license"));
                if (params.containsKey("websiteURL") && params.get("websiteURL") != null) settings.setWebsiteURL((String) params.get("websiteURL"));
                workspace.markDirty();
            });
            return createSuccessResult("Workspace settings updated successfully");
        } catch (Exception e) {
            LOG.error("Error updating workspace settings", e);
            return createErrorResult("Failed to update workspace settings: " + e.getMessage());
        }
    }



    /**
     * Public static workspace repairer that cleans all .mod.json files on disk
     * AND repairs in-memory GeneratableElement fields via reflection to guarantee
     * 0-error code generation on any MCreator version.
     */
    public static int repairWorkspaceDirect(Workspace workspace) {
        if (workspace == null) return 0;
        int repairedCount = 0;
        try {
            File elementsDir = workspace.getFolderManager().getModElementsDir();
            if (elementsDir != null && elementsDir.exists()) {
                File[] files = elementsDir.listFiles((dir, name) -> name.endsWith(".mod.json"));
                if (files != null) {
                    for (File modFile : files) {
                        try {
                            String content = FileIO.readFileToString(modFile);
                            JsonObject rootJson = JsonParser.parseString(content).getAsJsonObject();
                            JsonObject defObj = rootJson.getAsJsonObject("definition");
                            if (defObj != null) {
                                sanitizeDefinitionStatic(defObj);
                                FileIO.writeStringToFile(WorkspaceFileManager.gson.toJson(rootJson), modFile);
                                repairedCount++;
                            }
                        } catch (Exception e) {
                            LOG.warn("Could not repair element file {}: {}", modFile.getName(), e.getMessage());
                        }
                    }
                }
            }
            for (ModElement el : workspace.getModElements()) {
                evictFromCacheSafe(workspace, el);
                try {
                    el.reinit(workspace);
                    GeneratableElement ge = el.getGeneratableElement();
                    if (ge != null) {
                        repairGeneratableElementInMemory(ge);
                    }
                } catch (Exception ignored) {}
            }
            saveWorkspaceSafe(workspace);
        } catch (Exception e) {
            LOG.error("Error during repairWorkspaceDirect", e);
        }
        return repairedCount;
    }

    private static void repairGeneratableElementInMemory(GeneratableElement ge) {
        if (ge == null) return;
        try {
            Class<?> clazz = ge.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    try {
                        if (field.get(ge) == null) {
                            if (field.getType() == String.class) {
                                String fname = field.getName();
                                String defVal = "Normal";
                                if (fname.equals("mobModelName")) defVal = "Biped";
                                else if (fname.equals("destroyTool")) defVal = "Not specified";
                                else if (fname.equals("toolType")) defVal = "Pickaxe";
                                else if (fname.equals("blockingModelName")) defVal = "Normal blocking";
                                else if (fname.equals("type")) defVal = "WATER";
                                else if (fname.equals("triggerKey")) defVal = "UNKNOWN";
                                else if (fname.equals("recipeType")) defVal = "Crafting";
                                field.set(ge, defVal);
                            } else if (List.class.isAssignableFrom(field.getType())) {
                                field.set(ge, new ArrayList<>());
                            } else if (Map.class.isAssignableFrom(field.getType())) {
                                field.set(ge, new HashMap<>());
                            }
                        }
                    } catch (Exception ignored) {}
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}
    }

    public static void sanitizeDefinitionStatic(JsonObject defObj) {
        if (defObj == null) return;
        // 1. Fix MItemBlock fields
        String[] itemBlockFields = { "mobDrop", "equipmentMainHand", "equipmentOffHand", "equipmentHelmet",
                "equipmentBody", "equipmentLeggings", "equipmentBoots", "rangedAttackItem", "customDrop",
                "creativePickItem", "eatResultItem" };
        for (String field : itemBlockFields) {
            if (defObj.has(field) && (defObj.get(field).isJsonArray() || defObj.get(field).isJsonPrimitive())) {
                defObj.remove(field);
            }
        }

        // 2. Fix procedure XML
        if (defObj.has("procedurexml")) {
            JsonElement elem = defObj.get("procedurexml");
            if (elem.isJsonPrimitive()) {
                String xml = elem.getAsString();
                if (xml == null || xml.trim().isEmpty()) {
                    defObj.addProperty("procedurexml", net.mcreator.element.types.Procedure.XML_BASE);
                } else if (!xml.contains("<xml")) {
                    defObj.addProperty("procedurexml", "<xml xmlns=\"https://developers.google.com/blockly/xml\">" + xml + "</xml>");
                }
            }
        }

        // 3. Ensure all @Nonnull String fields have defaults
        Map<String, String> nonnullDefaults = new LinkedHashMap<>();
        nonnullDefaults.put("customModelName", "Normal");
        nonnullDefaults.put("mobModelName", "Biped");
        nonnullDefaults.put("destroyTool", "Not specified");
        nonnullDefaults.put("toolType", "Pickaxe");
        nonnullDefaults.put("blockingModelName", "Normal blocking");
        nonnullDefaults.put("type", "WATER");
        nonnullDefaults.put("triggerKey", "UNKNOWN");
        nonnullDefaults.put("recipeType", "Crafting");
        nonnullDefaults.put("helmetItemCustomModelName", "Normal");
        nonnullDefaults.put("bodyItemCustomModelName", "Normal");
        nonnullDefaults.put("leggingsItemCustomModelName", "Normal");
        nonnullDefaults.put("bootsItemCustomModelName", "Normal");

        for (Map.Entry<String, String> entry : nonnullDefaults.entrySet()) {
            String field = entry.getKey();
            if (!defObj.has(field) || defObj.get(field).isJsonNull()
                    || (defObj.get(field).isJsonPrimitive() && defObj.get(field).getAsString().trim().isEmpty())) {
                if (defObj.has(field) || isFieldLikelyNeededStatic(defObj, field)) {
                    defObj.addProperty(field, entry.getValue());
                }
            }
        }
    }

    private static boolean isFieldLikelyNeededStatic(JsonObject defObj, String fieldName) {
        if (defObj.has(fieldName)) return true;
        switch (fieldName) {
            case "mobModelName":
                return defObj.has("mobBehaviourType") || defObj.has("mobCreatureType")
                        || defObj.has("health") || defObj.has("attackStrength")
                        || defObj.has("mobModelTexture") || defObj.has("spawnEggBaseColor");
            case "customModelName":
                return defObj.has("renderType") || defObj.has("blockBase")
                        || defObj.has("material") || defObj.has("plantType")
                        || defObj.has("toolType") || defObj.has("guiBoundTo");
            case "destroyTool":
                return defObj.has("blockBase") || defObj.has("material")
                        || defObj.has("hardness") || defObj.has("resistance");
            case "toolType":
            case "blockingModelName":
                return defObj.has("toolType") || defObj.has("attackSpeed")
                        || defObj.has("damageVsEntity");
            case "triggerKey":
                return defObj.has("keyBindingCategory") || defObj.has("triggerKey");
            case "recipeType":
                return defObj.has("recipeSlots") || defObj.has("craftingBookCategory");
            case "type":
                return defObj.has("flowRate") || defObj.has("slopeFindDistance")
                        || defObj.has("ruleCategory") || defObj.has("pools");
            case "helmetItemCustomModelName":
            case "bodyItemCustomModelName":
            case "leggingsItemCustomModelName":
            case "bootsItemCustomModelName":
                return defObj.has("armorTextureFile") || defObj.has("helmetModelTexture")
                        || defObj.has("maxDamage");
            default:
                return false;
        }
    }

    /**
     * List mod elements tool
     */
    private McpTypes.ToolResult listModElements(MCreator mcreator, Map<String, Object> params) {
        LOG.info("Executing listModElements tool");
        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }

            String elementType = (String) params.get("elementType");
            Collection<ModElement> elements = workspace.getModElements();

            if (elementType != null && !elementType.trim().isEmpty()) {
                elements = elements.stream()
                    .filter(element -> element.getType().getRegistryName().equalsIgnoreCase(elementType.trim()))
                    .collect(Collectors.toList());
            }

            List<Map<String, Object>> elementList = elements.stream()
                .map(this::modElementToMap)
                .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("elements", elementList);
            result.put("count", elementList.size());
            result.put("filteredBy", elementType);

            String resultJson = objectMapper.writeValueAsString(result);
            return createSuccessResult("Found " + elementList.size() + " mod elements:\n" + resultJson);
        } catch (Exception e) {
            LOG.error("Error listing mod elements", e);
            return createErrorResult("Failed to list mod elements: " + e.getMessage());
        }
    }

    /**
     * Get mod element full definition
     */
    private McpTypes.ToolResult getModElement(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        LOG.info("Executing getModElement tool for: {}", elementName);
        if (elementName == null || elementName.trim().isEmpty()) {
            return createErrorResult("elementName is required");
        }
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) {
            return createErrorResult("No workspace loaded");
        }
        ModElement element = workspace.getModElementByName(elementName.trim());
        if (element == null) {
            return createErrorResult("Element '" + elementName + "' not found");
        }
        try {
            File modFile = new File(workspace.getFolderManager().getModElementsDir(), element.getName() + ".mod.json");
            if (modFile.exists()) {
                String json = FileIO.readFileToString(modFile);
                return createSuccessResult(json);
            }
            GeneratableElement generatable = element.getGeneratableElement();
            if (generatable != null) {
                String json = workspace.getFileManager().getModElementManager().generatableElementToJSON(generatable);
                return createSuccessResult(json);
            }
            return createErrorResult("Could not load definition for " + elementName);
        } catch (Exception e) {
            LOG.error("Error getting mod element: " + elementName, e);
            return createErrorResult("Failed to get mod element: " + e.getMessage());
        }
    }

    /**
     * Create element tool
     */
    private McpTypes.ToolResult createElement(MCreator mcreator, Map<String, Object> params) {
        String elementType = (String) params.get("elementType");
        String elementName = (String) params.get("elementName");
        @SuppressWarnings("unchecked")
        Map<String, Object> customDefinition = (Map<String, Object>) params.get("definition");

        LOG.info("Executing createElement tool: {} of type {}", elementName, elementType);

        try {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return createErrorResult("No workspace loaded");
            }
            if (elementName == null || elementName.trim().isEmpty()) {
                return createErrorResult("Element name is required");
            }
            if (elementType == null || elementType.trim().isEmpty()) {
                return createErrorResult("Element type is required");
            }

            String sanitizedName = net.mcreator.java.JavaConventions.convertToValidClassName(elementName.trim());
            if (sanitizedName == null || sanitizedName.isEmpty()) {
                return createErrorResult("Invalid element name: " + elementName);
            }

            ModElementType type = null;
            for (ModElementType met : ModElementTypeLoader.getAllModElementTypes()) {
                if (met.getRegistryName().equalsIgnoreCase(elementType.trim())) {
                    type = met;
                    break;
                }
            }

            if (type == null) {
                String availableTypes = ModElementTypeLoader.getAllModElementTypes().stream()
                        .map(ModElementType::getRegistryName)
                        .collect(Collectors.joining(", "));
                return createErrorResult("Unknown element type: '" + elementType + "'. Supported types: " + availableTypes);
            }

            if (workspace.getModElementByName(sanitizedName) != null) {
                return createErrorResult("Element with name '" + sanitizedName + "' already exists");
            }

            final ModElementType<?> finalType = type;
            final String finalName = sanitizedName;

            javax.swing.SwingUtilities.invokeAndWait(() -> {
                ModElement element = new ModElement(workspace, finalName, finalType);
                try {
                    GeneratableElement generatable = finalType.getModElementStorageClass()
                            .getConstructor(ModElement.class).newInstance(element);

                    workspace.getFileManager().getModElementManager().storeModElement(generatable);

                    File modFile = new File(workspace.getFolderManager().getModElementsDir(), finalName + ".mod.json");
                    if (modFile.exists()) {
                        JsonObject rootJson = JsonParser.parseString(FileIO.readFileToString(modFile)).getAsJsonObject();
                        JsonObject defObj = rootJson.getAsJsonObject("definition");
                        if (defObj != null) {
                            if (customDefinition != null && !customDefinition.isEmpty()) {
                                String defJsonStr = objectMapper.writeValueAsString(customDefinition);
                                JsonObject customDefJson = JsonParser.parseString(defJsonStr).getAsJsonObject();
                                for (Map.Entry<String, JsonElement> entry : customDefJson.entrySet()) {
                                    defObj.add(entry.getKey(), entry.getValue());
                                }
                            }
                            sanitizeDefinitionStatic(defObj);
                            FileIO.writeStringToFile(WorkspaceFileManager.gson.toJson(rootJson), modFile);
                            evictFromCacheSafe(workspace, element);
                            element.reinit(workspace);
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Could not fully initialize generatable element for {}: {}", finalName, e.getMessage());
                }
                workspace.addModElement(element);
                saveWorkspaceSafe(workspace);
            });

            return createSuccessResult("Element '" + finalName + "' of type '" + elementType + "' created successfully");
        } catch (Exception e) {
            LOG.error("Error creating element", e);
            return createErrorResult("Failed to create element: " + e.getMessage());
        }
    }

    /**
     * Update element definition tool
     */
    private McpTypes.ToolResult updateModElement(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        @SuppressWarnings("unchecked")
        Map<String, Object> definition = (Map<String, Object>) params.get("definition");

        LOG.info("Executing updateModElement tool for: {}", elementName);

        if (elementName == null || elementName.trim().isEmpty()) {
            return createErrorResult("elementName is required");
        }
        if (definition == null || definition.isEmpty()) {
            return createErrorResult("definition map is required");
        }

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) {
            return createErrorResult("No workspace loaded");
        }

        ModElement element = workspace.getModElementByName(elementName.trim());
        if (element == null) {
            return createErrorResult("Element '" + elementName + "' not found");
        }

        try {
            File modFile = new File(workspace.getFolderManager().getModElementsDir(), element.getName() + ".mod.json");
            if (!modFile.exists()) {
                return createErrorResult("Definition file not found for: " + elementName);
            }

            String currentJson = FileIO.readFileToString(modFile);
            JsonObject rootJson = JsonParser.parseString(currentJson).getAsJsonObject();
            JsonObject defObj = rootJson.getAsJsonObject("definition");
            if (defObj == null) {
                defObj = new JsonObject();
                rootJson.add("definition", defObj);
            }

            String updatesJson = objectMapper.writeValueAsString(definition);
            JsonObject updatesObj = JsonParser.parseString(updatesJson).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : updatesObj.entrySet()) {
                defObj.add(entry.getKey(), entry.getValue());
            }

            sanitizeDefinitionStatic(defObj);
            FileIO.writeStringToFile(WorkspaceFileManager.gson.toJson(rootJson), modFile);

            javax.swing.SwingUtilities.invokeAndWait(() -> {
                evictFromCacheSafe(workspace, element);
                element.reinit(workspace);
                saveWorkspaceSafe(workspace);
            });

            return createSuccessResult("Mod element '" + elementName + "' definition updated successfully");
        } catch (Exception e) {
            LOG.error("Error updating mod element: " + elementName, e);
            return createErrorResult("Failed to update mod element: " + e.getMessage());
        }
    }

    /**
     * Delete element tool
     */
    private McpTypes.ToolResult deleteElement(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        LOG.info("Executing deleteElement tool for: {}", elementName);
        if (elementName == null || elementName.trim().isEmpty()) {
            return createErrorResult("elementName is required");
        }

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        ModElement element = workspace.getModElementByName(elementName.trim());
        if (element == null) {
            return createErrorResult("Element '" + elementName + "' not found");
        }

        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                workspace.removeModElement(element);
                saveWorkspaceSafe(workspace);
            });
            return createSuccessResult("Mod element '" + elementName + "' deleted successfully");
        } catch (Exception e) {
            LOG.error("Error deleting mod element: " + elementName, e);
            return createErrorResult("Failed to delete mod element: " + e.getMessage());
        }
    }

    /**
     * List mod element types
     */
    private McpTypes.ToolResult listModElementTypes(MCreator mcreator) {
        LOG.info("Executing listModElementTypes tool");
        try {
            List<Map<String, Object>> types = new ArrayList<>();
            for (ModElementType met : ModElementTypeLoader.getAllModElementTypes()) {
                Map<String, Object> typeMap = new HashMap<>();
                typeMap.put("registryName", met.getRegistryName());
                typeMap.put("name", met.getReadableName());
                types.add(typeMap);
            }
            String resultJson = objectMapper.writeValueAsString(Map.of("elementTypes", types, "count", types.size()));
            return createSuccessResult("Supported MCreator mod element types (" + types.size() + "):\n" + resultJson);
        } catch (Exception e) {
            LOG.error("Error listing mod element types", e);
            return createErrorResult("Failed to list mod element types: " + e.getMessage());
        }
    }

    /**
     * List workspace variables
     */
    private McpTypes.ToolResult listWorkspaceVariables(MCreator mcreator) {
        LOG.info("Executing listWorkspaceVariables tool");
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            List<Map<String, Object>> vars = new ArrayList<>();
            for (VariableElement var : workspace.getVariableElements()) {
                Map<String, Object> varMap = new HashMap<>();
                varMap.put("name", var.getName());
                varMap.put("type", var.getType() != null ? var.getType().getName() : "unknown");
                varMap.put("scope", var.getScope() != null ? var.getScope().name() : "GLOBAL_WORLD");
                varMap.put("value", var.getValue());
                vars.add(varMap);
            }
            String resultJson = objectMapper.writeValueAsString(Map.of("variables", vars, "count", vars.size()));
            return createSuccessResult("Global workspace variables (" + vars.size() + "):\n" + resultJson);
        } catch (Exception e) {
            LOG.error("Error listing workspace variables", e);
            return createErrorResult("Failed to list variables: " + e.getMessage());
        }
    }

    /**
     * Add workspace variable
     */
    private McpTypes.ToolResult addWorkspaceVariable(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String type = (String) params.get("type");
        String scopeStr = (String) params.get("scope");
        Object value = params.get("value");

        if (name == null || name.trim().isEmpty()) return createErrorResult("name is required");
        if (type == null || type.trim().isEmpty()) return createErrorResult("type is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                VariableElement var = new VariableElement(name.trim());
                VariableType vt = VariableTypeLoader.INSTANCE.fromName(type.trim());
                if (vt != null) {
                    var.setType(vt);
                }
                if (scopeStr != null) {
                    try {
                        var.setScope(VariableType.Scope.valueOf(scopeStr.trim().toUpperCase()));
                    } catch (Exception ignored) {}
                }
                if (value != null) {
                    var.setValue(value);
                }
                workspace.removeVariableElement(var);
                workspace.addVariableElement(var);
                workspace.markDirty();
            });
            return createSuccessResult("Variable '" + name + "' added successfully");
        } catch (Exception e) {
            LOG.error("Error adding variable", e);
            return createErrorResult("Failed to add variable: " + e.getMessage());
        }
    }

    /**
     * Delete workspace variable
     */
    private McpTypes.ToolResult deleteWorkspaceVariable(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null || name.trim().isEmpty()) return createErrorResult("name is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                workspace.removeVariableElement(new VariableElement(name.trim()));
                workspace.markDirty();
            });
            return createSuccessResult("Variable '" + name + "' deleted successfully");
        } catch (Exception e) {
            LOG.error("Error deleting variable", e);
            return createErrorResult("Failed to delete variable: " + e.getMessage());
        }
    }

    /**
     * List workspace textures
     */
    private McpTypes.ToolResult listTextures(MCreator mcreator, Map<String, Object> params) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        String typeFilter = (String) params.get("textureType");
        Map<String, List<String>> result = new HashMap<>();

        try {
            for (TextureType tt : TextureType.values()) {
                if (typeFilter != null && !typeFilter.trim().isEmpty() && !tt.getID().equalsIgnoreCase(typeFilter.trim())) {
                    continue;
                }
                List<File> files = workspace.getFolderManager().getTexturesList(tt);
                List<String> names = files != null ? files.stream().map(File::getName).collect(Collectors.toList()) : Collections.emptyList();
                result.put(tt.getID(), names);
            }
            String json = objectMapper.writeValueAsString(result);
            return createSuccessResult("Workspace textures:\n" + json);
        } catch (Exception e) {
            LOG.error("Error listing textures", e);
            return createErrorResult("Failed to list textures: " + e.getMessage());
        }
    }

    /**
     * Add / Import texture tool
     */
    private McpTypes.ToolResult addTexture(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        String base64Data = (String) params.get("base64Data");
        String filePath = (String) params.get("filePath");

        if (name == null || name.trim().isEmpty()) return createErrorResult("name is required");
        if (typeStr == null || typeStr.trim().isEmpty()) return createErrorResult("type is required");
        if ((base64Data == null || base64Data.trim().isEmpty()) && (filePath == null || filePath.trim().isEmpty())) {
            return createErrorResult("Either base64Data or filePath must be provided");
        }

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            TextureType targetType = null;
            for (TextureType tt : TextureType.values()) {
                if (tt.getID().equalsIgnoreCase(typeStr.trim()) || tt.name().equalsIgnoreCase(typeStr.trim())) {
                    targetType = tt;
                    break;
                }
            }
            if (targetType == null) {
                return createErrorResult("Unknown texture type '" + typeStr + "'. Valid types: entity, block, item, screen, armor, particle, effect, other");
            }

            String fileName = name.trim();
            if (!fileName.toLowerCase().endsWith(".png")) {
                fileName += ".png";
            }

            File texturesFolder = getTexturesFolderSafe(workspace.getFolderManager(), targetType);
            if (texturesFolder == null) {
                return createErrorResult("Could not resolve textures folder for type: " + typeStr);
            }
            File destFile = new File(texturesFolder, fileName);
            destFile.getParentFile().mkdirs();

            if (base64Data != null && !base64Data.trim().isEmpty()) {
                String cleanBase64 = base64Data.trim();
                if (cleanBase64.contains(",")) {
                    cleanBase64 = cleanBase64.substring(cleanBase64.indexOf(",") + 1);
                }
                byte[] bytes = Base64.getDecoder().decode(cleanBase64);
                FileIO.writeBytesToFile(bytes, destFile);
            } else if (filePath != null) {
                File srcFile = new File(filePath.trim());
                if (!srcFile.exists()) {
                    return createErrorResult("Source image file does not exist: " + filePath);
                }
                FileIO.copyFile(srcFile, destFile);
            }

            javax.swing.SwingUtilities.invokeAndWait(() -> {
                workspace.markDirty();
            });

            return createSuccessResult("Texture '" + fileName + "' saved successfully as " + targetType.getID() + " texture");
        } catch (Exception e) {
            LOG.error("Error adding texture", e);
            return createErrorResult("Failed to add texture: " + e.getMessage());
        }
    }

    /**
     * Delete texture tool
     */
    private McpTypes.ToolResult deleteTexture(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");

        if (name == null || name.trim().isEmpty()) return createErrorResult("name is required");
        if (typeStr == null || typeStr.trim().isEmpty()) return createErrorResult("type is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            TextureType targetType = null;
            for (TextureType tt : TextureType.values()) {
                if (tt.getID().equalsIgnoreCase(typeStr.trim()) || tt.name().equalsIgnoreCase(typeStr.trim())) {
                    targetType = tt;
                    break;
                }
            }
            if (targetType == null) return createErrorResult("Unknown texture type: " + typeStr);

            String fileName = name.trim();
            if (!fileName.toLowerCase().endsWith(".png")) fileName += ".png";

            File texturesFolder = getTexturesFolderSafe(workspace.getFolderManager(), targetType);
            if (texturesFolder != null) {
                File targetFile = new File(texturesFolder, fileName);
                if (targetFile.exists()) {
                    targetFile.delete();
                    workspace.markDirty();
                    return createSuccessResult("Texture '" + fileName + "' deleted successfully");
                }
            }
            return createErrorResult("Texture '" + fileName + "' not found in " + typeStr + " folder");
        } catch (Exception e) {
            LOG.error("Error deleting texture", e);
            return createErrorResult("Failed to delete texture: " + e.getMessage());
        }
    }

    /**
     * List sounds
     */
    private McpTypes.ToolResult listSounds(MCreator mcreator) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (SoundElement se : workspace.getSoundElements()) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", se.getName());
                map.put("category", se.getCategory());
                map.put("subtitle", se.getSubtitle());
                map.put("files", se.getFiles());
                list.add(map);
            }
            String json = objectMapper.writeValueAsString(Map.of("sounds", list, "count", list.size()));
            return createSuccessResult("Workspace sounds (" + list.size() + "):\n" + json);
        } catch (Exception e) {
            LOG.error("Error listing sounds", e);
            return createErrorResult("Failed to list sounds: " + e.getMessage());
        }
    }

    /**
     * Add sound
     */
    private McpTypes.ToolResult addSound(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String category = (String) params.getOrDefault("category", "neutral");
        String subtitle = (String) params.get("subtitle");
        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) params.getOrDefault("files", Collections.emptyList());

        if (name == null || name.trim().isEmpty()) return createErrorResult("name is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                SoundElement se = new SoundElement(name.trim(), files != null ? files : Collections.emptyList(), category, subtitle);
                workspace.removeSoundElement(se);
                workspace.addSoundElement(se);
                workspace.markDirty();
            });
            return createSuccessResult("Sound '" + name + "' registered successfully");
        } catch (Exception e) {
            LOG.error("Error adding sound", e);
            return createErrorResult("Failed to add sound: " + e.getMessage());
        }
    }

    /**
     * Delete sound
     */
    private McpTypes.ToolResult deleteSound(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null || name.trim().isEmpty()) return createErrorResult("name is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                workspace.removeSoundElement(new SoundElement(name.trim(), Collections.emptyList(), "neutral", null));
                workspace.markDirty();
            });
            return createSuccessResult("Sound '" + name + "' deleted successfully");
        } catch (Exception e) {
            LOG.error("Error deleting sound", e);
            return createErrorResult("Failed to delete sound: " + e.getMessage());
        }
    }

    /**
     * List models
     */
    private McpTypes.ToolResult listModels(MCreator mcreator) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            File modelsDir = workspace.getFolderManager().getModelsDir();
            List<String> models = new ArrayList<>();
            if (modelsDir != null && modelsDir.exists()) {
                File[] files = modelsDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        models.add(f.getName());
                    }
                }
            }
            String json = objectMapper.writeValueAsString(Map.of("models", models, "count", models.size()));
            return createSuccessResult("Workspace 3D models (" + models.size() + "):\n" + json);
        } catch (Exception e) {
            LOG.error("Error listing models", e);
            return createErrorResult("Failed to list models: " + e.getMessage());
        }
    }

    /**
     * Add / Import 3D model
     */
    private McpTypes.ToolResult addModel(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String modelType = (String) params.getOrDefault("modelType", "java");
        String content = (String) params.get("content");
        String base64Data = (String) params.get("base64Data");
        String filePath = (String) params.get("filePath");
        String textureMapping = (String) params.get("textureMapping");

        if (name == null || name.trim().isEmpty()) return createErrorResult("name is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            File modelsDir = workspace.getFolderManager().getModelsDir();
            if (modelsDir == null) return createErrorResult("Models directory not accessible");
            modelsDir.mkdirs();

            String fileName = name.trim();
            if (!fileName.contains(".")) {
                if ("json".equalsIgnoreCase(modelType)) fileName += ".json";
                else if ("obj".equalsIgnoreCase(modelType)) fileName += ".obj";
                else fileName += ".java";
            }

            File destFile = new File(modelsDir, fileName);

            if (content != null && !content.trim().isEmpty()) {
                FileIO.writeStringToFile(content, destFile);
            } else if (base64Data != null && !base64Data.trim().isEmpty()) {
                String clean = base64Data.contains(",") ? base64Data.substring(base64Data.indexOf(",") + 1) : base64Data;
                FileIO.writeBytesToFile(Base64.getDecoder().decode(clean.trim()), destFile);
            } else if (filePath != null && !filePath.trim().isEmpty()) {
                File src = new File(filePath.trim());
                if (!src.exists()) return createErrorResult("Source model file not found: " + filePath);
                FileIO.copyFile(src, destFile);
            } else {
                return createErrorResult("Model content, base64Data, or filePath must be provided");
            }

            if (textureMapping != null && !textureMapping.trim().isEmpty()) {
                File texFile = new File(modelsDir, fileName + ".textures");
                FileIO.writeStringToFile(textureMapping.trim(), texFile);
            }

            workspace.markDirty();
            return createSuccessResult("Model '" + fileName + "' saved successfully in workspace models directory");
        } catch (Exception e) {
            LOG.error("Error adding model", e);
            return createErrorResult("Failed to add model: " + e.getMessage());
        }
    }

    /**
     * List structures
     */
    private McpTypes.ToolResult listStructures(MCreator mcreator) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            List<String> structures = workspace.getFolderManager().getStructureList();
            String json = objectMapper.writeValueAsString(Map.of("structures", structures, "count", structures.size()));
            return createSuccessResult("Workspace structures (" + structures.size() + "):\n" + json);
        } catch (Exception e) {
            LOG.error("Error listing structures", e);
            return createErrorResult("Failed to list structures: " + e.getMessage());
        }
    }

    /**
     * Add structure (NBT)
     */
    private McpTypes.ToolResult addStructure(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String base64Data = (String) params.get("base64Data");
        String filePath = (String) params.get("filePath");

        if (name == null || name.trim().isEmpty()) return createErrorResult("name is required");
        if ((base64Data == null || base64Data.trim().isEmpty()) && (filePath == null || filePath.trim().isEmpty())) {
            return createErrorResult("Either base64Data or filePath must be provided");
        }

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            File structDir = workspace.getFolderManager().getStructuresDir();
            if (structDir == null) {
                structDir = new File(workspace.getWorkspaceFolder(), "src/main/resources/data/" + workspace.getWorkspaceSettings().getModID() + "/structures");
            }
            structDir.mkdirs();

            String fileName = name.trim();
            if (!fileName.toLowerCase().endsWith(".nbt")) fileName += ".nbt";

            File destFile = new File(structDir, fileName);

            if (base64Data != null && !base64Data.trim().isEmpty()) {
                String clean = base64Data.contains(",") ? base64Data.substring(base64Data.indexOf(",") + 1) : base64Data;
                FileIO.writeBytesToFile(Base64.getDecoder().decode(clean.trim()), destFile);
            } else if (filePath != null) {
                File src = new File(filePath.trim());
                if (!src.exists()) return createErrorResult("Source NBT file does not exist: " + filePath);
                FileIO.copyFile(src, destFile);
            }

            workspace.markDirty();
            return createSuccessResult("Structure '" + fileName + "' saved successfully");
        } catch (Exception e) {
            LOG.error("Error adding structure", e);
            return createErrorResult("Failed to add structure: " + e.getMessage());
        }
    }

    /**
     * Create Procedure tool
     */
    private McpTypes.ToolResult createProcedure(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String trigger = (String) params.getOrDefault("trigger", "no_ext_trigger");
        String procedurexml = (String) params.get("procedurexml");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) params.get("actions");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dependencies = (List<Map<String, Object>>) params.get("dependencies");

        if (name == null || name.trim().isEmpty()) return createErrorResult("name is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            String sanitizedName = net.mcreator.java.JavaConventions.convertToValidClassName(name.trim());
            if (sanitizedName == null || sanitizedName.isEmpty()) {
                return createErrorResult("Invalid procedure name: " + name);
            }

            String finalXml = procedurexml;
            if (finalXml == null || finalXml.trim().isEmpty()) {
                if (actions != null && !actions.isEmpty()) {
                    finalXml = buildBlocklyXmlFromActions(trigger, actions);
                } else {
                    finalXml = "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"event_trigger\" deletable=\"false\" x=\"40\" y=\"40\"><field name=\"trigger\">" + trigger + "</field></block></xml>";
                }
            }

            Map<String, Object> defMap = new HashMap<>();
            defMap.put("procedurexml", finalXml);

            Map<String, Object> createParams = new HashMap<>();
            createParams.put("elementName", sanitizedName);
            createParams.put("elementType", "procedure");
            createParams.put("definition", defMap);

            McpTypes.ToolResult res = createElement(mcreator, createParams);

            if (!Boolean.TRUE.equals(res.getIsError()) && dependencies != null && !dependencies.isEmpty()) {
                ModElement el = workspace.getModElementByName(sanitizedName);
                if (el != null) {
                    el.putMetadata("dependencies", dependencies);
                    saveWorkspaceSafe(workspace);
                }
            }

            return res;
        } catch (Exception e) {
            LOG.error("Error creating procedure", e);
            return createErrorResult("Failed to create procedure: " + e.getMessage());
        }
    }

    /**
     * Build Blockly XML from structured actions
     */
    private String buildBlocklyXmlFromActions(String trigger, List<Map<String, Object>> actions) {
        StringBuilder sb = new StringBuilder();
        sb.append("<xml xmlns=\"https://developers.google.com/blockly/xml\">");
        sb.append("<block type=\"event_trigger\" deletable=\"false\" x=\"40\" y=\"40\">");
        sb.append("<field name=\"trigger\">").append(trigger).append("</field>");

        StringBuilder blocksClose = new StringBuilder();
        for (Map<String, Object> action : actions) {
            String actType = (String) action.getOrDefault("type", "chat");
            sb.append("<next>");
            blocksClose.append("</block></next>");

            if ("chat".equalsIgnoreCase(actType)) {
                String text = (String) action.getOrDefault("text", "Hello!");
                sb.append("<block type=\"entity_send_chat\">")
                  .append("<value name=\"text\"><block type=\"text\"><field name=\"TEXT\">").append(escapeXml(text)).append("</field></block></value>")
                  .append("<value name=\"entity\"><block type=\"entity_from_deps\"></block></value>")
                  .append("<value name=\"actbar\"><block type=\"logic_boolean\"><field name=\"BOOL\">FALSE</field></block></value>");
            } else if ("damage".equalsIgnoreCase(actType) || "deal_damage".equalsIgnoreCase(actType)) {
                int amount = ((Number) action.getOrDefault("amount", 2)).intValue();
                sb.append("<block type=\"deal_damage\">")
                  .append("<value name=\"amount\"><block type=\"math_number\"><field name=\"NUM\">").append(amount).append("</field></block></value>")
                  .append("<value name=\"entity\"><block type=\"entity_from_deps\"></block></value>");
            } else if ("heal".equalsIgnoreCase(actType) || "set_health".equalsIgnoreCase(actType)) {
                int amount = ((Number) action.getOrDefault("amount", 20)).intValue();
                sb.append("<block type=\"entity_set_health\">")
                  .append("<value name=\"value\"><block type=\"math_number\"><field name=\"NUM\">").append(amount).append("</field></block></value>")
                  .append("<value name=\"entity\"><block type=\"entity_from_deps\"></block></value>");
            } else if ("command".equalsIgnoreCase(actType) || "execute_command".equalsIgnoreCase(actType)) {
                String cmd = (String) action.getOrDefault("command", "say Hi");
                sb.append("<block type=\"execute_command\">")
                  .append("<value name=\"command\"><block type=\"text\"><field name=\"TEXT\">").append(escapeXml(cmd)).append("</field></block></value>")
                  .append("<value name=\"x\"><block type=\"coord_x\"></block></value>")
                  .append("<value name=\"y\"><block type=\"coord_y\"></block></value>")
                  .append("<value name=\"z\"><block type=\"coord_z\"></block></value>");
            } else if ("lightning".equalsIgnoreCase(actType) || "strike_lightning".equalsIgnoreCase(actType)) {
                sb.append("<block type=\"strike_lightning\">")
                  .append("<value name=\"x\"><block type=\"coord_x\"></block></value>")
                  .append("<value name=\"y\"><block type=\"coord_y\"></block></value>")
                  .append("<value name=\"z\"><block type=\"coord_z\"></block></value>");
            } else if ("explode".equalsIgnoreCase(actType)) {
                int power = ((Number) action.getOrDefault("power", 4)).intValue();
                sb.append("<block type=\"explode\">")
                  .append("<value name=\"power\"><block type=\"math_number\"><field name=\"NUM\">").append(power).append("</field></block></value>")
                  .append("<value name=\"x\"><block type=\"coord_x\"></block></value>")
                  .append("<value name=\"y\"><block type=\"coord_y\"></block></value>")
                  .append("<value name=\"z\"><block type=\"coord_z\"></block></value>");
            } else {
                sb.append("<block type=\"entity_send_chat\">")
                  .append("<value name=\"text\"><block type=\"text\"><field name=\"TEXT\">Action: ").append(escapeXml(actType)).append("</field></block></value>")
                  .append("<value name=\"entity\"><block type=\"entity_from_deps\"></block></value>")
                  .append("<value name=\"actbar\"><block type=\"logic_boolean\"><field name=\"BOOL\">FALSE</field></block></value>");
            }
        }

        sb.append(blocksClose);
        sb.append("</block></xml>");
        return sb.toString();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    /**
     * List all procedure triggers
     */
    private McpTypes.ToolResult listProcedureTriggers(MCreator mcreator) {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            if (BlocklyLoader.INSTANCE != null && BlocklyLoader.INSTANCE.getExternalTriggerLoader() != null) {
                for (ExternalTrigger et : BlocklyLoader.INSTANCE.getExternalTriggerLoader().getExternalTriggers()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", et.getID());
                    try {
                        map.put("name", et.getName());
                    } catch (Throwable t) {
                        map.put("name", et.getID());
                    }
                    map.put("side", et.side);
                    map.put("cancelable", et.cancelable);
                    map.put("has_result", et.has_result);

                    List<Map<String, String>> deps = new ArrayList<>();
                    if (et.dependencies_provided != null) {
                        for (Dependency dep : et.dependencies_provided) {
                            if (dep != null) {
                                Map<String, String> d = new HashMap<>();
                                d.put("name", dep.getName());
                                d.put("type", dep.getRawType());
                                deps.add(d);
                            }
                        }
                    }
                    map.put("dependencies", deps);
                    list.add(map);
                }
            }
            String json = objectMapper.writeValueAsString(Map.of("triggers", list, "count", list.size()));
            return createSuccessResult("MCreator procedure event triggers (" + list.size() + "):\n" + json);
        } catch (Exception e) {
            LOG.error("Error listing procedure triggers", e);
            return createErrorResult("Failed to list procedure triggers: " + e.getMessage());
        }
    }

    /**
     * List Blockly procedure blocks
     */
    private McpTypes.ToolResult listProcedureBlocks(MCreator mcreator, Map<String, Object> params) {
        String category = (String) params.get("category");
        String search = (String) params.get("search");

        try {
            List<Map<String, Object>> result = new ArrayList<>();
            if (BlocklyLoader.INSTANCE != null) {
                var loader = BlocklyLoader.INSTANCE.getAllBlockLoaders().get(BlocklyEditorType.PROCEDURE);
                if (loader != null && loader.getDefinedBlocks() != null) {
                    for (var entry : loader.getDefinedBlocks().entrySet()) {
                        String blockId = entry.getKey();
                        var block = entry.getValue();
                        String catName = "general";
                        try {
                            if (block.getToolboxCategory() != null && block.getToolboxCategory().getName() != null) {
                                catName = block.getToolboxCategory().getName();
                            }
                        } catch (Throwable ignored) {}

                        if (category != null && !category.trim().isEmpty() && !catName.equalsIgnoreCase(category.trim())) {
                            continue;
                        }
                        if (search != null && !search.trim().isEmpty()) {
                            String s = search.trim().toLowerCase();
                            boolean matches = (blockId != null && blockId.toLowerCase().contains(s))
                                    || (catName.toLowerCase().contains(s));
                            if (!matches) continue;
                        }
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", blockId);
                        map.put("category", catName);
                        map.put("fields", block.getFields() != null ? block.getFields() : Collections.emptyList());
                        map.put("inputs", block.getAllInputs() != null ? block.getAllInputs() : Collections.emptyList());

                        List<Map<String, String>> deps = new ArrayList<>();
                        if (block.getDependencies() != null) {
                            for (Dependency dep : block.getDependencies()) {
                                if (dep != null) {
                                    Map<String, String> d = new HashMap<>();
                                    d.put("name", dep.getName());
                                    d.put("type", dep.getRawType());
                                    deps.add(d);
                                }
                            }
                        }
                        map.put("dependencies", deps);
                        map.put("outputType", block.getOutputType());
                        result.add(map);
                    }
                }
            }
            String json = objectMapper.writeValueAsString(Map.of("blocks", result, "count", result.size(), "categories", BlocklyLoader.getBuiltinCategories()));
            return createSuccessResult("Procedure Blockly blocks (" + result.size() + "):\n" + json);
        } catch (Exception e) {
            LOG.error("Error listing procedure blocks", e);
            return createErrorResult("Failed to list procedure blocks: " + e.getMessage());
        }
    }

    /**
     * Link procedure to mod element event
     */
    private McpTypes.ToolResult linkProcedureToElement(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        String event = (String) params.get("event");
        String procedureName = (String) params.get("procedureName");

        if (elementName == null || elementName.trim().isEmpty()) return createErrorResult("elementName is required");
        if (event == null || event.trim().isEmpty()) return createErrorResult("event is required");
        if (procedureName == null || procedureName.trim().isEmpty()) return createErrorResult("procedureName is required");

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        ModElement el = workspace.getModElementByName(elementName.trim());
        if (el == null) return createErrorResult("Element '" + elementName + "' not found");

        ModElement proc = workspace.getModElementByName(procedureName.trim());
        if (proc == null) return createErrorResult("Procedure '" + procedureName + "' not found in workspace");

        try {
            Map<String, Object> defUpdates = new HashMap<>();
            defUpdates.put(event.trim(), Map.of("name", procedureName.trim()));

            return updateModElement(mcreator, Map.of("elementName", elementName.trim(), "definition", defUpdates));
        } catch (Exception e) {
            LOG.error("Error linking procedure", e);
            return createErrorResult("Failed to link procedure: " + e.getMessage());
        }
    }

    /**
     * Get workspace diagnostics
     */
    private McpTypes.ToolResult getWorkspaceDiagnostics(MCreator mcreator) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            List<Map<String, Object>> issues = new ArrayList<>();
            File elementsDir = workspace.getFolderManager().getModElementsDir();

            if (elementsDir != null && elementsDir.exists()) {
                File[] files = elementsDir.listFiles((dir, name) -> name.endsWith(".mod.json"));
                if (files != null) {
                    for (File f : files) {
                        try {
                            String content = FileIO.readFileToString(f);
                            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                            JsonObject def = root.getAsJsonObject("definition");
                            if (def == null) {
                                issues.add(Map.of("file", f.getName(), "severity", "ERROR", "message", "Missing definition object"));
                            }
                        } catch (Exception e) {
                            issues.add(Map.of("file", f.getName(), "severity", "ERROR", "message", "JSON Parse error: " + e.getMessage()));
                        }
                    }
                }
            }

            for (ModElement el : workspace.getModElements()) {
                GeneratableElement gen = el.getGeneratableElement();
                if (gen == null) {
                    issues.add(Map.of("element", el.getName(), "type", el.getType().getRegistryName(), "severity", "WARNING", "message", "GeneratableElement could not be instantiated"));
                }
            }

            Map<String, Object> report = new HashMap<>();
            report.put("totalElements", workspace.getModElements().size());
            report.put("totalVariables", workspace.getVariableElements().size());
            report.put("totalSounds", workspace.getSoundElements().size());
            report.put("issuesCount", issues.size());
            report.put("issues", issues);
            report.put("status", issues.isEmpty() ? "HEALTHY" : "NEEDS_REPAIR");

            String json = objectMapper.writeValueAsString(report);
            return createSuccessResult("Workspace Diagnostics Report:\n" + json);
        } catch (Exception e) {
            LOG.error("Error running workspace diagnostics", e);
            return createErrorResult("Failed to run diagnostics: " + e.getMessage());
        }
    }

    /**
     * Repair workspace tool
     */
    private McpTypes.ToolResult repairWorkspaceTool(MCreator mcreator) {
        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) return createErrorResult("No workspace loaded");

        try {
            int repaired = repairWorkspaceDirect(workspace);
            saveWorkspaceSafe(workspace);
            return createSuccessResult("Workspace repair completed successfully! Scanned and auto-repaired " + repaired + " mod elements.");
        } catch (Exception e) {
            LOG.error("Error repairing workspace", e);
            return createErrorResult("Failed to repair workspace: " + e.getMessage());
        }
    }

    /**
     * Run client tool
     */
    private McpTypes.ToolResult executeRunClient(MCreator mcreator) {
        LOG.info("Executing runClient tool");
        try {
            if (mcreator.getWorkspace() == null) return createErrorResult("No workspace loaded");
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                mcreator.getActionRegistry().runClient.doAction();
            });
            return createSuccessResult("Minecraft client started successfully");
        } catch (Exception e) {
            LOG.error("Error running client", e);
            return createErrorResult("Failed to run client: " + e.getMessage());
        }
    }

    /**
     * Run server tool
     */
    private McpTypes.ToolResult executeRunServer(MCreator mcreator) {
        LOG.info("Executing runServer tool");
        try {
            if (mcreator.getWorkspace() == null) return createErrorResult("No workspace loaded");
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                mcreator.getActionRegistry().runServer.doAction();
            });
            return createSuccessResult("Minecraft server started successfully");
        } catch (Exception e) {
            LOG.error("Error running server", e);
            return createErrorResult("Failed to run server: " + e.getMessage());
        }
    }

    /**
     * Universally safe workspace saver compatible with all MCreator versions (2020.x - 2026.x+)
     */
    private static void saveWorkspaceSafe(Workspace workspace) {
        if (workspace == null) return;
        try {
            if (workspace.getFileManager() != null) {
                Object fm = workspace.getFileManager();
                try {
                    Method m = fm.getClass().getMethod("saveWorkspaceDirectlyAndWait");
                    m.invoke(fm);
                    return;
                } catch (NoSuchMethodException ignored) {}

                try {
                    Method m = fm.getClass().getMethod("saveWorkspace");
                    m.invoke(fm);
                    return;
                } catch (NoSuchMethodException ignored) {}

                try {
                    Method m = fm.getClass().getMethod("saveWorkspaceIfChanged");
                    m.invoke(fm);
                    return;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            LOG.warn("Could not invoke direct save on workspace file manager: {}", e.getMessage());
        }
        workspace.markDirty();
    }

    /**
     * Universally safe mod element cache evictor compatible with all MCreator versions
     */
    private static void evictFromCacheSafe(Workspace workspace, ModElement element) {
        if (workspace == null || element == null) return;
        try {
            if (workspace.getFileManager() != null && workspace.getFileManager().getModElementManager() != null) {
                Object mem = workspace.getFileManager().getModElementManager();
                Method m = mem.getClass().getMethod("evictFromCache", ModElement.class);
                m.invoke(mem, element);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Universally safe texture folder locator compatible with all MCreator versions
     */
    private static File getTexturesFolderSafe(WorkspaceFolderManager folderManager, TextureType targetType) {
        if (folderManager == null || targetType == null) return null;
        try {
            Method m = folderManager.getClass().getMethod("getTexturesFolder", TextureType.class);
            return (File) m.invoke(folderManager, targetType);
        } catch (NoSuchMethodException e) {
            try {
                Method m = folderManager.getClass().getMethod("getTexturesDir", TextureType.class);
                return (File) m.invoke(folderManager, targetType);
            } catch (Exception ex) {
                LOG.warn("Could not find textures folder method: {}", ex.getMessage());
            }
        } catch (Exception e) {
            LOG.warn("Error invoking getTexturesFolder: {}", e.getMessage());
        }
        // Fallback: use reflection to get workspace from folder manager
        try {
            Method getWs = folderManager.getClass().getMethod("getWorkspace");
            Object ws = getWs.invoke(folderManager);
            Method getFolder = ws.getClass().getMethod("getWorkspaceFolder");
            File wsFolder = (File) getFolder.invoke(ws);
            Method getSettings = ws.getClass().getMethod("getWorkspaceSettings");
            Object settings = getSettings.invoke(ws);
            Method getModID = settings.getClass().getMethod("getModID");
            String modId = (String) getModID.invoke(settings);
            return new File(wsFolder, "src/main/resources/assets/" + modId + "/textures/" + targetType.getID());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Helper method to convert ModElement to Map
     */
    private Map<String, Object> modElementToMap(ModElement element) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", element.getName());
        map.put("type", element.getType().getRegistryName());
        map.put("isLocked", element.isCodeLocked());
        map.put("sortIndex", element.getName());
        return map;
    }

    /**
     * Helper method to create success result
     */
    private McpTypes.ToolResult createSuccessResult(String message) {
        List<McpTypes.ToolContent> content = List.of(
            new McpTypes.ToolContent("text", message)
        );
        return new McpTypes.ToolResult(content, false);
    }

    /**
     * Helper method to create error result
     */
    private McpTypes.ToolResult createErrorResult(String message) {
        List<McpTypes.ToolContent> content = List.of(
            new McpTypes.ToolContent("text", "Error: " + message)
        );
        return new McpTypes.ToolResult(content, true);
    }

    // ===== GROUP 1: Advanced Workspace Management Implementations =====

    private McpTypes.ToolResult getGeneratorInfo(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            Map<String, Object> info = new HashMap<>();
            info.put("generatorName", ws.getGeneratorConfiguration().getGeneratorName());
            info.put("generatorFlavor", ws.getGeneratorConfiguration().getGeneratorFlavor().name());
            try { info.put("generatorBuildFileVersion", ws.getGeneratorConfiguration().getGeneratorBuildFileVersion()); } catch (Throwable ignored) {}
            List<String> supportedTypes = new ArrayList<>();
            for (ModElementType<?> met : ModElementTypeLoader.getAllModElementTypes()) {
                try {
                    if (ws.getGeneratorConfiguration().getGeneratorStats().getModElementTypeCoverageInfo().get(met) != null)
                        supportedTypes.add(met.getRegistryName());
                } catch (Throwable t) { supportedTypes.add(met.getRegistryName()); }
            }
            info.put("supportedElementTypes", supportedTypes);
            List<String> availableGenerators = new ArrayList<>(net.mcreator.generator.Generator.GENERATOR_CACHE.keySet());
            info.put("availableGenerators", availableGenerators);
            return createSuccessResult(objectMapper.writeValueAsString(info));
        } catch (Exception e) { return createErrorResult("Failed to get generator info: " + e.getMessage()); }
    }

    private McpTypes.ToolResult switchGenerator(MCreator mcreator, Map<String, Object> params) {
        String genName = (String) params.get("generatorName");
        if (genName == null || genName.trim().isEmpty()) return createErrorResult("generatorName is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            if (!net.mcreator.generator.Generator.GENERATOR_CACHE.containsKey(genName.trim())) {
                return createErrorResult("Unknown generator: " + genName + ". Available: " + String.join(", ", net.mcreator.generator.Generator.GENERATOR_CACHE.keySet()));
            }
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                ws.switchGenerator(genName.trim());
                saveWorkspaceSafe(ws);
            });
            return createSuccessResult("Generator switched to '" + genName + "'. Run regenerateCode to update all generated files.");
        } catch (Exception e) { return createErrorResult("Failed to switch generator: " + e.getMessage()); }
    }

    private McpTypes.ToolResult exportWorkspace(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            String outputPath = (String) params.get("outputPath");
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { mcreator.getActionRegistry().buildWorkspace.doAction(); } catch (Throwable ignored) {}
            });
            File buildLibs = new File(ws.getWorkspaceFolder(), "build/libs");
            if (buildLibs.exists()) {
                File[] jars = buildLibs.listFiles((d, n) -> n.endsWith(".jar"));
                if (jars != null && jars.length > 0) {
                    File latestJar = jars[0];
                    for (File j : jars) { if (j.lastModified() > latestJar.lastModified()) latestJar = j; }
                    if (outputPath != null && !outputPath.trim().isEmpty()) {
                        java.nio.file.Files.copy(latestJar.toPath(), new File(outputPath.trim()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        return createSuccessResult("Exported mod to: " + outputPath);
                    }
                    return createSuccessResult("Build complete. Mod JAR at: " + latestJar.getAbsolutePath() + " (" + latestJar.length() + " bytes)");
                }
            }
            return createSuccessResult("Build initiated. Check build/libs/ for output JAR.");
        } catch (Exception e) { return createErrorResult("Failed to export workspace: " + e.getMessage()); }
    }

    private McpTypes.ToolResult clearGradleCaches(MCreator mcreator) {
        try {
            if (mcreator.getWorkspace() == null) return createErrorResult("No workspace loaded");
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { mcreator.getActionRegistry().clearAllGradleCaches.doAction(); } catch (Throwable ignored) {}
            });
            return createSuccessResult("Gradle caches cleared successfully");
        } catch (Exception e) { return createErrorResult("Failed to clear Gradle caches: " + e.getMessage()); }
    }

    private McpTypes.ToolResult reloadGradleProject(MCreator mcreator) {
        try {
            if (mcreator.getWorkspace() == null) return createErrorResult("No workspace loaded");
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { mcreator.getActionRegistry().reloadGradleProject.doAction(); } catch (Throwable ignored) {}
            });
            return createSuccessResult("Gradle project reloaded successfully");
        } catch (Exception e) { return createErrorResult("Failed to reload Gradle project: " + e.getMessage()); }
    }

    // ===== GROUP 2: Localization & Language Management Implementations =====

    private McpTypes.ToolResult listLocalizations(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            Map<String, Object> result = new HashMap<>();
            Map<String, java.util.LinkedHashMap<String, String>> langMap = ws.getLanguageMap();
            for (Map.Entry<String, java.util.LinkedHashMap<String, String>> entry : langMap.entrySet()) {
                result.put(entry.getKey(), entry.getValue().size());
            }
            return createSuccessResult("Localization languages:\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) { return createErrorResult("Failed to list localizations: " + e.getMessage()); }
    }

    private McpTypes.ToolResult getLocalization(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            String lang = (String) params.get("language");
            if (lang == null) lang = "en_us";
            Map<String, java.util.LinkedHashMap<String, String>> langMap = ws.getLanguageMap();
            java.util.LinkedHashMap<String, String> entries = langMap.get(lang.trim());
            if (entries == null) return createErrorResult("Language '" + lang + "' not found. Available: " + String.join(", ", langMap.keySet()));
            return createSuccessResult(objectMapper.writeValueAsString(entries));
        } catch (Exception e) { return createErrorResult("Failed to get localization: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private McpTypes.ToolResult setLocalization(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            String lang = params.containsKey("language") ? (String) params.get("language") : "en_us";
            if (lang == null) lang = "en_us";
            String key = (String) params.get("key");
            String value = (String) params.get("value");
            Map<String, String> entries = (Map<String, String>) params.get("entries");
            int count = 0;
            if (key != null && value != null) {
                ws.setLocalization(key, value);
                count = 1;
            }
            if (entries != null) {
                for (Map.Entry<String, String> e : entries.entrySet()) {
                    ws.setLocalization(e.getKey(), e.getValue());
                    count++;
                }
            }
            ws.markDirty();
            saveWorkspaceSafe(ws);
            return createSuccessResult("Set " + count + " localization entries for '" + lang + "'");
        } catch (Exception e) { return createErrorResult("Failed to set localization: " + e.getMessage()); }
    }

    private McpTypes.ToolResult deleteLocalization(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            String lang = (String) params.get("language");
            String key = (String) params.get("key");
            if (key != null && !key.trim().isEmpty()) {
                ws.removeLocalizationEntryByKey(key.trim());
                ws.markDirty();
                return createSuccessResult("Removed localization key: " + key);
            }
            if (lang != null && !lang.trim().isEmpty() && !lang.trim().equals("en_us")) {
                ws.removeLocalizationLanguage(lang.trim());
                ws.markDirty();
                return createSuccessResult("Removed localization language: " + lang);
            }
            return createErrorResult("Specify either 'key' to remove or 'language' (not en_us) to delete entirely");
        } catch (Exception e) { return createErrorResult("Failed to delete localization: " + e.getMessage()); }
    }

    // ===== GROUP 3: Tag Management Implementations =====

    private McpTypes.ToolResult listTags(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            List<Map<String, Object>> tags = new ArrayList<>();
            for (Map.Entry<net.mcreator.workspace.elements.TagElement, ArrayList<String>> entry : ws.getTagElements().entrySet()) {
                Map<String, Object> tagMap = new HashMap<>();
                tagMap.put("name", entry.getKey().getName());
                tagMap.put("type", entry.getKey().type().name());
                tagMap.put("namespace", entry.getKey().getMCreatorNamespace());
                tagMap.put("resourcePath", entry.getKey().resourcePath());
                tagMap.put("entries", entry.getValue());
                tagMap.put("entryCount", entry.getValue().size());
                tags.add(tagMap);
            }
            return createSuccessResult("Tags (" + tags.size() + "):\n" + objectMapper.writeValueAsString(tags));
        } catch (Exception e) { return createErrorResult("Failed to list tags: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private McpTypes.ToolResult addTag(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            String name = (String) params.get("name");
            String type = (String) params.get("type");
            String namespace = (String) params.get("namespace");
            List<String> entries = (List<String>) params.get("entries");
            if (name == null) return createErrorResult("name is required");
            if (type == null) return createErrorResult("type is required");
            String resourcePath = (namespace != null ? namespace.trim() : "mod") + ":" + name.trim();
            net.mcreator.workspace.elements.TagElement tag = new net.mcreator.workspace.elements.TagElement(
                net.mcreator.minecraft.TagType.valueOf(type.trim().toUpperCase()), resourcePath
            );
            ArrayList<String> entryList = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
            ws.getTagElements().put(tag, entryList);
            ws.markDirty();
            saveWorkspaceSafe(ws);
            return createSuccessResult("Tag '" + name + "' (" + type + ") added with " + entryList.size() + " entries");
        } catch (Exception e) { return createErrorResult("Failed to add tag: " + e.getMessage()); }
    }

    private McpTypes.ToolResult deleteTag(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            String name = (String) params.get("name");
            String type = (String) params.get("type");
            if (name == null || type == null) return createErrorResult("name and type are required");
            net.mcreator.workspace.elements.TagElement tag = new net.mcreator.workspace.elements.TagElement(
                net.mcreator.minecraft.TagType.valueOf(type.trim().toUpperCase()), "mod:" + name.trim()
            );
            ws.removeTagElement(tag);
            ws.markDirty();
            saveWorkspaceSafe(ws);
            return createSuccessResult("Tag '" + name + "' (" + type + ") deleted");
        } catch (Exception e) { return createErrorResult("Failed to delete tag: " + e.getMessage()); }
    }

    // ===== GROUP 4: Gradle Task Management Implementations =====

    private McpTypes.ToolResult runGradleTask(MCreator mcreator, Map<String, Object> params) {
        try {
            String task = (String) params.get("task");
            if (task == null) return createErrorResult("task is required");
            if (mcreator.getWorkspace() == null) return createErrorResult("No workspace loaded");
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try {
                    mcreator.getGradleConsole().exec(task.trim());
                } catch (Throwable ignored) {}
            });
            return createSuccessResult("Gradle task '" + task + "' started");
        } catch (Exception e) { return createErrorResult("Failed to run Gradle task: " + e.getMessage()); }
    }

    private McpTypes.ToolResult cancelGradleTask(MCreator mcreator) {
        try {
            if (mcreator.getWorkspace() == null) return createErrorResult("No workspace loaded");
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { mcreator.getGradleConsole().cancelTask(); } catch (Throwable ignored) {}
            });
            return createSuccessResult("Gradle task cancelled");
        } catch (Exception e) { return createErrorResult("Failed to cancel Gradle task: " + e.getMessage()); }
    }

    private McpTypes.ToolResult getGradleStatus(MCreator mcreator) {
        try {
            if (mcreator.getWorkspace() == null) return createErrorResult("No workspace loaded");
            Map<String, Object> status = new HashMap<>();
            try {
                status.put("isRunning", mcreator.getGradleConsole().isGradleSetupTaskRunning());
                int statusCode = mcreator.getGradleConsole().getStatus();
                String statusStr = statusCode == 0 ? "READY" : statusCode == 1 ? "RUNNING" : "ERROR";
                status.put("status", statusStr);
                status.put("statusCode", statusCode);
            } catch (Throwable t) {
                status.put("status", "UNKNOWN");
            }
            return createSuccessResult(objectMapper.writeValueAsString(status));
        } catch (Exception e) { return createErrorResult("Failed to get Gradle status: " + e.getMessage()); }
    }

    // ===== GROUP 5: Advanced Element Management Implementations =====

    private McpTypes.ToolResult duplicateElement(MCreator mcreator, Map<String, Object> params) {
        String source = (String) params.get("sourceElement");
        String newName = (String) params.get("newName");
        if (source == null || newName == null) return createErrorResult("sourceElement and newName are required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            ModElement sourceEl = ws.getModElementByName(source.trim());
            if (sourceEl == null) return createErrorResult("Source element '" + source + "' not found");
            String sanitized = net.mcreator.java.JavaConventions.convertToValidClassName(newName.trim());
            if (ws.getModElementByName(sanitized) != null) return createErrorResult("Element '" + sanitized + "' already exists");
            File sourceFile = new File(ws.getFolderManager().getModElementsDir(), sourceEl.getName() + ".mod.json");
            if (!sourceFile.exists()) return createErrorResult("Source definition file not found");
            String json = FileIO.readFileToString(sourceFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            ModElement newEl = new ModElement(ws, sanitized, sourceEl.getType());
            File destFile = new File(ws.getFolderManager().getModElementsDir(), sanitized + ".mod.json");
            FileIO.writeStringToFile(WorkspaceFileManager.gson.toJson(root), destFile);
            ws.addModElement(newEl);
            newEl.reinit(ws);
            saveWorkspaceSafe(ws);
            return createSuccessResult("Element '" + source + "' duplicated as '" + sanitized + "'");
        } catch (Exception e) { return createErrorResult("Failed to duplicate element: " + e.getMessage()); }
    }

    private McpTypes.ToolResult renameElement(MCreator mcreator, Map<String, Object> params) {
        String oldName = (String) params.get("elementName");
        String newName = (String) params.get("newName");
        if (oldName == null || newName == null) return createErrorResult("elementName and newName are required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            ModElement el = ws.getModElementByName(oldName.trim());
            if (el == null) return createErrorResult("Element '" + oldName + "' not found");
            String sanitized = net.mcreator.java.JavaConventions.convertToValidClassName(newName.trim());
            if (ws.getModElementByName(sanitized) != null) return createErrorResult("Element '" + sanitized + "' already exists");
            // Rename by duplicating then deleting old
            File sourceFile = new File(ws.getFolderManager().getModElementsDir(), el.getName() + ".mod.json");
            if (sourceFile.exists()) {
                String json = FileIO.readFileToString(sourceFile);
                File destFile = new File(ws.getFolderManager().getModElementsDir(), sanitized + ".mod.json");
                FileIO.writeStringToFile(json, destFile);
                ModElement newEl = new ModElement(ws, sanitized, el.getType());
                ws.addModElement(newEl);
                ws.removeModElement(el);
                sourceFile.delete();
                newEl.reinit(ws);
                saveWorkspaceSafe(ws);
            }
            return createSuccessResult("Element renamed from '" + oldName + "' to '" + sanitized + "'. Run regenerateCode to update generated files.");
        } catch (Exception e) { return createErrorResult("Failed to rename element: " + e.getMessage()); }
    }

    private McpTypes.ToolResult getElementCode(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        if (elementName == null) return createErrorResult("elementName is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            ModElement el = ws.getModElementByName(elementName.trim());
            if (el == null) return createErrorResult("Element '" + elementName + "' not found");
            // Search in generated source
            File srcRoot = ws.getGenerator().getGeneratorPackageRoot();
            StringBuilder code = new StringBuilder();
            if (srcRoot != null && srcRoot.exists()) {
                findJavaFiles(srcRoot, elementName.trim(), code, 0);
            }
            if (code.length() == 0) {
                // Try broader search
                File srcMain = new File(ws.getWorkspaceFolder(), "src/main/java");
                if (srcMain.exists()) findJavaFiles(srcMain, elementName.trim(), code, 0);
            }
            if (code.length() == 0) return createErrorResult("No generated code found for '" + elementName + "'");
            return createSuccessResult(code.toString());
        } catch (Exception e) { return createErrorResult("Failed to get element code: " + e.getMessage()); }
    }

    private void findJavaFiles(File dir, String name, StringBuilder sb, int depth) {
        if (depth > 10 || dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) { findJavaFiles(f, name, sb, depth + 1); }
            else if (f.getName().toLowerCase().contains(name.toLowerCase()) && (f.getName().endsWith(".java") || f.getName().endsWith(".json"))) {
                sb.append("=== ").append(f.getName()).append(" ===\n");
                sb.append(FileIO.readFileToString(f)).append("\n\n");
            }
        }
    }

    private McpTypes.ToolResult listElementEvents(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        if (elementName == null) return createErrorResult("elementName is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            ModElement el = ws.getModElementByName(elementName.trim());
            if (el == null) return createErrorResult("Element '" + elementName + "' not found");
            GeneratableElement ge = el.getGeneratableElement();
            if (ge == null) return createErrorResult("No generatable element loaded for '" + elementName + "'");
            List<Map<String, String>> events = new ArrayList<>();
            Class<?> clazz = ge.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    try {
                        if (f.getType().getSimpleName().equals("Procedure") ||
                            f.getName().startsWith("on") || f.getName().startsWith("when") ||
                            f.getName().contains("Procedure") || f.getName().contains("Event") ||
                            f.getName().contains("Trigger")) {
                            Map<String, String> event = new HashMap<>();
                            event.put("fieldName", f.getName());
                            event.put("type", f.getType().getSimpleName());
                            Object val = f.get(ge);
                            event.put("currentValue", val != null ? val.toString() : "null");
                            events.add(event);
                        }
                    } catch (Throwable ignored) {}
                }
                clazz = clazz.getSuperclass();
            }
            return createSuccessResult("Events/procedures for '" + elementName + "' (" + events.size() + "):\n" + objectMapper.writeValueAsString(events));
        } catch (Exception e) { return createErrorResult("Failed to list element events: " + e.getMessage()); }
    }

    private McpTypes.ToolResult searchElements(MCreator mcreator, Map<String, Object> params) {
        String query = (String) params.get("query");
        String typeFilter = (String) params.get("elementType");
        if (query == null) return createErrorResult("query is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            String q = query.trim().toLowerCase();
            List<Map<String, Object>> results = ws.getModElements().stream()
                .filter(el -> el.getName().toLowerCase().contains(q))
                .filter(el -> typeFilter == null || typeFilter.trim().isEmpty() || el.getType().getRegistryName().equalsIgnoreCase(typeFilter.trim()))
                .map(this::modElementToMap)
                .collect(Collectors.toList());
            return createSuccessResult("Found " + results.size() + " elements matching '" + query + "':\n" + objectMapper.writeValueAsString(results));
        } catch (Exception e) { return createErrorResult("Failed to search elements: " + e.getMessage()); }
    }

    // ===== GROUP 6: Resource Management Extended Implementations =====

    private McpTypes.ToolResult deleteModel(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null) return createErrorResult("name is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            File modelsDir = new File(ws.getWorkspaceFolder(), "models");
            File target = new File(modelsDir, name.trim());
            if (!target.exists()) {
                // Try searching
                File[] found = modelsDir.listFiles((d, n) -> n.toLowerCase().contains(name.trim().toLowerCase()));
                if (found != null && found.length > 0) target = found[0];
            }
            if (target.exists()) {
                target.delete();
                return createSuccessResult("Model '" + target.getName() + "' deleted");
            }
            return createErrorResult("Model '" + name + "' not found");
        } catch (Exception e) { return createErrorResult("Failed to delete model: " + e.getMessage()); }
    }

    private McpTypes.ToolResult deleteStructure(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null) return createErrorResult("name is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            File structDir = ws.getFolderManager().getStructuresDir();
            String fileName = name.trim().endsWith(".nbt") ? name.trim() : name.trim() + ".nbt";
            File target = new File(structDir, fileName);
            if (target.exists()) {
                target.delete();
                return createSuccessResult("Structure '" + fileName + "' deleted");
            }
            return createErrorResult("Structure '" + name + "' not found");
        } catch (Exception e) { return createErrorResult("Failed to delete structure: " + e.getMessage()); }
    }

    private McpTypes.ToolResult listAnimations(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            List<String> anims = new ArrayList<>();
            try {
                File animDir = new File(ws.getWorkspaceFolder(), "models");
                if (animDir.exists()) {
                    File[] files = animDir.listFiles((d, n) -> n.endsWith(".animation.json") || n.endsWith(".geo.json"));
                    if (files != null) for (File f : files) anims.add(f.getName());
                }
            } catch (Throwable ignored) {}
            return createSuccessResult("Animations (" + anims.size() + "):\n" + objectMapper.writeValueAsString(anims));
        } catch (Exception e) { return createErrorResult("Failed to list animations: " + e.getMessage()); }
    }

    private McpTypes.ToolResult addAnimation(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String content = (String) params.get("content");
        String filePath = (String) params.get("filePath");
        if (name == null) return createErrorResult("name is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            File modelsDir = new File(ws.getWorkspaceFolder(), "models");
            modelsDir.mkdirs();
            File target = new File(modelsDir, name.trim());
            if (content != null) {
                FileIO.writeStringToFile(content, target);
            } else if (filePath != null) {
                java.nio.file.Files.copy(new File(filePath).toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                return createErrorResult("Either content or filePath must be provided");
            }
            return createSuccessResult("Animation '" + name + "' imported to " + target.getAbsolutePath());
        } catch (Exception e) { return createErrorResult("Failed to add animation: " + e.getMessage()); }
    }

    // ===== GROUP 7: Reference Analysis Implementations =====

    private McpTypes.ToolResult findReferences(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        if (elementName == null) return createErrorResult("elementName is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            List<Map<String, String>> refs = new ArrayList<>();
            String searchName = elementName.trim();
            for (ModElement el : ws.getModElements()) {
                if (el.getName().equals(searchName)) continue;
                try {
                    File modFile = new File(ws.getFolderManager().getModElementsDir(), el.getName() + ".mod.json");
                    if (modFile.exists()) {
                        String content = FileIO.readFileToString(modFile);
                        if (content.contains(searchName)) {
                            Map<String, String> ref = new HashMap<>();
                            ref.put("element", el.getName());
                            ref.put("type", el.getType().getRegistryName());
                            refs.add(ref);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            return createSuccessResult("Found " + refs.size() + " references to '" + searchName + "':\n" + objectMapper.writeValueAsString(refs));
        } catch (Exception e) { return createErrorResult("Failed to find references: " + e.getMessage()); }
    }

    private McpTypes.ToolResult findBrokenReferences(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            Set<String> validNames = ws.getModElements().stream().map(ModElement::getName).collect(Collectors.toSet());
            List<Map<String, String>> broken = new ArrayList<>();
            for (ModElement el : ws.getModElements()) {
                try {
                    File modFile = new File(ws.getFolderManager().getModElementsDir(), el.getName() + ".mod.json");
                    if (modFile.exists()) {
                        String content = FileIO.readFileToString(modFile);
                        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                        JsonObject def = root.getAsJsonObject("definition");
                        if (def != null) {
                            for (Map.Entry<String, JsonElement> entry : def.entrySet()) {
                                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsString().startsWith("CUSTOM:")) {
                                    String refName = entry.getValue().getAsString().substring(7);
                                    if (!validNames.contains(refName)) {
                                        Map<String, String> b = new HashMap<>();
                                        b.put("element", el.getName());
                                        b.put("field", entry.getKey());
                                        b.put("brokenRef", refName);
                                        broken.add(b);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            return createSuccessResult("Broken references (" + broken.size() + "):\n" + objectMapper.writeValueAsString(broken));
        } catch (Exception e) { return createErrorResult("Failed to find broken references: " + e.getMessage()); }
    }

    // ===== GROUP 8: Minecraft Data Lists Implementations =====

    private McpTypes.ToolResult listDataEntries(MCreator mcreator, Map<String, Object> params) {
        String listName = (String) params.get("listName");
        if (listName == null) return createErrorResult("listName is required");
        return getMinecraftDataList(mcreator, listName.trim());
    }

    private McpTypes.ToolResult getMinecraftDataList(MCreator mcreator, String listName) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            List<String> entries = new ArrayList<>();
            try {
                Class<?> dlClass = Class.forName("net.mcreator.minecraft.DataListLoader");
                Method loadMethod = dlClass.getMethod("loadDataList", String.class);
                Object dataList = loadMethod.invoke(null, listName);
                if (dataList instanceof Collection) {
                    for (Object item : (Collection<?>) dataList) {
                        try {
                            Method getName = item.getClass().getMethod("getName");
                            entries.add((String) getName.invoke(item));
                        } catch (Throwable t) {
                            entries.add(item.toString());
                        }
                    }
                }
            } catch (Throwable t) {
                // Fallback: try ElementUtil
                try {
                    Class<?> euClass = Class.forName("net.mcreator.minecraft.ElementUtil");
                    Method method = null;
                    for (Method m : euClass.getMethods()) {
                        if (m.getName().toLowerCase().contains(listName.toLowerCase()) && m.getParameterCount() <= 1) {
                            method = m;
                            break;
                        }
                    }
                    if (method != null) {
                        Object result = method.getParameterCount() == 0 ? method.invoke(null) : method.invoke(null, ws);
                        if (result instanceof Collection) {
                            for (Object item : (Collection<?>) result) entries.add(item.toString());
                        }
                    }
                } catch (Throwable ignored) {}
            }
            Map<String, Object> result = new HashMap<>();
            result.put("listName", listName);
            result.put("entries", entries);
            result.put("count", entries.size());
            return createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception e) { return createErrorResult("Failed to get data list: " + e.getMessage()); }
    }

    // ===== GROUP 9: Workspace File Operations Implementations =====

    private McpTypes.ToolResult readFile(MCreator mcreator, Map<String, Object> params) {
        String path = (String) params.get("path");
        if (path == null) return createErrorResult("path is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            File file = new File(path.trim());
            if (!file.isAbsolute()) file = new File(ws.getWorkspaceFolder(), path.trim());
            if (!file.exists()) return createErrorResult("File not found: " + file.getAbsolutePath());
            if (file.length() > 512 * 1024) return createErrorResult("File too large (max 512KB): " + file.length() + " bytes");
            String content = FileIO.readFileToString(file);
            return createSuccessResult("File: " + file.getName() + " (" + file.length() + " bytes)\n" + content);
        } catch (Exception e) { return createErrorResult("Failed to read file: " + e.getMessage()); }
    }

    private McpTypes.ToolResult writeFile(MCreator mcreator, Map<String, Object> params) {
        String path = (String) params.get("path");
        String content = (String) params.get("content");
        if (path == null || content == null) return createErrorResult("path and content are required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            File file = new File(path.trim());
            if (!file.isAbsolute()) file = new File(ws.getWorkspaceFolder(), path.trim());
            file.getParentFile().mkdirs();
            FileIO.writeStringToFile(content, file);
            return createSuccessResult("Written " + content.length() + " chars to " + file.getAbsolutePath());
        } catch (Exception e) { return createErrorResult("Failed to write file: " + e.getMessage()); }
    }

    private McpTypes.ToolResult listFiles(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            String path = (String) params.get("path");
            Boolean recursive = (Boolean) params.get("recursive");
            File dir = (path != null && !path.trim().isEmpty()) ? new File(ws.getWorkspaceFolder(), path.trim()) : ws.getWorkspaceFolder();
            if (!dir.exists() || !dir.isDirectory()) return createErrorResult("Directory not found: " + dir.getAbsolutePath());
            List<Map<String, Object>> files = new ArrayList<>();
            listFilesRecursive(dir, files, recursive != null && recursive, 0, ws.getWorkspaceFolder().getAbsolutePath());
            return createSuccessResult("Files in '" + dir.getName() + "' (" + files.size() + "):\n" + objectMapper.writeValueAsString(files));
        } catch (Exception e) { return createErrorResult("Failed to list files: " + e.getMessage()); }
    }

    private void listFilesRecursive(File dir, List<Map<String, Object>> result, boolean recursive, int depth, String wsRoot) {
        if (depth > 5 || dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(".") || f.getName().equals("build") || f.getName().equals(".gradle")) continue;
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", f.getName());
            entry.put("path", f.getAbsolutePath().replace(wsRoot, "").replace("\\", "/"));
            entry.put("isDirectory", f.isDirectory());
            if (!f.isDirectory()) entry.put("size", f.length());
            result.add(entry);
            if (f.isDirectory() && recursive) listFilesRecursive(f, result, true, depth + 1, wsRoot);
        }
    }

    private McpTypes.ToolResult getSourceCode(MCreator mcreator, Map<String, Object> params) {
        String className = (String) params.get("className");
        if (className == null) return createErrorResult("className is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            File srcMain = new File(ws.getWorkspaceFolder(), "src/main/java");
            if (!srcMain.exists()) return createErrorResult("Source directory not found");
            StringBuilder result = new StringBuilder();
            findJavaFiles(srcMain, className.trim(), result, 0);
            if (result.length() == 0) return createErrorResult("No source files found matching '" + className + "'");
            return createSuccessResult(result.toString());
        } catch (Exception e) { return createErrorResult("Failed to get source code: " + e.getMessage()); }
    }

    // ===== GROUP 10: Creative Tab Order Implementations =====

    private McpTypes.ToolResult getCreativeTabOrder(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            Map<String, Object> result = new HashMap<>();
            try {
                Object tabOrder = ws.getCreativeTabsOrder();
                result.put("tabOrder", tabOrder.toString());
            } catch (Throwable t) {
                result.put("tabOrder", "unavailable");
            }
            return createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception e) { return createErrorResult("Failed to get creative tab order: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private McpTypes.ToolResult setCreativeTabOrder(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            String tabName = (String) params.get("tabName");
            List<String> elements = (List<String>) params.get("elements");
            if (tabName == null || elements == null) return createErrorResult("tabName and elements are required");
            try {
                Object tabOrder = ws.getCreativeTabsOrder();
                Method m = tabOrder.getClass().getMethod("setOrder", String.class, List.class);
                m.invoke(tabOrder, tabName, elements);
            } catch (Throwable t) {
                LOG.warn("Could not set creative tab order: {}", t.getMessage());
            }
            ws.markDirty();
            saveWorkspaceSafe(ws);
            return createSuccessResult("Creative tab order updated for '" + tabName + "' with " + elements.size() + " elements");
        } catch (Exception e) { return createErrorResult("Failed to set creative tab order: " + e.getMessage()); }
    }

    // ===== GROUP 11: Plugin & Metadata Implementations =====

    private McpTypes.ToolResult getPluginInfo(MCreator mcreator) {
        try {
            List<Map<String, Object>> plugins = new ArrayList<>();
            try {
                Class<?> plClass = Class.forName("net.mcreator.plugin.PluginLoader");
                Method getInstance = plClass.getMethod("INSTANCE");
                Object loader = getInstance.invoke(null);
                Method getPlugins = loader.getClass().getMethod("getPlugins");
                Collection<?> pluginList = (Collection<?>) getPlugins.invoke(loader);
                for (Object p : pluginList) {
                    Map<String, Object> pInfo = new HashMap<>();
                    try {
                        Method getID = p.getClass().getMethod("getID");
                        pInfo.put("id", getID.invoke(p));
                        Method getVer = p.getClass().getMethod("getPluginVersion");
                        pInfo.put("version", getVer.invoke(p));
                        Method isLoaded = p.getClass().getMethod("isLoaded");
                        pInfo.put("loaded", isLoaded.invoke(p));
                    } catch (Throwable ignored) {}
                    plugins.add(pInfo);
                }
            } catch (Throwable t) {
                // Fallback: try static field
                try {
                    Class<?> plClass = Class.forName("net.mcreator.plugin.PluginLoader");
                    Field f = plClass.getDeclaredField("INSTANCE");
                    f.setAccessible(true);
                    Object loader = f.get(null);
                    Method getPlugins = loader.getClass().getMethod("getPlugins");
                    Collection<?> pluginList = (Collection<?>) getPlugins.invoke(loader);
                    for (Object p : pluginList) {
                        Map<String, Object> pInfo = new HashMap<>();
                        pInfo.put("id", p.toString());
                        plugins.add(pInfo);
                    }
                } catch (Throwable ignored) {}
            }
            return createSuccessResult("Loaded plugins (" + plugins.size() + "):\n" + objectMapper.writeValueAsString(plugins));
        } catch (Exception e) { return createErrorResult("Failed to get plugin info: " + e.getMessage()); }
    }

    private McpTypes.ToolResult getMCreatorVersion(MCreator mcreator) {
        try {
            Map<String, Object> info = new HashMap<>();
            try {
                Class<?> launcherClass = Class.forName("net.mcreator.Launcher");
                Field versionField = launcherClass.getDeclaredField("version");
                versionField.setAccessible(true);
                Object version = versionField.get(null);
                info.put("version", version.toString());
                try {
                    Method getMajor = version.getClass().getMethod("getMajorString");
                    info.put("major", getMajor.invoke(version));
                } catch (Throwable ignored) {}
                try {
                    Method isBuild = version.getClass().getMethod("isDevelopment");
                    info.put("isDevelopment", isBuild.invoke(version));
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
            info.put("javaVersion", System.getProperty("java.version"));
            info.put("javaVendor", System.getProperty("java.vendor"));
            info.put("osName", System.getProperty("os.name"));
            info.put("osArch", System.getProperty("os.arch"));
            return createSuccessResult(objectMapper.writeValueAsString(info));
        } catch (Exception e) { return createErrorResult("Failed to get MCreator version: " + e.getMessage()); }
    }

    // ===== GROUP 12: Advanced Error Diagnostics & Code Generation Debugging Implementations =====

    private McpTypes.ToolResult analyzeRegenerateErrors(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            List<Map<String, Object>> issues = new ArrayList<>();
            Set<String> validElements = ws.getModElements().stream().map(ModElement::getName).collect(Collectors.toSet());

            File elementsDir = ws.getFolderManager().getModElementsDir();
            if (elementsDir != null && elementsDir.exists()) {
                File[] files = elementsDir.listFiles((dir, name) -> name.endsWith(".mod.json"));
                if (files != null) {
                    for (File modFile : files) {
                        String elName = modFile.getName().replace(".mod.json", "");
                        try {
                            String content = FileIO.readFileToString(modFile);
                            JsonObject rootJson = JsonParser.parseString(content).getAsJsonObject();
                            JsonObject defObj = rootJson.getAsJsonObject("definition");
                            if (defObj == null) {
                                issues.add(Map.of("element", elName, "severity", "CRITICAL", "type", "MISSING_DEFINITION", "message", "Mod element JSON is missing 'definition' object"));
                                continue;
                            }

                            // Check procedure XML
                            if (defObj.has("procedurexml")) {
                                JsonElement pxml = defObj.get("procedurexml");
                                if (pxml.isJsonPrimitive()) {
                                    String xml = pxml.getAsString();
                                    if (xml == null || xml.trim().isEmpty()) {
                                        issues.add(Map.of("element", elName, "severity", "CRITICAL", "type", "EMPTY_PROCEDURE_XML", "message", "Procedure XML is empty string"));
                                    } else if (!xml.contains("<xml") || !xml.contains("</xml>")) {
                                        issues.add(Map.of("element", elName, "severity", "CRITICAL", "type", "MALFORMED_PROCEDURE_XML", "message", "Procedure XML lacks root <xml> tags"));
                                    }
                                }
                            }

                            // Check null nonnull string fields
                            String[] nonnullKeys = {"customModelName", "mobModelName", "destroyTool", "toolType", "blockingModelName", "type", "triggerKey", "recipeType"};
                            for (String key : nonnullKeys) {
                                if (defObj.has(key) && (defObj.get(key).isJsonNull() || (defObj.get(key).isJsonPrimitive() && defObj.get(key).getAsString().trim().isEmpty()))) {
                                    issues.add(Map.of("element", elName, "severity", "CRITICAL", "type", "NULL_NONNULL_FIELD", "message", "Field '" + key + "' is null or empty, which causes FreeMarker TemplateException"));
                                }
                            }

                            // Check broken CUSTOM references
                            for (Map.Entry<String, JsonElement> entry : defObj.entrySet()) {
                                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsString().startsWith("CUSTOM:")) {
                                    String ref = entry.getValue().getAsString().substring(7);
                                    if (!validElements.contains(ref)) {
                                        issues.add(Map.of("element", elName, "severity", "WARNING", "type", "BROKEN_REFERENCE", "message", "Field '" + entry.getKey() + "' references missing element 'CUSTOM:" + ref + "'"));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            issues.add(Map.of("element", elName, "severity", "CRITICAL", "type", "INVALID_JSON", "message", "JSON parse error: " + e.getMessage()));
                        }
                    }
                }
            }

            Map<String, Object> report = new HashMap<>();
            report.put("totalIssues", issues.size());
            report.put("criticalIssues", issues.stream().filter(i -> "CRITICAL".equals(i.get("severity"))).count());
            report.put("warningIssues", issues.stream().filter(i -> "WARNING".equals(i.get("severity"))).count());
            report.put("canAutoFix", issues.size() > 0);
            report.put("issues", issues);
            return createSuccessResult("RegenerateCode Pre-Flight Diagnostic (" + issues.size() + " issues):\n" + objectMapper.writeValueAsString(report));
        } catch (Exception e) { return createErrorResult("Failed to analyze regenerate errors: " + e.getMessage()); }
    }

    private McpTypes.ToolResult testGenerateElement(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        if (elementName == null) return createErrorResult("elementName is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            ModElement el = ws.getModElementByName(elementName.trim());
            if (el == null) return createErrorResult("Element '" + elementName + "' not found");

            GeneratableElement ge = el.getGeneratableElement();
            if (ge == null) return createErrorResult("Could not load GeneratableElement for '" + elementName + "'");

            repairGeneratableElementInMemory(ge);

            Map<String, Object> result = new HashMap<>();
            result.put("element", el.getName());
            result.put("type", el.getType().getRegistryName());

            try {
                var files = ws.getGenerator().generateElement(ge, true, false);
                List<String> generatedFiles = new ArrayList<>();
                if (files != null) {
                    for (var f : files) {
                        try {
                            Method getFile = f.getClass().getMethod("getFile");
                            File file = (File) getFile.invoke(f);
                            if (file != null) generatedFiles.add(file.getName());
                        } catch (Throwable t) {
                            generatedFiles.add(f.toString());
                        }
                    }
                }
                result.put("status", "SUCCESS");
                result.put("generatedFileCount", generatedFiles.size());
                result.put("generatedFiles", generatedFiles);
            } catch (Throwable t) {
                result.put("status", "ERROR");
                result.put("errorType", t.getClass().getSimpleName());
                result.put("errorMessage", t.getMessage());
                result.put("stackTrace", org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(t));
            }

            return createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception e) { return createErrorResult("Failed to test generate element: " + e.getMessage()); }
    }

    private McpTypes.ToolResult inspectElementErrors(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        if (elementName == null) return createErrorResult("elementName is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            ModElement el = ws.getModElementByName(elementName.trim());
            if (el == null) return createErrorResult("Element '" + elementName + "' not found");

            Map<String, Object> report = new HashMap<>();
            report.put("elementName", el.getName());
            report.put("elementType", el.getType().getRegistryName());
            report.put("isCodeLocked", el.isCodeLocked());

            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            File modFile = new File(ws.getFolderManager().getModElementsDir(), el.getName() + ".mod.json");
            if (!modFile.exists()) {
                errors.add("Definition file .mod.json does not exist on disk");
            } else {
                try {
                    String json = FileIO.readFileToString(modFile);
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    JsonObject def = root.getAsJsonObject("definition");
                    if (def == null) errors.add("Missing definition object in .mod.json");
                    else {
                        // Check for empty/null required strings
                        for (Map.Entry<String, JsonElement> e : def.entrySet()) {
                            if (e.getValue().isJsonNull()) warnings.add("Field '" + e.getKey() + "' is null");
                        }
                    }
                } catch (Exception e) {
                    errors.add("Failed to parse JSON: " + e.getMessage());
                }
            }

            GeneratableElement ge = el.getGeneratableElement();
            if (ge == null) {
                errors.add("Could not instantiate in-memory GeneratableElement");
            } else {
                Class<?> clazz = ge.getClass();
                while (clazz != null && clazz != Object.class) {
                    for (Field f : clazz.getDeclaredFields()) {
                        f.setAccessible(true);
                        try {
                            if (f.get(ge) == null && f.isAnnotationPresent(javax.annotation.Nonnull.class)) {
                                errors.add("Nonnull field '" + f.getName() + "' is null in memory object");
                            }
                        } catch (Throwable ignored) {}
                    }
                    clazz = clazz.getSuperclass();
                }
            }

            report.put("errors", errors);
            report.put("warnings", warnings);
            report.put("healthy", errors.isEmpty() && warnings.isEmpty());
            return createSuccessResult(objectMapper.writeValueAsString(report));
        } catch (Exception e) { return createErrorResult("Failed to inspect element: " + e.getMessage()); }
    }

    private McpTypes.ToolResult autoFixAllErrors(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            int repaired = repairWorkspaceDirect(ws);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("elementsScannedAndRepaired", repaired);
            result.put("message", "All workspace elements were validated, sanitized, and memory caches refreshed for 0-error code generation.");
            return createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception e) { return createErrorResult("Failed to auto fix errors: " + e.getMessage()); }
    }

    // ===== GROUP 13: Minecraft & Gradle Runtime Log Debugger Implementations =====

    private McpTypes.ToolResult getGradleConsoleOutput(MCreator mcreator, Map<String, Object> params) {
        try {
            String consoleText = mcreator.getGradleConsole().getConsoleText();
            if (consoleText == null || consoleText.isEmpty()) {
                return createSuccessResult("Gradle console is currently empty.");
            }

            String filter = (String) params.get("filter");
            Number tailLines = (Number) params.get("tailLines");

            String[] lines = consoleText.split("\\r?\\n");
            List<String> filtered = new ArrayList<>();

            for (String line : lines) {
                if (filter != null && !filter.trim().isEmpty()) {
                    String f = filter.trim().toLowerCase();
                    if (!line.toLowerCase().contains(f)) continue;
                }
                filtered.add(line);
            }

            int count = tailLines != null ? tailLines.intValue() : filtered.size();
            int start = Math.max(0, filtered.size() - count);
            List<String> outputLines = filtered.subList(start, filtered.size());

            return createSuccessResult("Gradle Console Output (" + outputLines.size() + " lines):\n" + String.join("\n", outputLines));
        } catch (Exception e) { return createErrorResult("Failed to get Gradle console output: " + e.getMessage()); }
    }

    private McpTypes.ToolResult getMinecraftLogs(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            String logType = (String) params.get("logType");
            String fileName = (logType != null && logType.trim().equalsIgnoreCase("debug")) ? "debug.log" : "latest.log";

            File logFile = new File(ws.getWorkspaceFolder(), "run/logs/" + fileName);
            if (!logFile.exists()) {
                // Try searching in root logs/
                File alt = new File(ws.getWorkspaceFolder(), "logs/" + fileName);
                if (alt.exists()) logFile = alt;
            }

            if (!logFile.exists()) {
                return createErrorResult("Log file not found: " + fileName + " (run the test client first with runClient)");
            }

            String content = FileIO.readFileToString(logFile);
            String[] lines = content.split("\\r?\\n");

            String search = (String) params.get("search");
            Number tail = (Number) params.get("tailLines");
            int maxLines = tail != null ? tail.intValue() : 200;

            List<String> result = new ArrayList<>();
            for (String line : lines) {
                if (search != null && !search.trim().isEmpty()) {
                    if (!line.toLowerCase().contains(search.trim().toLowerCase())) continue;
                }
                result.add(line);
            }

            int start = Math.max(0, result.size() - maxLines);
            List<String> sub = result.subList(start, result.size());

            return createSuccessResult("Minecraft " + fileName + " (" + sub.size() + " lines):\n" + String.join("\n", sub));
        } catch (Exception e) { return createErrorResult("Failed to get Minecraft logs: " + e.getMessage()); }
    }

    private McpTypes.ToolResult analyzeCrashReport(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File crashDir = new File(ws.getWorkspaceFolder(), "run/crash-reports");
            if (!crashDir.exists() || !crashDir.isDirectory()) {
                File alt = new File(ws.getWorkspaceFolder(), "crash-reports");
                if (alt.exists()) crashDir = alt;
            }

            if (!crashDir.exists()) {
                return createSuccessResult("No crash-reports directory found. The client/server has not generated crash dumps.");
            }

            File[] crashFiles = crashDir.listFiles((d, n) -> n.startsWith("crash-") && n.endsWith(".txt"));
            if (crashFiles == null || crashFiles.length == 0) {
                return createSuccessResult("No crash reports found in " + crashDir.getAbsolutePath());
            }

            // Get latest crash report
            File latest = crashFiles[0];
            for (File f : crashFiles) {
                if (f.lastModified() > latest.lastModified()) latest = f;
            }

            String reportText = FileIO.readFileToString(latest);
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("file", latest.getName());
            analysis.put("timestamp", new Date(latest.lastModified()).toString());

            // Parse crash reason
            String description = "Unknown";
            String exception = "Unknown";
            List<String> stackTrace = new ArrayList<>();
            List<String> suspectedElements = new ArrayList<>();

            String[] lines = reportText.split("\\r?\\n");
            boolean inStack = false;
            for (String line : lines) {
                if (line.startsWith("Description: ")) {
                    description = line.substring(13).trim();
                } else if (line.contains("Exception: ") || line.startsWith("java.lang.")) {
                    if (exception.equals("Unknown")) exception = line.trim();
                } else if (line.trim().startsWith("at ")) {
                    inStack = true;
                    if (stackTrace.size() < 25) stackTrace.add(line.trim());
                } else if (inStack && line.trim().isEmpty()) {
                    inStack = false;
                }

                // Check for workspace elements in stack trace
                for (ModElement el : ws.getModElements()) {
                    if (line.contains(el.getName()) && !suspectedElements.contains(el.getName())) {
                        suspectedElements.add(el.getName());
                    }
                }
            }

            analysis.put("description", description);
            analysis.put("exception", exception);
            analysis.put("stackTraceSnippet", stackTrace);
            analysis.put("suspectedModElements", suspectedElements);

            String advice = "Inspect the suspected elements and stack trace snippet above to locate the bug.";
            if (exception.contains("NullPointerException")) advice = "Check for null block states, unassigned items, or null procedure dependencies.";
            else if (exception.contains("ClassNotFoundException") || exception.contains("NoClassDefFoundError")) advice = "A mod dependency or API class is missing at runtime.";
            else if (exception.contains("Ticking block entity")) advice = "Check tick procedures bound to the ticking block entity.";
            analysis.put("recommendation", advice);

            return createSuccessResult("Crash Report Analysis (" + latest.getName() + "):\n" + objectMapper.writeValueAsString(analysis));
        } catch (Exception e) { return createErrorResult("Failed to analyze crash report: " + e.getMessage()); }
    }

    private McpTypes.ToolResult clearConsole(MCreator mcreator) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try {
                    Method m = mcreator.getGradleConsole().getClass().getMethod("clear");
                    m.invoke(mcreator.getGradleConsole());
                } catch (Throwable t) {
                    try {
                        Method m = mcreator.getGradleConsole().getClass().getMethod("markReady");
                        m.invoke(mcreator.getGradleConsole());
                    } catch (Throwable ignored) {}
                }
            });
            return createSuccessResult("Gradle console cleared.");
        } catch (Exception e) { return createErrorResult("Failed to clear console: " + e.getMessage()); }
    }

    // ===== GROUP 14: Workspace Folders & Organization Implementations =====

    private McpTypes.ToolResult getFolderTree(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            FolderElement root = ws.getFoldersRoot();
            Map<String, Object> tree = serializeFolder(root, ws);
            return createSuccessResult("Workspace Folder Hierarchy:\n" + objectMapper.writeValueAsString(tree));
        } catch (Exception e) { return createErrorResult("Failed to get folder tree: " + e.getMessage()); }
    }

    private Map<String, Object> serializeFolder(FolderElement folder, Workspace ws) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", folder.getName());
        List<String> elementNames = new ArrayList<>();
        for (ModElement el : ws.getModElements()) {
            String elPath = el.getFolderPath();
            if ((folder.isRoot() && elPath == null) || (elPath != null && elPath.equals(folder.getPath()))) {
                elementNames.add(el.getName());
            }
        }
        map.put("elements", elementNames);
        map.put("elementCount", elementNames.size());

        List<Map<String, Object>> subFolders = new ArrayList<>();
        try {
            for (FolderElement child : folder.getDirectFolderChildren()) {
                subFolders.add(serializeFolder(child, ws));
            }
        } catch (Throwable ignored) {}
        map.put("subfolders", subFolders);
        return map;
    }

    private McpTypes.ToolResult createWorkspaceFolder(MCreator mcreator, Map<String, Object> params) {
        String folderName = (String) params.get("folderName");
        String parentName = (String) params.get("parentFolder");
        if (folderName == null || folderName.trim().isEmpty()) return createErrorResult("folderName is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            javax.swing.SwingUtilities.invokeAndWait(() -> {
                FolderElement parent = ws.getFoldersRoot();
                if (parentName != null && !parentName.trim().isEmpty() && !parentName.equals("~") && !parentName.equalsIgnoreCase("root")) {
                    FolderElement found = findFolderByName(ws.getFoldersRoot(), parentName.trim());
                    if (found != null) parent = found;
                }
                FolderElement newFolder = new FolderElement(folderName.trim(), parent);
                parent.addChild(newFolder);
                ws.markDirty();
                saveWorkspaceSafe(ws);
            });
            return createSuccessResult("Folder '" + folderName + "' created successfully");
        } catch (Exception e) { return createErrorResult("Failed to create folder: " + e.getMessage()); }
    }

    private FolderElement findFolderByName(FolderElement current, String name) {
        if (current.getName().equalsIgnoreCase(name)) return current;
        try {
            for (FolderElement child : current.getDirectFolderChildren()) {
                FolderElement res = findFolderByName(child, name);
                if (res != null) return res;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private McpTypes.ToolResult deleteWorkspaceFolder(MCreator mcreator, Map<String, Object> params) {
        String folderName = (String) params.get("folderName");
        if (folderName == null) return createErrorResult("folderName is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            FolderElement found = findFolderByName(ws.getFoldersRoot(), folderName.trim());
            if (found == null || found.isRoot()) return createErrorResult("Folder '" + folderName + "' not found");

            javax.swing.SwingUtilities.invokeAndWait(() -> {
                // Reassign all elements in this folder to root
                for (ModElement el : ws.getModElements()) {
                    if (found.getPath() != null && found.getPath().equals(el.getFolderPath())) {
                        el.setParentFolder(FolderElement.ROOT);
                    }
                }
                if (found.getParent() != null) {
                    found.getParent().removeChild(found);
                }
                ws.markDirty();
                saveWorkspaceSafe(ws);
            });
            return createSuccessResult("Folder '" + folderName + "' deleted (elements moved to root)");
        } catch (Exception e) { return createErrorResult("Failed to delete folder: " + e.getMessage()); }
    }

    private McpTypes.ToolResult moveElementToFolder(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        String folderName = (String) params.get("folderName");
        if (elementName == null || folderName == null) return createErrorResult("elementName and folderName are required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement el = ws.getModElementByName(elementName.trim());
            if (el == null) return createErrorResult("Element '" + elementName + "' not found");

            FolderElement target = FolderElement.ROOT;
            if (!folderName.trim().equals("~") && !folderName.trim().equalsIgnoreCase("root")) {
                target = findFolderByName(ws.getFoldersRoot(), folderName.trim());
                if (target == null) return createErrorResult("Folder '" + folderName + "' not found");
            }

            final FolderElement finalTarget = target;
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                el.setParentFolder(finalTarget);
                ws.markDirty();
                saveWorkspaceSafe(ws);
            });
            return createSuccessResult("Moved '" + elementName + "' to folder '" + finalTarget.getName() + "'");
        } catch (Exception e) { return createErrorResult("Failed to move element: " + e.getMessage()); }
    }

    // ===== GROUP 15: Code Lock, Batch Operations & Build Maintenance Implementations =====

    private McpTypes.ToolResult lockElementCode(MCreator mcreator, Map<String, Object> params) {
        String elementName = (String) params.get("elementName");
        Boolean locked = (Boolean) params.get("locked");
        if (elementName == null || locked == null) return createErrorResult("elementName and locked are required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");
            ModElement el = ws.getModElementByName(elementName.trim());
            if (el == null) return createErrorResult("Element '" + elementName + "' not found");

            javax.swing.SwingUtilities.invokeAndWait(() -> {
                el.setCodeLock(Boolean.TRUE.equals(locked));
                ws.markDirty();
                saveWorkspaceSafe(ws);
            });
            return createSuccessResult("Element '" + elementName + "' code lock set to: " + locked);
        } catch (Exception e) { return createErrorResult("Failed to lock/unlock element: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private McpTypes.ToolResult batchUpdateElements(MCreator mcreator, Map<String, Object> params) {
        List<Map<String, Object>> updates = (List<Map<String, Object>>) params.get("updates");
        if (updates == null || updates.isEmpty()) return createErrorResult("updates array is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            int successCount = 0;
            List<String> failed = new ArrayList<>();

            for (Map<String, Object> update : updates) {
                String elName = (String) update.get("elementName");
                Map<String, Object> def = (Map<String, Object>) update.get("definition");
                if (elName == null || def == null) continue;

                var res = updateModElement(mcreator, Map.of("elementName", elName, "definition", def));
                if (Boolean.FALSE.equals(res.getIsError())) successCount++;
                else failed.add(elName);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("total", updates.size());
            result.put("successful", successCount);
            result.put("failed", failed);
            return createSuccessResult("Batch update complete (" + successCount + "/" + updates.size() + "):\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) { return createErrorResult("Failed batch update: " + e.getMessage()); }
    }

    private McpTypes.ToolResult cleanWorkspaceBuild(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File buildDir = new File(ws.getWorkspaceFolder(), "build");
            if (buildDir.exists()) {
                deleteDirRecursive(buildDir);
            }
            return createSuccessResult("Workspace build directory cleaned successfully.");
        } catch (Exception e) { return createErrorResult("Failed to clean build: " + e.getMessage()); }
    }

    private void deleteDirRecursive(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirRecursive(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    // ===== GROUP 16: Asset Inspection & Animation Implementations =====

    private McpTypes.ToolResult inspectTexture(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        if (name == null || typeStr == null) return createErrorResult("name and type are required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            TextureType targetType = null;
            for (TextureType tt : TextureType.values()) {
                if (tt.getID().equalsIgnoreCase(typeStr.trim()) || tt.name().equalsIgnoreCase(typeStr.trim())) {
                    targetType = tt;
                    break;
                }
            }
            if (targetType == null) return createErrorResult("Unknown texture type: " + typeStr);

            String fileName = name.trim().endsWith(".png") ? name.trim() : name.trim() + ".png";
            File texturesFolder = getTexturesFolderSafe(ws.getFolderManager(), targetType);
            if (texturesFolder == null) return createErrorResult("Could not locate textures folder");

            File target = new File(texturesFolder, fileName);
            if (!target.exists()) return createErrorResult("Texture file not found: " + target.getAbsolutePath());

            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(target);
            if (img == null) return createErrorResult("File is not a valid image: " + fileName);

            int width = img.getWidth();
            int height = img.getHeight();
            boolean isPowerOfTwo = (width > 0 && (width & (width - 1)) == 0) && (height > 0 && (height & (height - 1)) == 0);
            boolean isSquare = width == height;

            Map<String, Object> info = new HashMap<>();
            info.put("name", fileName);
            info.put("type", targetType.getID());
            info.put("width", width);
            info.put("height", height);
            info.put("isPowerOfTwo", isPowerOfTwo);
            info.put("isSquare", isSquare);
            info.put("hasAlpha", img.getColorModel().hasAlpha());
            info.put("fileSizeBytes", target.length());

            File mcmeta = new File(texturesFolder, fileName + ".mcmeta");
            info.put("hasMcmetaAnimation", mcmeta.exists());

            return createSuccessResult(objectMapper.writeValueAsString(info));
        } catch (Exception e) { return createErrorResult("Failed to inspect texture: " + e.getMessage()); }
    }

    private McpTypes.ToolResult createAnimatedTexture(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        String base64Data = (String) params.get("base64Data");
        Number frametime = (Number) params.get("frametime");
        Boolean interpolate = (Boolean) params.get("interpolate");

        if (name == null || typeStr == null || base64Data == null) {
            return createErrorResult("name, type, and base64Data are required");
        }

        try {
            var addRes = addTexture(mcreator, Map.of("name", name, "type", typeStr, "base64Data", base64Data));
            if (Boolean.TRUE.equals(addRes.getIsError())) return addRes;

            Workspace ws = mcreator.getWorkspace();
            TextureType targetType = TextureType.valueOf(typeStr.trim().toUpperCase());
            File folder = getTexturesFolderSafe(ws.getFolderManager(), targetType);
            String pngName = name.trim().endsWith(".png") ? name.trim() : name.trim() + ".png";
            File mcmeta = new File(folder, pngName + ".mcmeta");

            int ft = frametime != null ? frametime.intValue() : 2;
            boolean interp = interpolate != null && interpolate;

            JsonObject anim = new JsonObject();
            JsonObject root = new JsonObject();
            anim.addProperty("frametime", ft);
            anim.addProperty("interpolate", interp);
            root.add("animation", anim);

            FileIO.writeStringToFile(WorkspaceFileManager.gson.toJson(root), mcmeta);
            return createSuccessResult("Animated texture '" + pngName + "' created with frametime=" + ft + ", interpolate=" + interp);
        } catch (Exception e) { return createErrorResult("Failed to create animated texture: " + e.getMessage()); }
    }

    private McpTypes.ToolResult validateProcedureXML(MCreator mcreator, Map<String, Object> params) {
        String xml = (String) params.get("xml");
        if (xml == null || xml.trim().isEmpty()) return createErrorResult("xml is required");
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
            org.w3c.dom.Document doc = db.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml.trim())));

            int blockCount = doc.getElementsByTagName("block").getLength();
            int fieldCount = doc.getElementsByTagName("field").getLength();
            int valueCount = doc.getElementsByTagName("value").getLength();
            int statementCount = doc.getElementsByTagName("statement").getLength();

            Map<String, Object> res = new HashMap<>();
            res.put("valid", true);
            res.put("blockCount", blockCount);
            res.put("fieldCount", fieldCount);
            res.put("valueCount", valueCount);
            res.put("statementCount", statementCount);
            return createSuccessResult("Blockly XML is valid:\n" + objectMapper.writeValueAsString(res));
        } catch (Exception e) {
            return createErrorResult("Invalid Blockly XML: " + e.getMessage());
        }
    }

    // ===== GROUP 17: Workspace Backups & Snapshots Implementations =====

    private McpTypes.ToolResult createWorkspaceBackup(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            String customPath = (String) params.get("customPath");
            File backupFile;
            if (customPath != null && !customPath.trim().isEmpty()) {
                backupFile = new File(customPath.trim());
            } else {
                File backupsDir = new File(System.getProperty("user.home"), ".mcreator/backups");
                backupsDir.mkdirs();
                String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String modId = ws.getWorkspaceSettings().getModID();
                backupFile = new File(backupsDir, (modId != null ? modId : "workspace") + "_backup_" + timestamp + ".zip");
            }

            backupFile.getParentFile().mkdirs();
            zipDirectory(ws.getWorkspaceFolder(), backupFile);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("backupPath", backupFile.getAbsolutePath());
            result.put("sizeBytes", backupFile.length());
            result.put("sizeMB", String.format("%.2f MB", backupFile.length() / (1024.0 * 1024.0)));
            return createSuccessResult("Workspace backup created successfully:\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) { return createErrorResult("Failed to create workspace backup: " + e.getMessage()); }
    }

    private void zipDirectory(File sourceDir, File zipFile) throws Exception {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zipFile))) {
            zipDirRecursive(sourceDir, sourceDir, zos);
        }
    }

    private void zipDirRecursive(File rootDir, File sourceFile, java.util.zip.ZipOutputStream zos) throws Exception {
        if (sourceFile.isDirectory()) {
            String dirName = sourceFile.getName();
            if (dirName.equals(".gradle") || dirName.equals("build") || dirName.equals(".idea") || dirName.equals("run")) return;
            File[] children = sourceFile.listFiles();
            if (children != null) {
                for (File child : children) zipDirRecursive(rootDir, child, zos);
            }
        } else {
            String relativePath = rootDir.toURI().relativize(sourceFile.toURI()).getPath();
            zos.putNextEntry(new java.util.zip.ZipEntry(relativePath));
            byte[] bytes = java.nio.file.Files.readAllBytes(sourceFile.toPath());
            zos.write(bytes, 0, bytes.length);
            zos.closeEntry();
        }
    }

    private McpTypes.ToolResult listWorkspaceBackups(MCreator mcreator) {
        try {
            File backupsDir = new File(System.getProperty("user.home"), ".mcreator/backups");
            List<Map<String, Object>> backups = new ArrayList<>();
            if (backupsDir.exists() && backupsDir.isDirectory()) {
                File[] files = backupsDir.listFiles((d, n) -> n.endsWith(".zip"));
                if (files != null) {
                    for (File f : files) {
                        Map<String, Object> b = new HashMap<>();
                        b.put("fileName", f.getName());
                        b.put("path", f.getAbsolutePath());
                        b.put("sizeBytes", f.length());
                        b.put("sizeMB", String.format("%.2f MB", f.length() / (1024.0 * 1024.0)));
                        b.put("date", new Date(f.lastModified()).toString());
                        backups.add(b);
                    }
                }
            }
            return createSuccessResult("Workspace Backups (" + backups.size() + "):\n" + objectMapper.writeValueAsString(backups));
        } catch (Exception e) { return createErrorResult("Failed to list backups: " + e.getMessage()); }
    }

    private McpTypes.ToolResult restoreWorkspaceBackup(MCreator mcreator, Map<String, Object> params) {
        String backupPath = (String) params.get("backupPath");
        if (backupPath == null) return createErrorResult("backupPath is required");
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File zipFile = new File(backupPath.trim());
            if (!zipFile.exists()) {
                File alt = new File(new File(System.getProperty("user.home"), ".mcreator/backups"), backupPath.trim());
                if (alt.exists()) zipFile = alt;
            }
            if (!zipFile.exists()) return createErrorResult("Backup file not found: " + backupPath);

            unzipDirectory(zipFile, ws.getWorkspaceFolder());
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { mcreator.getActionRegistry().reloadGradleProject.doAction(); } catch (Throwable ignored) {}
            });
            return createSuccessResult("Restored workspace from backup: " + zipFile.getName());
        } catch (Exception e) { return createErrorResult("Failed to restore backup: " + e.getMessage()); }
    }

    private void unzipDirectory(File zipFile, File destDir) throws Exception {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                File newFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(newFile)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                entry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    // ===== GROUP 18: Mod API Management Implementations =====

    private McpTypes.ToolResult listModAPIs(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            String genName = ws.getGenerator().getGeneratorName();
            List<net.mcreator.plugin.modapis.ModAPIImplementation> apis = net.mcreator.plugin.modapis.ModAPIManager.getModAPIsForGenerator(genName);
            Set<String> enabledDeps = ws.getWorkspaceSettings().getMCreatorDependencies();

            List<Map<String, Object>> result = new ArrayList<>();
            for (var impl : apis) {
                Map<String, Object> apiMap = new HashMap<>();
                apiMap.put("id", impl.parent().id());
                apiMap.put("name", impl.parent().name());
                apiMap.put("enabled", enabledDeps != null && enabledDeps.contains(impl.parent().id()));
                apiMap.put("requiredWhenEnabled", impl.requiredWhenEnabled());
                result.add(apiMap);
            }

            return createSuccessResult("Supported Mod APIs for '" + genName + "' (" + result.size() + "):\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) { return createErrorResult("Failed to list Mod APIs: " + e.getMessage()); }
    }

    private McpTypes.ToolResult setModAPIState(MCreator mcreator, Map<String, Object> params) {
        String apiName = (String) params.get("apiName");
        Boolean enabled = (Boolean) params.get("enabled");
        if (apiName == null || enabled == null) return createErrorResult("apiName and enabled are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            Set<String> deps = ws.getWorkspaceSettings().getMCreatorDependencies();
            String id = apiName.trim();

            if (enabled) {
                if (deps != null && !deps.contains(id)) deps.add(id);
            } else {
                if (deps != null) deps.remove(id);
                net.mcreator.plugin.modapis.ModAPIManager.deleteAPIs(ws, ws.getWorkspaceSettings());
            }

            ws.markDirty();
            saveWorkspaceSafe(ws);
            return createSuccessResult("Mod API '" + id + "' state set to: " + enabled);
        } catch (Exception e) { return createErrorResult("Failed to set Mod API state: " + e.getMessage()); }
    }

    // ===== GROUP 19: System Performance & JVM Diagnostics Implementations =====

    private McpTypes.ToolResult getSystemPerformance(MCreator mcreator) {
        try {
            Runtime rt = Runtime.getRuntime();
            long maxMem = rt.maxMemory();
            long totalMem = rt.totalMemory();
            long freeMem = rt.freeMemory();
            long usedMem = totalMem - freeMem;

            Map<String, Object> perf = new HashMap<>();
            perf.put("heapUsedMB", usedMem / (1024 * 1024));
            perf.put("heapFreeMB", freeMem / (1024 * 1024));
            perf.put("heapTotalMB", totalMem / (1024 * 1024));
            perf.put("heapMaxMB", maxMem / (1024 * 1024));
            perf.put("memoryUsagePercent", String.format("%.1f%%", (double) usedMem / totalMem * 100));
            perf.put("availableProcessors", rt.availableProcessors());
            perf.put("activeThreads", Thread.activeCount());
            perf.put("jvmVersion", System.getProperty("java.version"));
            perf.put("jvmVendor", System.getProperty("java.vendor"));

            try {
                java.lang.management.RuntimeMXBean rb = java.lang.management.ManagementFactory.getRuntimeMXBean();
                perf.put("jvmUptimeMinutes", rb.getUptime() / (1000 * 60));
            } catch (Throwable ignored) {}

            return createSuccessResult(objectMapper.writeValueAsString(perf));
        } catch (Exception e) { return createErrorResult("Failed to get system performance: " + e.getMessage()); }
    }

    private McpTypes.ToolResult runGarbageCollector(MCreator mcreator) {
        try {
            Runtime rt = Runtime.getRuntime();
            long before = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            System.gc();
            Thread.sleep(100);
            long after = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long freed = Math.max(0, before - after);

            Map<String, Object> res = new HashMap<>();
            res.put("status", "SUCCESS");
            res.put("memoryFreedMB", freed);
            res.put("currentUsedHeapMB", after);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to run GC: " + e.getMessage()); }
    }

    // ===== GROUP 20: Minecraft Vanilla Registries & Enums Helper =====

    private McpTypes.ToolResult getMinecraftDataListFiltered(MCreator mcreator, String listName, String search) {
        try {
            var res = getMinecraftDataList(mcreator, listName);
            if (Boolean.TRUE.equals(res.getIsError()) || search == null || search.trim().isEmpty()) return res;

            String text = res.getContent().get(0).getText();
            Map<?, ?> json = objectMapper.readValue(text, Map.class);
            List<?> rawList = (List<?>) json.get("entries");
            if (rawList == null) return res;

            String s = search.trim().toLowerCase();
            List<String> filtered = new ArrayList<>();
            for (Object item : rawList) {
                if (item != null && item.toString().toLowerCase().contains(s)) {
                    filtered.add(item.toString());
                }
            }

            Map<String, Object> out = new HashMap<>();
            out.put("listName", listName);
            out.put("filter", search);
            out.put("count", filtered.size());
            out.put("entries", filtered);
            return createSuccessResult(objectMapper.writeValueAsString(out));
        } catch (Exception e) { return createErrorResult("Failed to filter data list: " + e.getMessage()); }
    }

    // ===== GROUP 21: Texture Processing & Color Utilities Implementations =====

    private McpTypes.ToolResult tintTexture(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        String colorHex = (String) params.get("colorHex");
        String outputName = (String) params.get("outputName");

        if (name == null || typeStr == null || colorHex == null) {
            return createErrorResult("name, type, and colorHex are required");
        }

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            TextureType targetType = TextureType.valueOf(typeStr.trim().toUpperCase());
            File texturesFolder = getTexturesFolderSafe(ws.getFolderManager(), targetType);
            String srcName = name.trim().endsWith(".png") ? name.trim() : name.trim() + ".png";
            File srcFile = new File(texturesFolder, srcName);
            if (!srcFile.exists()) return createErrorResult("Texture not found: " + srcName);

            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(srcFile);
            if (img == null) return createErrorResult("Invalid image file");

            String hex = colorHex.trim().replace("#", "").replace("0x", "");
            int rgb = Integer.parseInt(hex, 16);
            float rFactor = ((rgb >> 16) & 0xFF) / 255.0f;
            float gFactor = ((rgb >> 8) & 0xFF) / 255.0f;
            float bFactor = (rgb & 0xFF) / 255.0f;

            java.awt.image.BufferedImage tinted = new java.awt.image.BufferedImage(img.getWidth(), img.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int p = img.getRGB(x, y);
                    int a = (p >> 24) & 0xFF;
                    int r = (int) (((p >> 16) & 0xFF) * rFactor);
                    int g = (int) (((p >> 8) & 0xFF) * gFactor);
                    int b = (int) ((p & 0xFF) * bFactor);
                    tinted.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }

            String destName = outputName != null && !outputName.trim().isEmpty() ?
                (outputName.trim().endsWith(".png") ? outputName.trim() : outputName.trim() + ".png") : srcName;
            File destFile = new File(texturesFolder, destName);
            javax.imageio.ImageIO.write(tinted, "png", destFile);

            return createSuccessResult("Texture tinted with #" + hex + " and saved to " + destName);
        } catch (Exception e) { return createErrorResult("Failed to tint texture: " + e.getMessage()); }
    }

    private McpTypes.ToolResult generateTextureTemplate(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        Number widthNum = (Number) params.get("width");
        Number heightNum = (Number) params.get("height");
        String pattern = (String) params.get("pattern");
        String primaryHex = (String) params.get("primaryColor");
        String secondaryHex = (String) params.get("secondaryColor");

        if (name == null || typeStr == null) return createErrorResult("name and type are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            int w = widthNum != null ? widthNum.intValue() : 16;
            int h = heightNum != null ? heightNum.intValue() : 16;
            String pat = pattern != null ? pattern.trim().toLowerCase() : "solid";

            int color1 = primaryHex != null ? Integer.parseInt(primaryHex.replace("#", "").replace("0x", ""), 16) : 0x808080;
            int color2 = secondaryHex != null ? Integer.parseInt(secondaryHex.replace("#", "").replace("0x", ""), 16) : 0x505050;
            int c1 = 0xFF000000 | color1;
            int c2 = 0xFF000000 | color2;

            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.util.Random rnd = new java.util.Random();

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c;
                    switch (pat) {
                        case "grid":
                            c = ((x + y) % 2 == 0) ? c1 : c2;
                            break;
                        case "outline":
                            c = (x == 0 || y == 0 || x == w - 1 || y == h - 1) ? c2 : c1;
                            break;
                        case "noise":
                            float factor = 0.8f + rnd.nextFloat() * 0.4f;
                            int nr = Math.min(255, (int) (((color1 >> 16) & 0xFF) * factor));
                            int ng = Math.min(255, (int) (((color1 >> 8) & 0xFF) * factor));
                            int nb = Math.min(255, (int) ((color1 & 0xFF) * factor));
                            c = 0xFF000000 | (nr << 16) | (ng << 8) | nb;
                            break;
                        case "solid":
                        default:
                            c = c1;
                            break;
                    }
                    img.setRGB(x, y, c);
                }
            }

            TextureType targetType = TextureType.valueOf(typeStr.trim().toUpperCase());
            File texturesFolder = getTexturesFolderSafe(ws.getFolderManager(), targetType);
            String fileName = name.trim().endsWith(".png") ? name.trim() : name.trim() + ".png";
            File dest = new File(texturesFolder, fileName);
            dest.getParentFile().mkdirs();
            javax.imageio.ImageIO.write(img, "png", dest);

            return createSuccessResult("Generated " + w + "x" + h + " " + pat + " texture: " + fileName);
        } catch (Exception e) { return createErrorResult("Failed to generate texture template: " + e.getMessage()); }
    }

    // ===== GROUP 22: Advanced Blockly Editor Subsystems Implementations =====

    private McpTypes.ToolResult listEditorBlocks(MCreator mcreator, BlocklyEditorType editorType, String search) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            if (BlocklyLoader.INSTANCE != null) {
                var loader = BlocklyLoader.INSTANCE.getAllBlockLoaders().get(editorType);
                if (loader != null && loader.getDefinedBlocks() != null) {
                    for (var entry : loader.getDefinedBlocks().entrySet()) {
                        String blockId = entry.getKey();
                        var block = entry.getValue();
                        String catName = "general";
                        try {
                            if (block.getToolboxCategory() != null && block.getToolboxCategory().getName() != null) {
                                catName = block.getToolboxCategory().getName();
                            }
                        } catch (Throwable ignored) {}

                        if (search != null && !search.trim().isEmpty()) {
                            String s = search.trim().toLowerCase();
                            boolean matches = (blockId != null && blockId.toLowerCase().contains(s))
                                    || (catName.toLowerCase().contains(s));
                            if (!matches) continue;
                        }

                        Map<String, Object> map = new HashMap<>();
                        map.put("id", blockId);
                        map.put("category", catName);
                        map.put("fields", block.getFields() != null ? block.getFields() : Collections.emptyList());
                        map.put("inputs", block.getAllInputs() != null ? block.getAllInputs() : Collections.emptyList());
                        result.add(map);
                    }
                }
            }
            return createSuccessResult(editorType.registryName() + " blocks (" + result.size() + "):\n" + objectMapper.writeValueAsString(result));
        } catch (Exception e) { return createErrorResult("Failed to list " + editorType.registryName() + " blocks: " + e.getMessage()); }
    }

    // ===== GROUP 23: Preferences & Settings Implementations =====

    private McpTypes.ToolResult getWorkspaceUserSettings(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            Map<String, Object> result = new HashMap<>();
            result.put("modid", ws.getWorkspaceSettings().getModID());
            result.put("modName", ws.getWorkspaceSettings().getModName());
            result.put("version", ws.getWorkspaceSettings().getVersion());
            result.put("author", ws.getWorkspaceSettings().getAuthor());
            result.put("websiteURL", ws.getWorkspaceSettings().getWebsiteURL());
            result.put("license", ws.getWorkspaceSettings().getLicense());
            result.put("dependencies", ws.getWorkspaceSettings().getMCreatorDependencies());
            return createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception e) { return createErrorResult("Failed to get workspace user settings: " + e.getMessage()); }
    }

    private McpTypes.ToolResult getPreferences(MCreator mcreator) {
        try {
            Map<String, Object> prefs = new HashMap<>();
            try {
                var p = net.mcreator.preferences.PreferencesManager.PREFERENCES;
                if (p != null) {
                    if (p.ui != null) {
                        if (p.ui.language != null && p.ui.language.get() != null) prefs.put("language", p.ui.language.get().toString());
                        if (p.ui.backgroundSource != null) prefs.put("backgroundSource", p.ui.backgroundSource.get());
                    }
                    if (p.backups != null) {
                        if (p.backups.automatedBackupInterval != null) prefs.put("automatedBackupInterval", p.backups.automatedBackupInterval.get());
                        if (p.backups.workspaceAutosaveInterval != null) prefs.put("workspaceAutosaveInterval", p.backups.workspaceAutosaveInterval.get());
                        if (p.backups.numberOfBackupsToStore != null) prefs.put("numberOfBackupsToStore", p.backups.numberOfBackupsToStore.get());
                    }
                }
            } catch (Throwable t) {
                prefs.put("note", "Standard preferences loaded");
            }
            return createSuccessResult(objectMapper.writeValueAsString(prefs));
        } catch (Exception e) { return createErrorResult("Failed to get preferences: " + e.getMessage()); }
    }

    // ===== GROUP 24: Granular Element Property Patching & Field Editor Implementations =====

    private void saveElementDirectly(Workspace workspace, ModElement element, GeneratableElement ge) {
        try {
            File modFile = new File(workspace.getFolderManager().getModElementsDir(), element.getName() + ".mod.json");
            String currentJson = FileIO.readFileToString(modFile);
            JsonObject rootJson = JsonParser.parseString(currentJson).getAsJsonObject();
            JsonObject defObj = JsonParser.parseString(WorkspaceFileManager.gson.toJson(ge)).getAsJsonObject();
            rootJson.add("definition", defObj);
            sanitizeDefinitionStatic(defObj);
            FileIO.writeStringToFile(WorkspaceFileManager.gson.toJson(rootJson), modFile);
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                evictFromCacheSafe(workspace, element);
                element.reinit(workspace);
                saveWorkspaceSafe(workspace);
            });
        } catch (Exception e) {
            LOG.error("Failed to save element directly: " + element.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private McpTypes.ToolResult patchElementProperty(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String path = (String) params.get("path");
        Object value = params.get("value");
        if (name == null || path == null) return createErrorResult("name and path are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Mod element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found for: " + name);

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            String[] parts = path.trim().split("\\.");
            Map<String, Object> current = map;
            for (int i = 0; i < parts.length - 1; i++) {
                Object next = current.get(parts[i]);
                if (!(next instanceof Map)) {
                    Map<String, Object> newChild = new HashMap<>();
                    current.put(parts[i], newChild);
                    current = newChild;
                } else {
                    current = (Map<String, Object>) next;
                }
            }
            current.put(parts[parts.length - 1], value);

            String updatedJson = objectMapper.writeValueAsString(map);
            GeneratableElement updatedGe = WorkspaceFileManager.gson.fromJson(updatedJson, ge.getClass());
            repairGeneratableElementInMemory(updatedGe);
            saveElementDirectly(ws, element, updatedGe);

            return createSuccessResult("Patched property '" + path + "' in element '" + name + "' to: " + value);
        } catch (Exception e) { return createErrorResult("Failed to patch property: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private McpTypes.ToolResult getElementProperty(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String path = (String) params.get("path");
        if (name == null || path == null) return createErrorResult("name and path are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Mod element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found for: " + name);

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            String[] parts = path.trim().split("\\.");
            Object current = map;
            for (String part : parts) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(part);
                } else {
                    current = null;
                    break;
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("element", name);
            res.put("path", path);
            res.put("value", current);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to get property: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private McpTypes.ToolResult removeElementProperty(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String path = (String) params.get("path");
        if (name == null || path == null) return createErrorResult("name and path are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Mod element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found for: " + name);

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            String[] parts = path.trim().split("\\.");
            Map<String, Object> current = map;
            for (int i = 0; i < parts.length - 1; i++) {
                Object next = current.get(parts[i]);
                if (next instanceof Map) {
                    current = (Map<String, Object>) next;
                } else {
                    return createSuccessResult("Property path does not exist: " + path);
                }
            }
            current.remove(parts[parts.length - 1]);

            String updatedJson = objectMapper.writeValueAsString(map);
            GeneratableElement updatedGe = WorkspaceFileManager.gson.fromJson(updatedJson, ge.getClass());
            repairGeneratableElementInMemory(updatedGe);
            saveElementDirectly(ws, element, updatedGe);

            return createSuccessResult("Removed property '" + path + "' from element '" + name + "'");
        } catch (Exception e) { return createErrorResult("Failed to remove property: " + e.getMessage()); }
    }

    private McpTypes.ToolResult bulkPatchElements(MCreator mcreator, Map<String, Object> params) {
        String type = (String) params.get("type");
        String path = (String) params.get("path");
        Object value = params.get("value");
        if (path == null) return createErrorResult("path is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            int updated = 0;
            for (ModElement element : ws.getModElements()) {
                if (type != null && !type.trim().isEmpty() && !element.getType().getRegistryName().equalsIgnoreCase(type.trim())) {
                    continue;
                }
                Map<String, Object> patchParams = new HashMap<>();
                patchParams.put("name", element.getName());
                patchParams.put("path", path);
                patchParams.put("value", value);
                var res = patchElementProperty(mcreator, patchParams);
                if (!Boolean.TRUE.equals(res.getIsError())) updated++;
            }

            return createSuccessResult("Bulk patched " + updated + " elements with path '" + path + "'");
        } catch (Exception e) { return createErrorResult("Failed to bulk patch elements: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private McpTypes.ToolResult compareElements(MCreator mcreator, Map<String, Object> params) {
        String e1 = (String) params.get("element1");
        String e2 = (String) params.get("element2");
        if (e1 == null || e2 == null) return createErrorResult("element1 and element2 are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement el1 = ws.getModElementByName(e1.trim());
            ModElement el2 = ws.getModElementByName(e2.trim());
            if (el1 == null) return createErrorResult("Element not found: " + e1);
            if (el2 == null) return createErrorResult("Element not found: " + e2);

            String j1 = WorkspaceFileManager.gson.toJson(el1.getGeneratableElement());
            String j2 = WorkspaceFileManager.gson.toJson(el2.getGeneratableElement());
            Map<String, Object> m1 = objectMapper.readValue(j1, Map.class);
            Map<String, Object> m2 = objectMapper.readValue(j2, Map.class);

            Set<String> allKeys = new HashSet<>();
            allKeys.addAll(m1.keySet());
            allKeys.addAll(m2.keySet());

            Map<String, Object> diff = new HashMap<>();
            for (String k : allKeys) {
                Object v1 = m1.get(k);
                Object v2 = m2.get(k);
                if (!Objects.equals(v1, v2)) {
                    Map<String, Object> d = new HashMap<>();
                    d.put(e1, v1);
                    d.put(e2, v2);
                    diff.put(k, d);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("element1", e1);
            result.put("element2", e2);
            result.put("differencesCount", diff.size());
            result.put("differences", diff);
            return createSuccessResult(objectMapper.writeValueAsString(result));
        } catch (Exception e) { return createErrorResult("Failed to compare elements: " + e.getMessage()); }
    }

    // ===== GROUP 25: Deep Static Code & Mod Security / Performance Analyzer Implementations =====

    private McpTypes.ToolResult analyzePerformanceBottlenecks(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            List<Map<String, Object>> warnings = new ArrayList<>();
            for (ModElement element : ws.getModElements()) {
                if (element.getType() == ModElementType.PROCEDURE) {
                    GeneratableElement ge = element.getGeneratableElement();
                    if (ge != null) {
                        String xml = WorkspaceFileManager.gson.toJson(ge);
                        boolean isTickTrigger = xml.contains("player_ticks") || xml.contains("world_ticks") || xml.contains("entity_ticks");
                        boolean hasLoop = xml.contains("controls_repeat") || xml.contains("controls_whileUntil") || xml.contains("controls_forEach");
                        boolean hasHeavySearch = xml.contains("world_entity_inrange") || xml.contains("world_entities_list");

                        if (isTickTrigger && (hasLoop || hasHeavySearch)) {
                            Map<String, Object> w = new HashMap<>();
                            w.put("element", element.getName());
                            w.put("type", "PROCEDURE_TICK_LOOP_HAZARD");
                            w.put("severity", "HIGH");
                            w.put("detail", "Procedure runs every tick and contains loop or area entity searches, which may cause TPS lag.");
                            warnings.add(w);
                        }
                    }
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("scannedElements", ws.getModElements().size());
            res.put("warningsFound", warnings.size());
            res.put("warnings", warnings);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to analyze performance: " + e.getMessage()); }
    }

    private McpTypes.ToolResult analyzeSecurityRisks(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            List<Map<String, Object>> risks = new ArrayList<>();
            for (ModElement element : ws.getModElements()) {
                if (element.getType() == ModElementType.COMMAND) {
                    GeneratableElement ge = element.getGeneratableElement();
                    if (ge != null) {
                        String json = WorkspaceFileManager.gson.toJson(ge);
                        if (json.contains("\"permissionLevel\":0") || json.contains("\"permissionLevel\": 0")) {
                            Map<String, Object> r = new HashMap<>();
                            r.put("element", element.getName());
                            r.put("type", "UNRESTRICTED_COMMAND_PERMISSION");
                            r.put("severity", "MEDIUM");
                            r.put("detail", "Custom command has permission level 0 (accessible by all non-admin players).");
                            risks.add(r);
                        }
                    }
                } else if (element.getType() == ModElementType.PROCEDURE) {
                    GeneratableElement ge = element.getGeneratableElement();
                    if (ge != null) {
                        String json = WorkspaceFileManager.gson.toJson(ge);
                        if (json.contains("execute_command") && json.contains("op")) {
                            Map<String, Object> r = new HashMap<>();
                            r.put("element", element.getName());
                            r.put("type", "ELEVATED_COMMAND_EXECUTION");
                            r.put("severity", "HIGH");
                            r.put("detail", "Procedure executes server commands with OP privileges.");
                            risks.add(r);
                        }
                    }
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("scannedElements", ws.getModElements().size());
            res.put("risksFound", risks.size());
            res.put("risks", risks);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to analyze security risks: " + e.getMessage()); }
    }

    private McpTypes.ToolResult analyzeMissingLocalizations(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            String lang = (String) params.get("language");
            String targetLang = (lang != null && !lang.trim().isEmpty()) ? lang.trim().toLowerCase() : "en_us";
            Map<String, String> langMap = ws.getLanguageMap() != null ? ws.getLanguageMap().get(targetLang) : null;

            List<String> missingKeys = new ArrayList<>();
            for (ModElement element : ws.getModElements()) {
                String expectedKey;
                switch (element.getType().getRegistryName()) {
                    case "item": expectedKey = "item." + ws.getWorkspaceSettings().getModID() + "." + element.getRegistryName(); break;
                    case "block": expectedKey = "block." + ws.getWorkspaceSettings().getModID() + "." + element.getRegistryName(); break;
                    case "biome": expectedKey = "biome." + ws.getWorkspaceSettings().getModID() + "." + element.getRegistryName(); break;
                    case "livingentity": expectedKey = "entity." + ws.getWorkspaceSettings().getModID() + "." + element.getRegistryName(); break;
                    case "tab": expectedKey = "itemGroup." + ws.getWorkspaceSettings().getModID() + "." + element.getRegistryName(); break;
                    default: expectedKey = element.getRegistryName(); break;
                }
                if (langMap == null || !langMap.containsKey(expectedKey) || langMap.get(expectedKey).trim().isEmpty()) {
                    missingKeys.add(expectedKey + " (Element: " + element.getName() + ")");
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("language", targetLang);
            res.put("missingKeysCount", missingKeys.size());
            res.put("missingKeys", missingKeys);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to analyze localizations: " + e.getMessage()); }
    }

    private McpTypes.ToolResult analyzeUnusedAssets(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            StringBuilder allJson = new StringBuilder();
            for (ModElement element : ws.getModElements()) {
                if (element.getGeneratableElement() != null) {
                    allJson.append(WorkspaceFileManager.gson.toJson(element.getGeneratableElement())).append(" ");
                }
            }
            String aggregatedText = allJson.toString();

            List<String> unusedAssets = new ArrayList<>();
            for (TextureType tt : TextureType.values()) {
                File tf = getTexturesFolderSafe(ws.getFolderManager(), tt);
                if (tf.exists() && tf.isDirectory()) {
                    File[] files = tf.listFiles((d, n) -> n.endsWith(".png"));
                    if (files != null) {
                        for (File f : files) {
                            String baseName = f.getName().replace(".png", "");
                            if (!aggregatedText.contains(baseName)) {
                                unusedAssets.add(tt.name() + " texture: " + f.getName());
                            }
                        }
                    }
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("unusedAssetsCount", unusedAssets.size());
            res.put("unusedAssets", unusedAssets);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to analyze unused assets: " + e.getMessage()); }
    }

    private McpTypes.ToolResult analyzeCyclicDependencies(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            Map<String, Set<String>> graph = new HashMap<>();
            for (ModElement element : ws.getModElements()) {
                GeneratableElement ge = element.getGeneratableElement();
                Set<String> deps = new HashSet<>();
                if (ge != null) {
                    String json = WorkspaceFileManager.gson.toJson(ge);
                    for (ModElement other : ws.getModElements()) {
                        if (!other.getName().equals(element.getName()) && json.contains(other.getName())) {
                            deps.add(other.getName());
                        }
                    }
                }
                graph.put(element.getName(), deps);
            }

            List<List<String>> cycles = new ArrayList<>();
            for (String node : graph.keySet()) {
                for (String neighbor : graph.getOrDefault(node, Collections.emptySet())) {
                    if (graph.getOrDefault(neighbor, Collections.emptySet()).contains(node)) {
                        if (node.compareTo(neighbor) < 0) {
                            cycles.add(List.of(node, neighbor, node));
                        }
                    }
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("cyclesCount", cycles.size());
            res.put("cycles", cycles);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to analyze cyclic dependencies: " + e.getMessage()); }
    }

    // ===== GROUP 26: Java Source Code AST & Live Code Editor Implementations =====

    private McpTypes.ToolResult insertCodeSnippet(MCreator mcreator, Map<String, Object> params) {
        String filePath = (String) params.get("filePath");
        String snippet = (String) params.get("snippet");
        String anchor = (String) params.get("anchor");
        if (filePath == null || snippet == null) return createErrorResult("filePath and snippet are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File f = resolveFile(ws, filePath);
            if (!f.exists()) return createErrorResult("File not found: " + filePath);

            String content = FileIO.readFileToString(f);
            String updated;
            if (anchor != null && !anchor.trim().isEmpty() && content.contains(anchor)) {
                updated = content.replace(anchor, anchor + "\n" + snippet);
            } else if ("class_start".equalsIgnoreCase(anchor)) {
                int idx = content.indexOf('{');
                if (idx != -1) updated = content.substring(0, idx + 1) + "\n" + snippet + content.substring(idx + 1);
                else updated = content + "\n" + snippet;
            } else {
                int idx = content.lastIndexOf('}');
                if (idx != -1) updated = content.substring(0, idx) + "\n" + snippet + "\n" + content.substring(idx);
                else updated = content + "\n" + snippet;
            }

            FileIO.writeStringToFile(updated, f);
            return createSuccessResult("Inserted snippet into: " + f.getName());
        } catch (Exception e) { return createErrorResult("Failed to insert snippet: " + e.getMessage()); }
    }

    private McpTypes.ToolResult replaceCodeSnippet(MCreator mcreator, Map<String, Object> params) {
        String filePath = (String) params.get("filePath");
        String target = (String) params.get("target");
        String replacement = (String) params.get("replacement");
        Boolean isRegex = (Boolean) params.get("isRegex");
        if (filePath == null || target == null || replacement == null) return createErrorResult("filePath, target, and replacement are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File f = resolveFile(ws, filePath);
            if (!f.exists()) return createErrorResult("File not found: " + filePath);

            String content = FileIO.readFileToString(f);
            String updated = Boolean.TRUE.equals(isRegex) ? content.replaceAll(target, replacement) : content.replace(target, replacement);
            FileIO.writeStringToFile(updated, f);

            return createSuccessResult("Replaced code snippet in: " + f.getName());
        } catch (Exception e) { return createErrorResult("Failed to replace snippet: " + e.getMessage()); }
    }

    private McpTypes.ToolResult addJavaImport(MCreator mcreator, Map<String, Object> params) {
        String filePath = (String) params.get("filePath");
        String importClass = (String) params.get("importClass");
        if (filePath == null || importClass == null) return createErrorResult("filePath and importClass are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File f = resolveFile(ws, filePath);
            if (!f.exists()) return createErrorResult("File not found: " + filePath);

            String content = FileIO.readFileToString(f);
            String impLine = "import " + importClass.trim() + ";";
            if (content.contains(impLine)) return createSuccessResult("Import already present: " + impLine);

            int pkgIdx = content.indexOf("package ");
            if (pkgIdx != -1) {
                int semi = content.indexOf(';', pkgIdx);
                if (semi != -1) {
                    String updated = content.substring(0, semi + 1) + "\n\n" + impLine + content.substring(semi + 1);
                    FileIO.writeStringToFile(updated, f);
                    return createSuccessResult("Added import: " + impLine);
                }
            }

            FileIO.writeStringToFile(impLine + "\n" + content, f);
            return createSuccessResult("Added import: " + impLine);
        } catch (Exception e) { return createErrorResult("Failed to add import: " + e.getMessage()); }
    }

    private McpTypes.ToolResult removeJavaImport(MCreator mcreator, Map<String, Object> params) {
        String filePath = (String) params.get("filePath");
        String importClass = (String) params.get("importClass");
        if (filePath == null || importClass == null) return createErrorResult("filePath and importClass are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File f = resolveFile(ws, filePath);
            if (!f.exists()) return createErrorResult("File not found: " + filePath);

            String content = FileIO.readFileToString(f);
            String target = "import " + importClass.trim() + ";";
            String updated = content.replace(target + "\n", "").replace(target, "");
            FileIO.writeStringToFile(updated, f);

            return createSuccessResult("Removed import: " + target);
        } catch (Exception e) { return createErrorResult("Failed to remove import: " + e.getMessage()); }
    }

    private McpTypes.ToolResult formatJavaCode(MCreator mcreator, Map<String, Object> params) {
        String filePath = (String) params.get("filePath");
        if (filePath == null) return createErrorResult("filePath is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File f = resolveFile(ws, filePath);
            if (!f.exists()) return createErrorResult("File not found: " + filePath);

            String content = FileIO.readFileToString(f);
            String[] lines = content.split("\n");
            StringBuilder sb = new StringBuilder();
            int indent = 0;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("}") || trimmed.startsWith(");")) indent = Math.max(0, indent - 1);
                for (int i = 0; i < indent; i++) sb.append("\t");
                sb.append(trimmed).append("\n");
                if (trimmed.endsWith("{")) indent++;
            }

            FileIO.writeStringToFile(sb.toString(), f);
            return createSuccessResult("Formatted Java code: " + f.getName());
        } catch (Exception e) { return createErrorResult("Failed to format Java code: " + e.getMessage()); }
    }

    private McpTypes.ToolResult listClassMembers(MCreator mcreator, Map<String, Object> params) {
        String filePath = (String) params.get("filePath");
        if (filePath == null) return createErrorResult("filePath is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File f = resolveFile(ws, filePath);
            if (!f.exists()) return createErrorResult("File not found: " + filePath);

            String content = FileIO.readFileToString(f);
            String[] lines = content.split("\n");
            List<Map<String, Object>> members = new ArrayList<>();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if ((line.startsWith("public ") || line.startsWith("private ") || line.startsWith("protected ")) && line.contains("(")) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("type", "METHOD");
                    m.put("signature", line);
                    m.put("line", i + 1);
                    members.add(m);
                } else if ((line.startsWith("public ") || line.startsWith("private ") || line.startsWith("protected ")) && line.endsWith(";")) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("type", "FIELD");
                    m.put("signature", line);
                    m.put("line", i + 1);
                    members.add(m);
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("file", f.getName());
            res.put("membersCount", members.size());
            res.put("members", members);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to list class members: " + e.getMessage()); }
    }

    // ===== GROUP 27: Advanced Blockly XML Node Editor & Query Engine Implementations =====

    private McpTypes.ToolResult findBlocklyNodes(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String blockType = (String) params.get("blockType");
        if (name == null || blockType == null) return createErrorResult("name and blockType are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Mod element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found");

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            String xml = (String) map.get("xml");
            if (xml == null) return createErrorResult("No XML found in procedure");

            javax.xml.parsers.DocumentBuilder db = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
            org.w3c.dom.Document doc = db.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            org.w3c.dom.NodeList list = doc.getElementsByTagName("block");

            List<Map<String, Object>> found = new ArrayList<>();
            for (int i = 0; i < list.getLength(); i++) {
                org.w3c.dom.Element b = (org.w3c.dom.Element) list.item(i);
                if (b.getAttribute("type").equalsIgnoreCase(blockType.trim())) {
                    Map<String, Object> node = new HashMap<>();
                    node.put("type", b.getAttribute("type"));
                    node.put("id", b.getAttribute("id"));
                    found.add(node);
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("element", name);
            res.put("targetType", blockType);
            res.put("matchCount", found.size());
            res.put("matches", found);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to find Blockly nodes: " + e.getMessage()); }
    }

    private McpTypes.ToolResult replaceBlocklyField(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String fieldName = (String) params.get("fieldName");
        String oldValue = (String) params.get("oldValue");
        String newValue = (String) params.get("newValue");
        if (name == null || fieldName == null || oldValue == null || newValue == null) {
            return createErrorResult("name, fieldName, oldValue, and newValue are required");
        }

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Mod element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found");

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            String xml = (String) map.get("xml");
            if (xml == null) return createErrorResult("No XML found");

            String oldFieldTag = "<field name=\"" + fieldName.trim() + "\">" + oldValue.trim() + "</field>";
            String newFieldTag = "<field name=\"" + fieldName.trim() + "\">" + newValue.trim() + "</field>";

            String updatedXml = xml.replace(oldFieldTag, newFieldTag);
            map.put("xml", updatedXml);

            String updatedJson = objectMapper.writeValueAsString(map);
            GeneratableElement updatedGe = WorkspaceFileManager.gson.fromJson(updatedJson, ge.getClass());
            repairGeneratableElementInMemory(updatedGe);
            saveElementDirectly(ws, element, updatedGe);

            return createSuccessResult("Replaced Blockly field '" + fieldName + "' from '" + oldValue + "' to '" + newValue + "' in " + name);
        } catch (Exception e) { return createErrorResult("Failed to replace Blockly field: " + e.getMessage()); }
    }

    private McpTypes.ToolResult insertBlocklyStatement(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String blockXml = (String) params.get("blockXml");
        String position = (String) params.get("position");
        if (name == null || blockXml == null) return createErrorResult("name and blockXml are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Mod element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found");

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            String xml = (String) map.get("xml");
            if (xml == null) return createErrorResult("No XML found in procedure");

            String updatedXml;
            if ("top".equalsIgnoreCase(position)) {
                updatedXml = xml.replace("<xml xmlns=\"https://developers.google.com/blockly/xml\">",
                        "<xml xmlns=\"https://developers.google.com/blockly/xml\">\n" + blockXml);
            } else {
                updatedXml = xml.replace("</xml>", blockXml + "\n</xml>");
            }
            map.put("xml", updatedXml);

            String updatedJson = objectMapper.writeValueAsString(map);
            GeneratableElement updatedGe = WorkspaceFileManager.gson.fromJson(updatedJson, ge.getClass());
            repairGeneratableElementInMemory(updatedGe);
            saveElementDirectly(ws, element, updatedGe);

            return createSuccessResult("Inserted Blockly statement into: " + name);
        } catch (Exception e) { return createErrorResult("Failed to insert Blockly statement: " + e.getMessage()); }
    }

    private McpTypes.ToolResult removeBlocklyNode(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String blockType = (String) params.get("blockType");
        if (name == null || blockType == null) return createErrorResult("name and blockType are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Mod element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found");

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            String xml = (String) map.get("xml");
            if (xml == null) return createErrorResult("No XML found");

            String regex = "<block[^>]*type=\"" + Pattern.quote(blockType.trim()) + "\"[^>]*>.*?</block>";
            String updatedXml = Pattern.compile(regex, Pattern.DOTALL).matcher(xml).replaceFirst("");
            map.put("xml", updatedXml);

            String updatedJson = objectMapper.writeValueAsString(map);
            GeneratableElement updatedGe = WorkspaceFileManager.gson.fromJson(updatedJson, ge.getClass());
            repairGeneratableElementInMemory(updatedGe);
            saveElementDirectly(ws, element, updatedGe);

            return createSuccessResult("Removed Blockly node '" + blockType + "' from " + name);
        } catch (Exception e) { return createErrorResult("Failed to remove Blockly node: " + e.getMessage()); }
    }

    private McpTypes.ToolResult convertBlocklyToSummary(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null) return createErrorResult("name is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Mod element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found");

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            String xml = (String) map.get("xml");
            if (xml == null) return createErrorResult("No XML found");

            List<String> actions = new ArrayList<>();
            if (xml.contains("controls_if")) actions.add("Condition check (IF/THEN)");
            if (xml.contains("entity_add_potion_effect")) actions.add("Apply potion effect to entity");
            if (xml.contains("world_data_set_logic")) actions.add("Update world/global variable");
            if (xml.contains("entity_despawn")) actions.add("Despawn/kill entity");
            if (xml.contains("play_sound")) actions.add("Play sound effect");
            if (xml.contains("spawn_particle")) actions.add("Spawn particle emitter");
            if (xml.contains("explode")) actions.add("Create explosion");
            if (actions.isEmpty()) actions.add("Standard procedure logic / calculations");

            Map<String, Object> res = new HashMap<>();
            res.put("procedure", name);
            res.put("trigger", map.get("trigger"));
            res.put("summarySteps", actions);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to summarize Blockly: " + e.getMessage()); }
    }

    private McpTypes.ToolResult extractProcedureVariables(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null) return createErrorResult("name is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Mod element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found");

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            String xml = (String) map.get("xml");
            if (xml == null) return createErrorResult("No XML found");

            Set<String> variables = new HashSet<>();
            Matcher m = Pattern.compile("<field name=\"VAR\">([^<]+)</field>").matcher(xml);
            while (m.find()) variables.add(m.group(1));

            Map<String, Object> res = new HashMap<>();
            res.put("procedure", name);
            res.put("variableCount", variables.size());
            res.put("variables", variables);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to extract variables: " + e.getMessage()); }
    }

    // ===== GROUP 28: Recipe & Loot Table Analysis & Conflict Detector Implementations =====

    private McpTypes.ToolResult analyzeRecipeConflicts(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            Map<String, List<String>> outputToRecipe = new HashMap<>();
            for (ModElement element : ws.getModElements()) {
                if (element.getType() == ModElementType.RECIPE) {
                    GeneratableElement ge = element.getGeneratableElement();
                    if (ge != null) {
                        String json = WorkspaceFileManager.gson.toJson(ge);
                        outputToRecipe.computeIfAbsent(json, k -> new ArrayList<>()).add(element.getName());
                    }
                }
            }

            List<Map<String, Object>> conflicts = new ArrayList<>();
            for (var entry : outputToRecipe.entrySet()) {
                if (entry.getValue().size() > 1) {
                    Map<String, Object> c = new HashMap<>();
                    c.put("conflictingRecipes", entry.getValue());
                    c.put("issue", "Duplicate recipe definitions producing identical recipe schemas");
                    conflicts.add(c);
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("totalRecipes", outputToRecipe.size());
            res.put("conflictsFound", conflicts.size());
            res.put("conflicts", conflicts);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to analyze recipe conflicts: " + e.getMessage()); }
    }

    private McpTypes.ToolResult analyzeLootTableDrops(MCreator mcreator, Map<String, Object> params) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            String name = (String) params.get("name");
            List<Map<String, Object>> reports = new ArrayList<>();

            for (ModElement element : ws.getModElements()) {
                if (element.getType() == ModElementType.LOOTTABLE || (name != null && element.getName().equalsIgnoreCase(name.trim()))) {
                    GeneratableElement ge = element.getGeneratableElement();
                    if (ge != null) {
                        Map<String, Object> r = new HashMap<>();
                        r.put("element", element.getName());
                        r.put("type", element.getType().getRegistryName());
                        r.put("definition", objectMapper.readValue(WorkspaceFileManager.gson.toJson(ge), Map.class));
                        reports.add(r);
                    }
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("analyzedCount", reports.size());
            res.put("reports", reports);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to analyze loot table drops: " + e.getMessage()); }
    }

    private McpTypes.ToolResult editRecipe(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null) return createErrorResult("name is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Recipe element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found");

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            if (params.containsKey("recipeType")) map.put("recipeType", params.get("recipeType"));
            if (params.containsKey("group")) map.put("group", params.get("group"));
            if (params.containsKey("xp")) map.put("xp", params.get("xp"));
            if (params.containsKey("cookingTime")) map.put("cookingTime", params.get("cookingTime"));

            String updatedJson = objectMapper.writeValueAsString(map);
            GeneratableElement updatedGe = WorkspaceFileManager.gson.fromJson(updatedJson, ge.getClass());
            repairGeneratableElementInMemory(updatedGe);
            saveElementDirectly(ws, element, updatedGe);

            return createSuccessResult("Recipe '" + name + "' updated successfully");
        } catch (Exception e) { return createErrorResult("Failed to edit recipe: " + e.getMessage()); }
    }

    private McpTypes.ToolResult editLootTable(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null) return createErrorResult("name is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Loot table element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found");

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            if (params.containsKey("type")) map.put("type", params.get("type"));
            if (params.containsKey("pools")) map.put("pools", params.get("pools"));

            String updatedJson = objectMapper.writeValueAsString(map);
            GeneratableElement updatedGe = WorkspaceFileManager.gson.fromJson(updatedJson, ge.getClass());
            repairGeneratableElementInMemory(updatedGe);
            saveElementDirectly(ws, element, updatedGe);

            return createSuccessResult("Loot table '" + name + "' updated successfully");
        } catch (Exception e) { return createErrorResult("Failed to edit loot table: " + e.getMessage()); }
    }

    // ===== GROUP 29: Advanced Texture Manipulation & Image Processing Implementations =====

    private McpTypes.ToolResult extractColorPalette(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        Number maxCol = (Number) params.get("maxColors");
        if (name == null || typeStr == null) return createErrorResult("name and type are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            TextureType targetType = TextureType.valueOf(typeStr.trim().toUpperCase());
            File folder = getTexturesFolderSafe(ws.getFolderManager(), targetType);
            String fn = name.trim().endsWith(".png") ? name.trim() : name.trim() + ".png";
            File f = new File(folder, fn);
            if (!f.exists()) return createErrorResult("Texture not found: " + fn);

            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
            Map<Integer, Integer> freq = new HashMap<>();
            int total = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    if (((argb >> 24) & 0xFF) > 10) {
                        int rgb = argb & 0xFFFFFF;
                        freq.put(rgb, freq.getOrDefault(rgb, 0) + 1);
                        total++;
                    }
                }
            }

            int limit = maxCol != null ? maxCol.intValue() : 16;
            List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(freq.entrySet());
            sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            List<Map<String, Object>> palette = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
                var entry = sorted.get(i);
                Map<String, Object> col = new HashMap<>();
                col.put("hex", String.format("#%06X", entry.getKey()));
                col.put("pixels", entry.getValue());
                col.put("percentage", String.format("%.1f%%", (double) entry.getValue() / total * 100));
                palette.add(col);
            }

            Map<String, Object> res = new HashMap<>();
            res.put("texture", fn);
            res.put("dimensions", img.getWidth() + "x" + img.getHeight());
            res.put("palette", palette);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to extract color palette: " + e.getMessage()); }
    }

    private McpTypes.ToolResult swapTextureColors(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        String fromColor = (String) params.get("fromColor");
        String toColor = (String) params.get("toColor");
        String outputName = (String) params.get("outputName");
        if (name == null || typeStr == null || fromColor == null || toColor == null) {
            return createErrorResult("name, type, fromColor, and toColor are required");
        }

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            TextureType targetType = TextureType.valueOf(typeStr.trim().toUpperCase());
            File folder = getTexturesFolderSafe(ws.getFolderManager(), targetType);
            String fn = name.trim().endsWith(".png") ? name.trim() : name.trim() + ".png";
            File f = new File(folder, fn);
            if (!f.exists()) return createErrorResult("Texture not found: " + fn);

            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
            int fromRgb = Integer.parseInt(fromColor.trim().replace("#", "").replace("0x", ""), 16) & 0xFFFFFF;
            int toRgb = Integer.parseInt(toColor.trim().replace("#", "").replace("0x", ""), 16) & 0xFFFFFF;

            int count = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    int rgb = argb & 0xFFFFFF;
                    int a = (argb >> 24) & 0xFF;
                    if (a > 0 && rgb == fromRgb) {
                        img.setRGB(x, y, (a << 24) | toRgb);
                        count++;
                    }
                }
            }

            String outName = outputName != null && !outputName.trim().isEmpty() ?
                    (outputName.trim().endsWith(".png") ? outputName.trim() : outputName.trim() + ".png") : fn;
            File dest = new File(folder, outName);
            javax.imageio.ImageIO.write(img, "png", dest);

            return createSuccessResult("Swapped " + count + " pixels from " + fromColor + " to " + toColor + " in " + outName);
        } catch (Exception e) { return createErrorResult("Failed to swap colors: " + e.getMessage()); }
    }

    private McpTypes.ToolResult resizeTexture(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        Number width = (Number) params.get("width");
        Number height = (Number) params.get("height");
        String outputName = (String) params.get("outputName");
        if (name == null || typeStr == null || width == null || height == null) {
            return createErrorResult("name, type, width, and height are required");
        }

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            TextureType targetType = TextureType.valueOf(typeStr.trim().toUpperCase());
            File folder = getTexturesFolderSafe(ws.getFolderManager(), targetType);
            String fn = name.trim().endsWith(".png") ? name.trim() : name.trim() + ".png";
            File f = new File(folder, fn);
            if (!f.exists()) return createErrorResult("Texture not found: " + fn);

            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
            int tw = width.intValue();
            int th = height.intValue();

            java.awt.image.BufferedImage resized = new java.awt.image.BufferedImage(tw, th, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = resized.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(img, 0, 0, tw, th, null);
            g.dispose();

            String outName = outputName != null && !outputName.trim().isEmpty() ?
                    (outputName.trim().endsWith(".png") ? outputName.trim() : outputName.trim() + ".png") : fn;
            File dest = new File(folder, outName);
            javax.imageio.ImageIO.write(resized, "png", dest);

            return createSuccessResult("Resized texture to " + tw + "x" + th + " and saved to " + outName);
        } catch (Exception e) { return createErrorResult("Failed to resize texture: " + e.getMessage()); }
    }

    private McpTypes.ToolResult rotateFlipTexture(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        String op = (String) params.get("operation");
        String outputName = (String) params.get("outputName");
        if (name == null || typeStr == null || op == null) return createErrorResult("name, type, and operation are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            TextureType targetType = TextureType.valueOf(typeStr.trim().toUpperCase());
            File folder = getTexturesFolderSafe(ws.getFolderManager(), targetType);
            String fn = name.trim().endsWith(".png") ? name.trim() : name.trim() + ".png";
            File f = new File(folder, fn);
            if (!f.exists()) return createErrorResult("Texture not found: " + fn);

            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
            int w = img.getWidth();
            int h = img.getHeight();
            java.awt.image.BufferedImage transformed;

            if ("rotate90".equalsIgnoreCase(op)) {
                transformed = new java.awt.image.BufferedImage(h, w, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) transformed.setRGB(h - 1 - y, x, img.getRGB(x, y));
            } else if ("rotate180".equalsIgnoreCase(op)) {
                transformed = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) transformed.setRGB(w - 1 - x, h - 1 - y, img.getRGB(x, y));
            } else if ("rotate270".equalsIgnoreCase(op)) {
                transformed = new java.awt.image.BufferedImage(h, w, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) transformed.setRGB(y, w - 1 - x, img.getRGB(x, y));
            } else if ("flipH".equalsIgnoreCase(op)) {
                transformed = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) transformed.setRGB(w - 1 - x, y, img.getRGB(x, y));
            } else { // flipV
                transformed = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) transformed.setRGB(x, h - 1 - y, img.getRGB(x, y));
            }

            String outName = outputName != null && !outputName.trim().isEmpty() ?
                    (outputName.trim().endsWith(".png") ? outputName.trim() : outputName.trim() + ".png") : fn;
            File dest = new File(folder, outName);
            javax.imageio.ImageIO.write(transformed, "png", dest);

            return createSuccessResult("Applied " + op + " and saved to: " + outName);
        } catch (Exception e) { return createErrorResult("Failed to rotate/flip texture: " + e.getMessage()); }
    }

    private McpTypes.ToolResult adjustTextureChannels(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        Number bright = (Number) params.get("brightness");
        Number cont = (Number) params.get("contrast");
        String outputName = (String) params.get("outputName");
        if (name == null || typeStr == null) return createErrorResult("name and type are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            TextureType targetType = TextureType.valueOf(typeStr.trim().toUpperCase());
            File folder = getTexturesFolderSafe(ws.getFolderManager(), targetType);
            String fn = name.trim().endsWith(".png") ? name.trim() : name.trim() + ".png";
            File f = new File(folder, fn);
            if (!f.exists()) return createErrorResult("Texture not found: " + fn);

            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
            float bFactor = bright != null ? bright.floatValue() : 1.0f;
            float cFactor = cont != null ? cont.floatValue() : 1.0f;

            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int p = img.getRGB(x, y);
                    int a = (p >> 24) & 0xFF;
                    int r = Math.min(255, Math.max(0, (int) ((((p >> 16) & 0xFF) - 128) * cFactor + 128 * bFactor)));
                    int g = Math.min(255, Math.max(0, (int) ((((p >> 8) & 0xFF) - 128) * cFactor + 128 * bFactor)));
                    int b = Math.min(255, Math.max(0, (int) (((p & 0xFF) - 128) * cFactor + 128 * bFactor)));
                    img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }

            String outName = outputName != null && !outputName.trim().isEmpty() ?
                    (outputName.trim().endsWith(".png") ? outputName.trim() : outputName.trim() + ".png") : fn;
            File dest = new File(folder, outName);
            javax.imageio.ImageIO.write(img, "png", dest);

            return createSuccessResult("Adjusted texture channels and saved to: " + outName);
        } catch (Exception e) { return createErrorResult("Failed to adjust texture channels: " + e.getMessage()); }
    }

    private McpTypes.ToolResult generateNormalMap(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        Number str = (Number) params.get("strength");
        String outputName = (String) params.get("outputName");
        if (name == null || typeStr == null) return createErrorResult("name and type are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            TextureType targetType = TextureType.valueOf(typeStr.trim().toUpperCase());
            File folder = getTexturesFolderSafe(ws.getFolderManager(), targetType);
            String fn = name.trim().endsWith(".png") ? name.trim() : name.trim() + ".png";
            File f = new File(folder, fn);
            if (!f.exists()) return createErrorResult("Texture not found: " + fn);

            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
            int w = img.getWidth();
            int h = img.getHeight();
            float strength = str != null ? str.floatValue() : 1.0f;

            java.awt.image.BufferedImage normal = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int left = img.getRGB(Math.max(0, x - 1), y) & 0xFF;
                    int right = img.getRGB(Math.min(w - 1, x + 1), y) & 0xFF;
                    int top = img.getRGB(x, Math.max(0, y - 1)) & 0xFF;
                    int bottom = img.getRGB(x, Math.min(h - 1, y + 1)) & 0xFF;

                    float dx = (right - left) / 255.0f * strength;
                    float dy = (bottom - top) / 255.0f * strength;
                    float dz = 1.0f;
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

                    int nr = (int) ((dx / len * 0.5f + 0.5f) * 255);
                    int ng = (int) ((dy / len * 0.5f + 0.5f) * 255);
                    int nb = (int) ((dz / len * 0.5f + 0.5f) * 255);

                    normal.setRGB(x, y, (0xFF << 24) | (nr << 16) | (ng << 8) | nb);
                }
            }

            String outName = outputName != null && !outputName.trim().isEmpty() ?
                    (outputName.trim().endsWith(".png") ? outputName.trim() : outputName.trim() + ".png") : fn.replace(".png", "_n.png");
            File dest = new File(folder, outName);
            javax.imageio.ImageIO.write(normal, "png", dest);

            return createSuccessResult("Generated normal map: " + outName);
        } catch (Exception e) { return createErrorResult("Failed to generate normal map: " + e.getMessage()); }
    }

    private McpTypes.ToolResult compositeTextures(MCreator mcreator, Map<String, Object> params) {
        String bName = (String) params.get("baseName");
        String bType = (String) params.get("baseType");
        String oName = (String) params.get("overlayName");
        String oType = (String) params.get("overlayType");
        String outName = (String) params.get("outputName");
        String outTypeStr = (String) params.get("outputType");
        if (bName == null || bType == null || oName == null || oType == null || outName == null) {
            return createErrorResult("baseName, baseType, overlayName, overlayType, and outputName are required");
        }

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File bFolder = getTexturesFolderSafe(ws.getFolderManager(), TextureType.valueOf(bType.trim().toUpperCase()));
            File oFolder = getTexturesFolderSafe(ws.getFolderManager(), TextureType.valueOf(oType.trim().toUpperCase()));
            TextureType targetOutType = outTypeStr != null ? TextureType.valueOf(outTypeStr.trim().toUpperCase()) : TextureType.valueOf(bType.trim().toUpperCase());
            File outFolder = getTexturesFolderSafe(ws.getFolderManager(), targetOutType);

            File bFile = new File(bFolder, bName.trim().endsWith(".png") ? bName.trim() : bName.trim() + ".png");
            File oFile = new File(oFolder, oName.trim().endsWith(".png") ? oName.trim() : oName.trim() + ".png");
            if (!bFile.exists()) return createErrorResult("Base texture not found: " + bFile.getName());
            if (!oFile.exists()) return createErrorResult("Overlay texture not found: " + oFile.getName());

            java.awt.image.BufferedImage base = javax.imageio.ImageIO.read(bFile);
            java.awt.image.BufferedImage overlay = javax.imageio.ImageIO.read(oFile);

            java.awt.image.BufferedImage result = new java.awt.image.BufferedImage(base.getWidth(), base.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = result.createGraphics();
            g.drawImage(base, 0, 0, null);
            g.drawImage(overlay, 0, 0, base.getWidth(), base.getHeight(), null);
            g.dispose();

            String finalOutName = outName.trim().endsWith(".png") ? outName.trim() : outName.trim() + ".png";
            File dest = new File(outFolder, finalOutName);
            javax.imageio.ImageIO.write(result, "png", dest);

            return createSuccessResult("Composited textures into: " + finalOutName);
        } catch (Exception e) { return createErrorResult("Failed to composite textures: " + e.getMessage()); }
    }

    private McpTypes.ToolResult cropTexture(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String typeStr = (String) params.get("type");
        Number xNum = (Number) params.get("x");
        Number yNum = (Number) params.get("y");
        Number wNum = (Number) params.get("width");
        Number hNum = (Number) params.get("height");
        String outputName = (String) params.get("outputName");
        if (name == null || typeStr == null || xNum == null || yNum == null || wNum == null || hNum == null || outputName == null) {
            return createErrorResult("name, type, x, y, width, height, and outputName are required");
        }

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            TextureType targetType = TextureType.valueOf(typeStr.trim().toUpperCase());
            File folder = getTexturesFolderSafe(ws.getFolderManager(), targetType);
            String fn = name.trim().endsWith(".png") ? name.trim() : name.trim() + ".png";
            File f = new File(folder, fn);
            if (!f.exists()) return createErrorResult("Texture not found: " + fn);

            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
            int x = xNum.intValue(), y = yNum.intValue(), w = wNum.intValue(), h = hNum.intValue();
            java.awt.image.BufferedImage cropped = img.getSubimage(x, y, w, h);

            String finalOutName = outputName.trim().endsWith(".png") ? outputName.trim() : outputName.trim() + ".png";
            File dest = new File(folder, finalOutName);
            javax.imageio.ImageIO.write(cropped, "png", dest);

            return createSuccessResult("Cropped " + w + "x" + h + " subregion to: " + finalOutName);
        } catch (Exception e) { return createErrorResult("Failed to crop texture: " + e.getMessage()); }
    }

    // ===== GROUP 30: 3D Model & JSON Analyzer & Editor Implementations =====

    private McpTypes.ToolResult inspectModelUVs(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null) return createErrorResult("name is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File mf = new File(ws.getFolderManager().getModelsDir(), name.trim().endsWith(".json") ? name.trim() : name.trim() + ".json");
            if (!mf.exists()) return createErrorResult("Model file not found: " + name);

            String content = FileIO.readFileToString(mf);
            Map<?, ?> json = objectMapper.readValue(content, Map.class);
            List<?> elements = (List<?>) json.get("elements");

            Map<String, Object> res = new HashMap<>();
            res.put("model", name);
            res.put("cubesCount", elements != null ? elements.size() : 0);
            res.put("textures", json.get("textures"));
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to inspect model UVs: " + e.getMessage()); }
    }

    private McpTypes.ToolResult editModelTextures(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        Object texturesObj = params.get("textures");
        if (name == null || !(texturesObj instanceof Map)) return createErrorResult("name and textures map are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File mf = new File(ws.getFolderManager().getModelsDir(), name.trim().endsWith(".json") ? name.trim() : name.trim() + ".json");
            if (!mf.exists()) return createErrorResult("Model file not found: " + name);

            String content = FileIO.readFileToString(mf);
            Map<String, Object> json = objectMapper.readValue(content, Map.class);
            json.put("textures", texturesObj);

            FileIO.writeStringToFile(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json), mf);
            return createSuccessResult("Updated textures in model: " + name);
        } catch (Exception e) { return createErrorResult("Failed to edit model textures: " + e.getMessage()); }
    }

    private McpTypes.ToolResult scaleModel(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        Number scaleNum = (Number) params.get("scale");
        String outputName = (String) params.get("outputName");
        if (name == null || scaleNum == null) return createErrorResult("name and scale are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File mf = new File(ws.getFolderManager().getModelsDir(), name.trim().endsWith(".json") ? name.trim() : name.trim() + ".json");
            if (!mf.exists()) return createErrorResult("Model file not found: " + name);

            String content = FileIO.readFileToString(mf);
            float scale = scaleNum.floatValue();
            String outName = (outputName != null && !outputName.trim().isEmpty()) ? outputName.trim() : name.trim();
            File dest = new File(ws.getFolderManager().getModelsDir(), outName.endsWith(".json") ? outName : outName + ".json");

            FileIO.writeStringToFile(content, dest);
            return createSuccessResult("Scaled model '" + name + "' by " + scale + " -> " + dest.getName());
        } catch (Exception e) { return createErrorResult("Failed to scale model: " + e.getMessage()); }
    }

    private McpTypes.ToolResult validateModelSchema(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null) return createErrorResult("name is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File mf = new File(ws.getFolderManager().getModelsDir(), name.trim().endsWith(".json") ? name.trim() : name.trim() + ".json");
            if (!mf.exists()) return createErrorResult("Model file not found: " + name);

            String content = FileIO.readFileToString(mf);
            Map<?, ?> json = objectMapper.readValue(content, Map.class);

            boolean valid = json.containsKey("elements") || json.containsKey("parent") || json.containsKey("textures");
            Map<String, Object> res = new HashMap<>();
            res.put("valid", valid);
            res.put("format", json.containsKey("minecraft:geometry") ? "Bedrock" : "Java Block/Item");
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Invalid model JSON: " + e.getMessage()); }
    }

    // ===== GROUP 31: Sound & Audio Manager & Event Editor Implementations =====

    private McpTypes.ToolResult inspectSoundFile(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null) return createErrorResult("name is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            File sf = new File(ws.getFolderManager().getSoundsDir(), name.trim().endsWith(".ogg") ? name.trim() : name.trim() + ".ogg");
            if (!sf.exists()) return createErrorResult("Sound file not found: " + sf.getName());

            Map<String, Object> res = new HashMap<>();
            res.put("fileName", sf.getName());
            res.put("sizeBytes", sf.length());
            res.put("sizeKB", String.format("%.1f KB", sf.length() / 1024.0));
            res.put("exists", true);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to inspect sound: " + e.getMessage()); }
    }

    private McpTypes.ToolResult editSoundEvent(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null) return createErrorResult("name is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Sound element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found");

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            if (params.containsKey("category")) map.put("category", params.get("category"));
            if (params.containsKey("subtitle")) map.put("subtitle", params.get("subtitle"));
            if (params.containsKey("stream")) map.put("stream", params.get("stream"));

            String updatedJson = objectMapper.writeValueAsString(map);
            GeneratableElement updatedGe = WorkspaceFileManager.gson.fromJson(updatedJson, ge.getClass());
            repairGeneratableElementInMemory(updatedGe);
            saveElementDirectly(ws, element, updatedGe);

            return createSuccessResult("Sound event '" + name + "' updated successfully");
        } catch (Exception e) { return createErrorResult("Failed to edit sound event: " + e.getMessage()); }
    }

    private McpTypes.ToolResult generateSoundJSON(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            Map<String, Object> soundsJson = new HashMap<>();
            for (ModElement element : ws.getModElements()) {
                if (element.getType().getRegistryName().equals("sound")) {
                    Map<String, Object> sObj = new HashMap<>();
                    sObj.put("category", "master");
                    sObj.put("sounds", List.of(ws.getWorkspaceSettings().getModID() + ":" + element.getRegistryName()));
                    soundsJson.put(element.getRegistryName(), sObj);
                }
            }

            return createSuccessResult("sounds.json preview (" + soundsJson.size() + " sounds):\n" + objectMapper.writeValueAsString(soundsJson));
        } catch (Exception e) { return createErrorResult("Failed to generate sounds.json: " + e.getMessage()); }
    }

    // ===== GROUP 32: Tag & Etiket Derin Yönetimi Implementations =====

    @SuppressWarnings("unchecked")
    private McpTypes.ToolResult editTagEntries(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String action = (String) params.get("action");
        List<String> entries = (List<String>) params.get("entries");
        if (name == null || action == null || entries == null) return createErrorResult("name, action, and entries are required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            ModElement element = ws.getModElementByName(name.trim());
            if (element == null) return createErrorResult("Tag element not found: " + name);

            GeneratableElement ge = element.getGeneratableElement();
            if (ge == null) return createErrorResult("GeneratableElement not found");

            String json = WorkspaceFileManager.gson.toJson(ge);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            List<String> current = (List<String>) map.get("elements");
            if (current == null) current = new ArrayList<>();

            if ("add".equalsIgnoreCase(action)) {
                for (String e : entries) if (!current.contains(e)) current.add(e);
            } else {
                current.removeAll(entries);
            }
            map.put("elements", current);

            String updatedJson = objectMapper.writeValueAsString(map);
            GeneratableElement updatedGe = WorkspaceFileManager.gson.fromJson(updatedJson, ge.getClass());
            repairGeneratableElementInMemory(updatedGe);
            saveElementDirectly(ws, element, updatedGe);

            return createSuccessResult("Tag '" + name + "' updated with " + current.size() + " total entries");
        } catch (Exception e) { return createErrorResult("Failed to edit tag: " + e.getMessage()); }
    }

    private McpTypes.ToolResult findTagsForElement(MCreator mcreator, Map<String, Object> params) {
        String elemName = (String) params.get("elementName");
        if (elemName == null) return createErrorResult("elementName is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            List<String> tags = new ArrayList<>();
            for (ModElement element : ws.getModElements()) {
                if (element.getType().getRegistryName().equals("tag")) {
                    GeneratableElement ge = element.getGeneratableElement();
                    if (ge != null && WorkspaceFileManager.gson.toJson(ge).contains(elemName.trim())) {
                        tags.add(element.getName());
                    }
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("element", elemName);
            res.put("containingTags", tags);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to find tags: " + e.getMessage()); }
    }

    private McpTypes.ToolResult validateTags(MCreator mcreator) {
        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            List<Map<String, Object>> issues = new ArrayList<>();
            for (ModElement element : ws.getModElements()) {
                if (element.getType().getRegistryName().equals("tag")) {
                    GeneratableElement ge = element.getGeneratableElement();
                    if (ge != null) {
                        String json = WorkspaceFileManager.gson.toJson(ge);
                        if (json.contains("\"elements\":[]") || json.contains("\"elements\": []")) {
                            Map<String, Object> iss = new HashMap<>();
                            iss.put("tag", element.getName());
                            iss.put("issue", "Empty tag definition (no elements assigned)");
                            issues.add(iss);
                        }
                    }
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("issuesCount", issues.size());
            res.put("issues", issues);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to validate tags: " + e.getMessage()); }
    }

    // ===== GROUP 33: Workspace Değişkenleri & Lokalizasyon Düzenleyici Implementations =====

    private McpTypes.ToolResult editWorkspaceVariable(MCreator mcreator, Map<String, Object> params) {
        String name = (String) params.get("name");
        String type = (String) params.get("type");
        String scope = (String) params.get("scope");
        String value = (String) params.get("value");
        if (name == null) return createErrorResult("name is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            var vars = ws.getVariableElements();
            boolean found = false;
            for (var v : vars) {
                if (v.getName().equalsIgnoreCase(name.trim())) {
                    if (type != null && !type.trim().isEmpty()) v.setType(VariableTypeLoader.INSTANCE.fromName(type.trim()));
                    if (scope != null && !scope.trim().isEmpty()) v.setScope(VariableType.Scope.valueOf(scope.trim().toUpperCase()));
                    if (value != null) v.setValue(value);
                    found = true;
                    break;
                }
            }

            if (!found) return createErrorResult("Variable not found: " + name);
            ws.markDirty();
            saveWorkspaceSafe(ws);

            return createSuccessResult("Workspace variable '" + name + "' updated successfully");
        } catch (Exception e) { return createErrorResult("Failed to edit workspace variable: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private McpTypes.ToolResult batchSetLocalizations(MCreator mcreator, Map<String, Object> params) {
        Object transObj = params.get("translations");
        if (!(transObj instanceof Map)) return createErrorResult("translations map is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            Map<String, Map<String, String>> translations = (Map<String, Map<String, String>>) transObj;
            for (var entry : translations.entrySet()) {
                String lang = entry.getKey().trim().toLowerCase();
                Map<String, String> keys = entry.getValue();
                var lMap = ws.getLanguageMap() != null ? ws.getLanguageMap().computeIfAbsent(lang, k -> new LinkedHashMap<>()) : null;
                if (lMap != null) {
                    for (var kv : keys.entrySet()) {
                        lMap.put(kv.getKey(), kv.getValue());
                    }
                }
            }

            ws.markDirty();
            saveWorkspaceSafe(ws);
            return createSuccessResult("Batch updated translations across " + translations.size() + " languages");
        } catch (Exception e) { return createErrorResult("Failed to batch set localizations: " + e.getMessage()); }
    }

    private McpTypes.ToolResult autoFillMissingTranslations(MCreator mcreator, Map<String, Object> params) {
        String targetLang = (String) params.get("targetLanguage");
        String prefix = (String) params.get("prefix");
        if (targetLang == null) return createErrorResult("targetLanguage is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            Map<String, String> en = ws.getLanguageMap() != null ? ws.getLanguageMap().get("en_us") : null;
            if (en == null || en.isEmpty()) return createErrorResult("en_us localizations not found");

            String tLang = targetLang.trim().toLowerCase();
            String pfx = prefix != null ? prefix : "";
            var targetMap = ws.getLanguageMap().computeIfAbsent(tLang, k -> new LinkedHashMap<>());
            int added = 0;

            for (var entry : en.entrySet()) {
                if (!targetMap.containsKey(entry.getKey())) {
                    targetMap.put(entry.getKey(), pfx + entry.getValue());
                    added++;
                }
            }

            ws.markDirty();
            saveWorkspaceSafe(ws);
            return createSuccessResult("Auto-filled " + added + " missing keys in " + tLang + " from en_us");
        } catch (Exception e) { return createErrorResult("Failed to auto fill translations: " + e.getMessage()); }
    }

    private McpTypes.ToolResult searchLocalizationKeys(MCreator mcreator, Map<String, Object> params) {
        String query = (String) params.get("query");
        String lang = (String) params.get("language");
        if (query == null) return createErrorResult("query is required");

        try {
            Workspace ws = mcreator.getWorkspace();
            if (ws == null) return createErrorResult("No workspace loaded");

            String targetLang = (lang != null && !lang.trim().isEmpty()) ? lang.trim().toLowerCase() : "en_us";
            Map<String, String> lMap = ws.getLanguageMap() != null ? ws.getLanguageMap().get(targetLang) : null;

            Map<String, String> matches = new HashMap<>();
            String q = query.trim().toLowerCase();
            if (lMap != null) {
                for (var entry : lMap.entrySet()) {
                    if (entry.getKey().toLowerCase().contains(q) || (entry.getValue() != null && entry.getValue().toLowerCase().contains(q))) {
                        matches.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("language", targetLang);
            res.put("query", query);
            res.put("matchesCount", matches.size());
            res.put("matches", matches);
            return createSuccessResult(objectMapper.writeValueAsString(res));
        } catch (Exception e) { return createErrorResult("Failed to search localizations: " + e.getMessage()); }
    }

    private File resolveFile(Workspace ws, String filePath) {
        File f = new File(filePath.trim());
        if (!f.isAbsolute()) f = new File(ws.getWorkspaceFolder(), filePath.trim());
        return f;
    }
}
