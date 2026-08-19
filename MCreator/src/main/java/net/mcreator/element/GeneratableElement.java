/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2023, Pylo, opensource contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.element;

import com.google.gson.*;
import net.mcreator.Launcher;
import net.mcreator.element.converter.ConverterRegistry;
import net.mcreator.element.converter.ConverterUtils;
import net.mcreator.element.converter.IConverter;
import net.mcreator.element.parts.IWorkspaceDependent;
import net.mcreator.element.parts.procedure.RetvalProcedure;
import net.mcreator.generator.template.IAdditionalTemplateDataProvider;
import net.mcreator.ui.minecraft.states.StateMap;
import net.mcreator.util.TestUtil;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public abstract class GeneratableElement {

	public static final int formatVersion = 79;

	private static final Logger LOG = LogManager.getLogger("Generatable Element");

	private transient ModElement element;

	private transient boolean conversionApplied = false;

	public GeneratableElement(ModElement element) {
		if (element != null)
			setModElement(element);
	}

	public ModElement getModElement() {
		return element;
	}

	public void setModElement(ModElement element) {
		this.element = element;
	}

	/**
	 * @return BufferedImage of mod element preview or null if default mod element icon should be used
	 */
	public BufferedImage generateModElementPicture() {
		return null;
	}

	/**
	 * This method should take care of generating additional mod
	 * element resources for cases such as GUI mod element
	 */
	public void finalizeModElementGeneration() {
	}

	/**
	 * Override this to add additional data to the element data model
	 *
	 * @return null if no additional data, or IAdditionalTemplateDataProvider implementation
	 */
	public @Nullable IAdditionalTemplateDataProvider getAdditionalTemplateData() {
		return null;
	}

	public boolean wasConversionApplied() {
		return conversionApplied;
	}

	public final boolean performQuickValidation() {
		for (Field field : getClass().getDeclaredFields()) {
			if (field.isAnnotationPresent(Nonnull.class)) {
				field.setAccessible(true);
				try {
					if (field.get(this) == null) {
						if (field.getType() == String.class) {
							String defVal = "Normal";
							String fname = field.getName();
							if (fname.equals("mobModelName")) defVal = "Biped";
							else if (fname.equals("destroyTool")) defVal = "Not specified";
							else if (fname.equals("toolType")) defVal = "Pickaxe";
							else if (fname.equals("blockingModelName")) defVal = "Normal blocking";
							else if (fname.equals("type")) defVal = "WATER";
							else if (fname.equals("triggerKey")) defVal = "UNKNOWN";
							else if (fname.equals("recipeType")) defVal = "Crafting";
							else if (fname.equals("customModelName")) defVal = "Normal";
							field.set(this, defVal);
							LOG.info("Auto-fixed null @Nonnull field {} of mod element {} with '{}'",
									field.getName(), this.element.getName(), defVal);
						} else if (List.class.isAssignableFrom(field.getType())) {
							field.set(this, new ArrayList<>());
							LOG.info("Auto-fixed null @Nonnull List field {} of mod element {}",
									field.getName(), this.element.getName());
						} else if (Map.class.isAssignableFrom(field.getType())) {
							field.set(this, new HashMap<>());
							LOG.info("Auto-fixed null @Nonnull Map field {} of mod element {}",
									field.getName(), this.element.getName());
						} else if (field.getType().getName().contains("MItemBlock")) {
							field.set(this, new net.mcreator.element.parts.MItemBlock());
						} else {
							LOG.warn(
									"Field {} of mod element {} is null, but should not be. Assuming invalid generatable element.",
									field.getName(), this.element.getName());
							return false;
						}
					}
				} catch (Exception e) {
					LOG.warn("Failed to validate/repair field {} of {}: {}", field.getName(), this.element.getName(), e.getMessage());
				}
			}
		}

		return true;
	}

	public boolean isUnknown() {
		return false;
	}

	public static class GSONAdapter
			implements JsonSerializer<GeneratableElement>, JsonDeserializer<GeneratableElement> {

		private static final Gson gson;

		static {
			GsonBuilder gsonBuilder = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
					.setStrictness(Strictness.LENIENT);

			RetvalProcedure.GSON_ADAPTERS.forEach(gsonBuilder::registerTypeAdapter);

			gsonBuilder.registerTypeAdapter(StateMap.class, new StateMap.GSONAdapter());

			gson = gsonBuilder.create();
		}

		@Nonnull private final Workspace workspace;

		public GSONAdapter(@Nonnull Workspace workspace) {
			this.workspace = workspace;
		}

		@Override
		public GeneratableElement deserialize(JsonElement jsonElement, Type type,
				JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
			ModElement lastModElement = workspace.getModElementManager().getLastElementInConversion();

			final String modElementTypeString = switch (jsonElement.getAsJsonObject().get("_type").getAsString()) {
				case "gun" -> "rangeditem";
				case "mob" -> "livingentity";
				default -> jsonElement.getAsJsonObject().get("_type").getAsString();
			};
			final int importedFormatVersion = jsonDeserializationContext.deserialize(
					jsonElement.getAsJsonObject().get("_fv"), Integer.class);

			// If GE was stored with newer FV, we can not deserialize it (we still allow this on development builds for testing purposes)
			if (importedFormatVersion > formatVersion) {
				if (Launcher.version.isDevelopment()) {
					LOG.info("Mod element {} was saved in FV {} but current FV is " + GeneratableElement.formatVersion
							+ ". Things may not work well", lastModElement.getName(), importedFormatVersion);
				} else {
					LOG.warn("Mod element {} was saved in FV {} but current FV is " + GeneratableElement.formatVersion
							+ " so we can not deserialize it", lastModElement.getName(), importedFormatVersion);
					return null;
				}
			}

			try {
				ModElementType<?> modElementType = ModElementTypeLoader.getModElementType(modElementTypeString);

				JsonObject jsonObject = jsonElement.getAsJsonObject().get("definition").getAsJsonObject();
				if (jsonObject.keySet().isEmpty()) {
					LOG.warn("Mod element {} ({}) has no definition so we can not deserialize it",
							lastModElement.getName(), modElementType);
					return null;
				}

				// Sanitize MItemBlock fields: if any was stored as [] (array) or primitive instead of an object, remove it
				// so that the default MItemBlock() fallback below kicks in instead of crashing Gson.
				String[] itemBlockFields = { "mobDrop", "equipmentMainHand", "equipmentOffHand", "equipmentHelmet", "equipmentBody",
						"equipmentLeggings", "equipmentBoots", "rangedAttackItem", "customDrop", "creativePickItem", "eatResultItem" };
				for (String field : itemBlockFields) {
					if (jsonObject.has(field) && (jsonObject.get(field).isJsonArray() || jsonObject.get(field).isJsonPrimitive())) {
						jsonObject.remove(field);
					}
				}

				GeneratableElement generatableElement = gson.fromJson(jsonObject,
						modElementType.getModElementStorageClass());
				generatableElement.setModElement(lastModElement); // set the mod element reference

				if (generatableElement instanceof net.mcreator.element.types.LivingEntity le) {
					if (le.mobModelName == null || le.mobModelName.trim().isEmpty())
						le.mobModelName = "Biped";
					if (le.mobModelTexture == null)
						le.mobModelTexture = "";
					if (le.mobName == null || le.mobName.trim().isEmpty())
						le.mobName = lastModElement.getName();
					if (le.mobLabel == null || le.mobLabel.trim().isEmpty())
						le.mobLabel = le.mobName;
					if (le.mobSpawningType == null || le.mobSpawningType.trim().isEmpty())
						le.mobSpawningType = "creature";
					if (le.mobCreatureType == null || le.mobCreatureType.trim().isEmpty())
						le.mobCreatureType = "UNDEFINED";
					if (le.mobBehaviourType == null || le.mobBehaviourType.trim().isEmpty())
						le.mobBehaviourType = "Mob";
					if (le.aixml == null || le.aixml.trim().isEmpty())
						le.aixml = "<xml xmlns=\"https://developers.google.com/blockly/xml\"><block type=\"aitasks_container\" deletable=\"false\" x=\"40\" y=\"40\"></block></xml>";
					if (le.creativeTabs == null)
						le.creativeTabs = new java.util.ArrayList<>();
					if (le.modelLayers == null)
						le.modelLayers = new java.util.ArrayList<>();
					if (le.animations == null)
						le.animations = new java.util.ArrayList<>();
					if (le.entityDataEntries == null)
						le.entityDataEntries = new java.util.ArrayList<>();
					if (le.breedTriggerItems == null)
						le.breedTriggerItems = new java.util.ArrayList<>();
					if (le.restrictionBiomes == null)
						le.restrictionBiomes = new java.util.ArrayList<>();
					if (le.vibrationalEvents == null)
						le.vibrationalEvents = new java.util.ArrayList<>();
					if (le.raidSpawnsCount == null)
						le.raidSpawnsCount = new int[] { 4, 3, 3, 4, 4, 4, 2 };
					if (le.mobDrop == null)
						le.mobDrop = new net.mcreator.element.parts.MItemBlock();
					if (le.equipmentMainHand == null)
						le.equipmentMainHand = new net.mcreator.element.parts.MItemBlock();
					if (le.equipmentOffHand == null)
						le.equipmentOffHand = new net.mcreator.element.parts.MItemBlock();
					if (le.equipmentHelmet == null)
						le.equipmentHelmet = new net.mcreator.element.parts.MItemBlock();
					if (le.equipmentBody == null)
						le.equipmentBody = new net.mcreator.element.parts.MItemBlock();
					if (le.equipmentLeggings == null)
						le.equipmentLeggings = new net.mcreator.element.parts.MItemBlock();
					if (le.equipmentBoots == null)
						le.equipmentBoots = new net.mcreator.element.parts.MItemBlock();
					if (le.rangedAttackItem == null)
						le.rangedAttackItem = new net.mcreator.element.parts.MItemBlock();
					if (le.aiBase == null || le.aiBase.trim().isEmpty())
						le.aiBase = "(none)";
					if (le.bossBarColor == null || le.bossBarColor.trim().isEmpty())
						le.bossBarColor = "PINK";
					if (le.bossBarType == null || le.bossBarType.trim().isEmpty())
						le.bossBarType = "PROGRESS";
					if (le.rangedItemType == null || le.rangedItemType.trim().isEmpty())
						le.rangedItemType = "Default item";
				} else if (generatableElement instanceof net.mcreator.element.types.Block b) {
					if (b.customModelName == null || b.customModelName.trim().isEmpty())
						b.customModelName = "Normal";
					if (b.customDrop == null)
						b.customDrop = new net.mcreator.element.parts.MItemBlock();
					if (b.creativePickItem == null)
						b.creativePickItem = new net.mcreator.element.parts.MItemBlock();
					if (b.creativeTabs == null)
						b.creativeTabs = new java.util.ArrayList<>();
					if (b.customProperties == null)
						b.customProperties = new java.util.ArrayList<>();
					if (b.boundingBoxes == null)
						b.boundingBoxes = new java.util.ArrayList<>();
					if (b.restrictionBiomes == null)
						b.restrictionBiomes = new java.util.ArrayList<>();
					if (b.blocksToReplace == null)
						b.blocksToReplace = new java.util.ArrayList<>();
					if (b.inventoryInSlotIDs == null)
						b.inventoryInSlotIDs = new java.util.ArrayList<>();
					if (b.inventoryOutSlotIDs == null)
						b.inventoryOutSlotIDs = new java.util.ArrayList<>();
					if (b.vibrationalEvents == null)
						b.vibrationalEvents = new java.util.ArrayList<>();
					if (b.animations == null)
						b.animations = new java.util.ArrayList<>();
					if (b.blockSetType == null || b.blockSetType.trim().isEmpty())
						b.blockSetType = "OAK";
					if (b.tintType == null || b.tintType.trim().isEmpty())
						b.tintType = "No tint";
					if (b.reactionToPushing == null || b.reactionToPushing.trim().isEmpty())
						b.reactionToPushing = "NORMAL";
					if (b.generationShape == null || b.generationShape.trim().isEmpty())
						b.generationShape = "UNIFORM";
					if (b.destroyTool == null || b.destroyTool.trim().isEmpty())
						b.destroyTool = "Not specified";
					if (b.vanillaToolTier == null || b.vanillaToolTier.trim().isEmpty())
						b.vanillaToolTier = "NONE";
					if (b.colorOnMap == null || b.colorOnMap.trim().isEmpty())
						b.colorOnMap = "DEFAULT";
					if (b.noteBlockInstrument == null || b.noteBlockInstrument.trim().isEmpty())
						b.noteBlockInstrument = "harp";
					if (b.aiPathNodeType == null || b.aiPathNodeType.trim().isEmpty())
						b.aiPathNodeType = "DEFAULT";
					if (b.offsetType == null || b.offsetType.trim().isEmpty())
						b.offsetType = "NONE";
				} else if (generatableElement instanceof net.mcreator.element.types.Item item) {
					if (item.customModelName == null || item.customModelName.trim().isEmpty())
						item.customModelName = "Normal";
					if (item.eatResultItem == null)
						item.eatResultItem = new net.mcreator.element.parts.MItemBlock();
					if (item.creativeTabs == null)
						item.creativeTabs = new java.util.ArrayList<>();
					if (item.customProperties == null)
						item.customProperties = new java.util.LinkedHashMap<>();
					if (item.states == null)
						item.states = new java.util.ArrayList<>();
					if (item.providedBannerPatterns == null)
						item.providedBannerPatterns = new java.util.ArrayList<>();
					if (item.rarity == null || item.rarity.trim().isEmpty())
						item.rarity = "COMMON";
					if (item.animation == null || item.animation.trim().isEmpty())
						item.animation = "eat";
				} else if (generatableElement instanceof net.mcreator.element.types.Tool tool) {
					if (tool.customModelName == null || tool.customModelName.trim().isEmpty())
						tool.customModelName = "Normal";
					if (tool.toolType == null || tool.toolType.trim().isEmpty())
						tool.toolType = "Pickaxe";
					if (tool.creativeTabs == null)
						tool.creativeTabs = new java.util.ArrayList<>();
					if (tool.repairItems == null)
						tool.repairItems = new java.util.ArrayList<>();
					if (tool.blocksAffected == null)
						tool.blocksAffected = new java.util.ArrayList<>();
					if (tool.blockingModelName == null || tool.blockingModelName.trim().isEmpty())
						tool.blockingModelName = "Normal blocking";
					if (tool.blockDropsTier == null || tool.blockDropsTier.trim().isEmpty())
						tool.blockDropsTier = "WOOD";
				} else if (generatableElement instanceof net.mcreator.element.types.Plant plant) {
					if (plant.customModelName == null || plant.customModelName.trim().isEmpty())
						plant.customModelName = "Cross model";
					if (plant.customDrop == null)
						plant.customDrop = new net.mcreator.element.parts.MItemBlock();
					if (plant.creativePickItem == null)
						plant.creativePickItem = new net.mcreator.element.parts.MItemBlock();
					if (plant.creativeTabs == null)
						plant.creativeTabs = new java.util.ArrayList<>();
					if (plant.boundingBoxes == null)
						plant.boundingBoxes = new java.util.ArrayList<>();
					if (plant.canBePlacedOn == null)
						plant.canBePlacedOn = new java.util.ArrayList<>();
					if (plant.restrictionBiomes == null)
						plant.restrictionBiomes = new java.util.ArrayList<>();
					if (plant.rarity == null || plant.rarity.trim().isEmpty())
						plant.rarity = "COMMON";
				} else if (generatableElement instanceof net.mcreator.element.types.Armor armor) {
					if (armor.helmetItemCustomModelName == null || armor.helmetItemCustomModelName.trim().isEmpty())
						armor.helmetItemCustomModelName = "Normal";
					if (armor.bodyItemCustomModelName == null || armor.bodyItemCustomModelName.trim().isEmpty())
						armor.bodyItemCustomModelName = "Normal";
					if (armor.leggingsItemCustomModelName == null || armor.leggingsItemCustomModelName.trim().isEmpty())
						armor.leggingsItemCustomModelName = "Normal";
					if (armor.bootsItemCustomModelName == null || armor.bootsItemCustomModelName.trim().isEmpty())
						armor.bootsItemCustomModelName = "Normal";
					if (armor.creativeTabs == null)
						armor.creativeTabs = new java.util.ArrayList<>();
					if (armor.repairItems == null)
						armor.repairItems = new java.util.ArrayList<>();
				} else if (generatableElement instanceof net.mcreator.element.types.Fluid fluid) {
					if (fluid.type == null || fluid.type.trim().isEmpty())
						fluid.type = "WATER";
				} else if (generatableElement instanceof net.mcreator.element.types.GameRule gr) {
					if (gr.type == null || gr.type.trim().isEmpty())
						gr.type = "Logic";
				} else if (generatableElement instanceof net.mcreator.element.types.KeyBinding kb) {
					if (kb.triggerKey == null || kb.triggerKey.trim().isEmpty())
						kb.triggerKey = "UNKNOWN";
				} else if (generatableElement instanceof net.mcreator.element.types.LootTable lt) {
					if (lt.type == null || lt.type.trim().isEmpty())
						lt.type = "generic";
				} else if (generatableElement instanceof net.mcreator.element.types.Recipe r) {
					if (r.recipeType == null || r.recipeType.trim().isEmpty())
						r.recipeType = "Crafting";
				} else if (generatableElement instanceof net.mcreator.element.types.Procedure p) {
					if (p.procedurexml == null || p.procedurexml.trim().isEmpty())
						p.procedurexml = net.mcreator.element.types.Procedure.XML_BASE;
				}

				// Populate workspace-dependant fields with workspace reference
				IWorkspaceDependent.processWorkspaceDependentObjects(generatableElement,
						workspaceDependent -> workspaceDependent.setWorkspace(workspace));

				List<IConverter> converters = ConverterRegistry.getConvertersForModElementType(modElementType);
				if (converters != null) {
					List<IConverter> applicableConverters = converters.stream()
							.filter(converter -> importedFormatVersion < converter.getVersionConvertingTo()).sorted()
							.toList();
					// If there are converters applicable to this mod element type, log the conversion
					if (!applicableConverters.isEmpty()) {
						LOG.debug("Converting {} ({}) from FV{} using: {}", lastModElement.getName(), modElementType,
								importedFormatVersion, applicableConverters.stream()
										.map(converter -> converter.getClass().getSimpleName() + " to FV"
												+ converter.getVersionConvertingTo())
										.collect(Collectors.joining(", ")));
					}
					for (IConverter converter : applicableConverters) {
						try {
							generatableElement = converter.convert(this.workspace, generatableElement, jsonElement);
						} catch (Exception e) {
							LOG.warn("Failed to convert mod element {} of type {} to FV{} using {}",
									lastModElement.getName(), modElementType, converter.getVersionConvertingTo(),
									converter.getClass().getSimpleName(), e);
							TestUtil.failIfTestingEnvironment();
						}

						if (generatableElement == null
								|| generatableElement.getClass() != modElementType.getModElementStorageClass()) {
							ConverterUtils.convertElementToDifferentType(converter, lastModElement, generatableElement);
							return null;
						} else {
							generatableElement.conversionApplied = true;
						}
					}
				}

				return generatableElement;
			} catch (IllegalArgumentException e) { // we may be dealing with mod element type no longer existing
				IConverter converter = ConverterRegistry.getConverterForModElementType(modElementTypeString);
				if (converter != null && importedFormatVersion < converter.getVersionConvertingTo()) {
					try {
						GeneratableElement result = null;
						try {
							result = converter.convert(this.workspace, new Unknown(lastModElement), jsonElement);
						} catch (Exception e2) {
							LOG.warn("Failed to convert mod element {} of type {} to FV{} using {}",
									lastModElement.getName(), modElementTypeString, converter.getVersionConvertingTo(),
									converter.getClass().getSimpleName(), e2);
							TestUtil.failIfTestingEnvironment();
						}
						ConverterUtils.convertElementToDifferentType(converter, lastModElement, result);
					} catch (Exception e2) {
						LOG.warn("Failed to convert mod element {} of type {} to a potential alternative.",
								lastModElement.getName(), modElementTypeString, e2);
					}
				}

				return null;
			} catch (Exception e) {
				LOG.warn("Failed to deserialize mod element {}", lastModElement.getName(), e);
				return null;
			}
		}

		@Override
		public JsonElement serialize(GeneratableElement modElement, Type type,
				JsonSerializationContext jsonSerializationContext) {
			JsonObject root = new JsonObject();
			root.add("_fv", new JsonPrimitive(GeneratableElement.formatVersion));
			root.add("_type", gson.toJsonTree(modElement.getModElement().getType().getRegistryName()));

			JsonObject definition = gson.toJsonTree(modElement).getAsJsonObject();

			if (definition.keySet().isEmpty()) {
				LOG.warn("Mod element {} ({}) has no definition so we can't serialize it",
						modElement.getModElement().getName(), modElement.getModElement().getType());
				return null;
			}

			root.add("definition", definition);

			return root;
		}

	}

	public static final class Unknown extends GeneratableElement {

		public Unknown(ModElement element) {
			super(element);
		}

		@Override public boolean isUnknown() {
			return true;
		}
	}

}
