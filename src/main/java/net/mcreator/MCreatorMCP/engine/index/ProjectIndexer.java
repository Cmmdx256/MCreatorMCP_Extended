package net.mcreator.MCreatorMCP.engine.index;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.MCreatorMCP.engine.graph.SemanticProjectGraph;
import net.mcreator.MCreatorMCP.engine.model.*;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.io.FileIO;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.VariableElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectIndexer {

    private static final Logger LOG = LogManager.getLogger("ProjectIndexer");

    public static void indexWorkspace(Workspace workspace, ProjectModel model, SemanticProjectGraph graph) {
        if (workspace == null || model == null || graph == null) return;

        try {
            long startTime = System.currentTimeMillis();
            graph.clear();
            model.getElements().clear();
            model.getProcedures().clear();
            model.getVariables().clear();
            model.getResources().clear();
            model.getTags().clear();
            model.getLocalizations().clear();

            // 1. Settings
            var s = workspace.getWorkspaceSettings();
            if (s != null) {
                model.setModName(s.getModName());
                model.setModId(s.getModID());
                model.setVersion(s.getVersion());
                model.setAuthor(s.getAuthor());
                model.setPackageName(s.getModElementsPackage());
            }
            model.setMcreatorVersion(String.valueOf(workspace.getMCreatorVersion()));
            if (workspace.getGeneratorConfiguration() != null) {
                model.setGeneratorName(workspace.getGeneratorConfiguration().getGeneratorName());
            }
            model.setWorkspacePath(workspace.getWorkspaceFolder().getAbsolutePath());

            // 2. Variables
            for (VariableElement ve : workspace.getVariableElements()) {
                String vType = ve.getType() != null ? ve.getType().getName() : "String";
                String vScope = ve.getScope() != null ? ve.getScope().name() : "GLOBAL_SESSION";
                String vVal = ve.getValue() != null ? ve.getValue().toString() : "";
                VariableNode vn = new VariableNode(ve.getName(), vType, vScope, vVal);
                model.getVariables().put(ve.getName(), vn);
            }

            // 3. Localizations
            if (workspace.getLanguageMap() != null) {
                for (var entry : workspace.getLanguageMap().entrySet()) {
                    model.getLocalizations().put(entry.getKey(), new HashMap<>(entry.getValue()));
                }
            }

            // 4. Mod Elements & Procedures
            for (ModElement element : workspace.getModElements()) {
                indexSingleElement(workspace, element, model, graph);
            }

            // 5. Index Resources (Textures, Models, Sounds, Structures)
            indexResources(workspace, model, graph);

            model.setLastIndexTimestamp(System.currentTimeMillis());
            LOG.info("Workspace indexed in {}ms: {} elements, {} procedures, {} relationships",
                    (System.currentTimeMillis() - startTime),
                    model.getElements().size(),
                    model.getProcedures().size(),
                    graph.exportGraphSummary().get("totalRelationships"));

        } catch (Exception e) {
            LOG.error("Error during full workspace indexing", e);
        }
    }

    public static void indexSingleElement(Workspace workspace, ModElement element, ProjectModel model, SemanticProjectGraph graph) {
        if (element == null) return;
        try {
            String name = element.getName();
            String type = element.getType() != null ? element.getType().getRegistryName() : "unknown";
            ElementNode en = new ElementNode(name, type);
            en.setLocked(element.isCodeLocked());

            File modFile = new File(workspace.getFolderManager().getModElementsDir(), name + ".mod.json");
            if (modFile.exists()) {
                String jsonStr = FileIO.readFileToString(modFile);
                JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
                JsonObject def = root.getAsJsonObject("definition");
                if (def != null) {
                    for (var entry : def.entrySet()) {
                        en.getProperties().put(entry.getKey(), entry.getValue().toString());
                    }
                    findAndLinkDependencies(name, def, en, graph, workspace);
                }
            }

            model.getElements().put(name, en);

            // If procedure, index deep Blockly structure
            if (element.getType() == ModElementType.PROCEDURE) {
                indexProcedureElement(workspace, element, model, graph);
            }

        } catch (Exception e) {
            LOG.warn("Failed to index element {}: {}", element.getName(), e.getMessage());
        }
    }

    private static void findAndLinkDependencies(String sourceName, JsonObject def, ElementNode en,
                                               SemanticProjectGraph graph, Workspace workspace) {
        String defText = def.toString();
        for (ModElement other : workspace.getModElements()) {
            if (!other.getName().equals(sourceName)) {
                if (defText.contains("\"" + other.getName() + "\"")) {
                    en.getReferencedElements().add(other.getName());
                    DependencyRelation rel = other.getType() == ModElementType.PROCEDURE ?
                            DependencyRelation.TRIGGERS : DependencyRelation.REFERENCES;
                    graph.addEdge(sourceName, other.getName(), rel, "Definition reference");
                }
            }
        }
    }

    private static void indexProcedureElement(Workspace workspace, ModElement element,
                                              ProjectModel model, SemanticProjectGraph graph) {
        try {
            String name = element.getName();
            ProcedureNode pn = new ProcedureNode(name);
            GeneratableElement ge = element.getGeneratableElement();
            if (ge != null) {
                String json = WorkspaceFileManager.gson.toJson(ge);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (obj.has("trigger")) {
                    pn.setTrigger(obj.get("trigger").getAsString());
                }
                String xml = obj.has("xml") ? obj.get("xml").getAsString() : "";

                if (!xml.isEmpty()) {
                    pn.setContainsLoops(xml.contains("controls_repeat") || xml.contains("controls_whileUntil") || xml.contains("controls_forEach"));
                    pn.setContainsAreaSearches(xml.contains("world_entity_inrange") || xml.contains("world_entities_list"));

                    Matcher varMatcher = Pattern.compile("<field name=\"VAR\">([^<]+)</field>").matcher(xml);
                    while (varMatcher.find()) {
                        pn.getAccessedVariables().add(varMatcher.group(1));
                        graph.addEdge(name, varMatcher.group(1), DependencyRelation.MODIFIES, "Procedure variable");
                    }

                    Matcher potionMatcher = Pattern.compile("<field name=\"potion\">([^<]+)</field>").matcher(xml);
                    while (potionMatcher.find()) {
                        pn.getAppliedEffects().add(potionMatcher.group(1));
                        graph.addEdge(name, potionMatcher.group(1), DependencyRelation.PROVIDES_EFFECT, "Potion effect");
                    }

                    for (ModElement other : workspace.getModElements()) {
                        if (!other.getName().equals(name) && xml.contains(other.getName())) {
                            pn.getReferencedElements().add(other.getName());
                            graph.addEdge(name, other.getName(), DependencyRelation.REFERENCES, "Blockly reference");
                        }
                    }
                }
            }
            model.getProcedures().put(name, pn);
        } catch (Exception e) {
            LOG.warn("Failed to index procedure {}: {}", element.getName(), e.getMessage());
        }
    }

    private static void indexResources(Workspace workspace, ProjectModel model, SemanticProjectGraph graph) {
        try {
            // Textures
            for (TextureType tt : TextureType.values()) {
                List<File> files = workspace.getFolderManager().getTexturesList(tt);
                if (files != null) {
                    for (File f : files) {
                        String rName = f.getName().replace(".png", "");
                        ResourceNode rn = new ResourceNode(rName, ResourceNode.ResourceType.TEXTURE, tt.name(), f.getAbsolutePath(), f.length());
                        model.getResources().put("texture:" + rName, rn);
                    }
                }
            }

            // Models
            File modelsDir = workspace.getFolderManager().getModelsDir();
            if (modelsDir != null && modelsDir.exists()) {
                File[] files = modelsDir.listFiles((d, n) -> n.endsWith(".json") || n.endsWith(".java") || n.endsWith(".obj"));
                if (files != null) {
                    for (File f : files) {
                        String rName = f.getName();
                        ResourceNode rn = new ResourceNode(rName, ResourceNode.ResourceType.MODEL, "3D_MODEL", f.getAbsolutePath(), f.length());
                        model.getResources().put("model:" + rName, rn);
                    }
                }
            }

            // Sounds
            File soundsDir = workspace.getFolderManager().getSoundsDir();
            if (soundsDir != null && soundsDir.exists()) {
                File[] files = soundsDir.listFiles((d, n) -> n.endsWith(".ogg"));
                if (files != null) {
                    for (File f : files) {
                        String rName = f.getName().replace(".ogg", "");
                        ResourceNode rn = new ResourceNode(rName, ResourceNode.ResourceType.SOUND, "OGG_AUDIO", f.getAbsolutePath(), f.length());
                        model.getResources().put("sound:" + rName, rn);
                    }
                }
            }

            // Structures
            File structDir = workspace.getFolderManager().getStructuresDir();
            if (structDir != null && structDir.exists()) {
                File[] files = structDir.listFiles((d, n) -> n.endsWith(".nbt"));
                if (files != null) {
                    for (File f : files) {
                        String rName = f.getName().replace(".nbt", "");
                        ResourceNode rn = new ResourceNode(rName, ResourceNode.ResourceType.STRUCTURE, "NBT_STRUCTURE", f.getAbsolutePath(), f.length());
                        model.getResources().put("structure:" + rName, rn);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Error indexing workspace resources", e);
        }
    }
}
