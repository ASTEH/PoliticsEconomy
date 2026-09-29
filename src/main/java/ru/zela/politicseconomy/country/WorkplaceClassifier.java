package ru.zela.politicseconomy.country;

import java.util.Locale;

/**
 * Maps real placed infrastructure blocks to national job capacity.
 *
 * <p>Only explicitly recognized blocks create jobs. Decorative blocks,
 * ordinary building materials and the internal parts of Create contraptions
 * do not create employment.</p>
 */
public final class WorkplaceClassifier {
    private WorkplaceClassifier() {}

    public static WorkplaceDefinition classify(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        String normalized = id.toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        String namespace = colon >= 0 ? normalized.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? normalized.substring(colon + 1) : normalized;

        if (namespace.equals("minecraft")) {
            return vanilla(path);
        }

        if (namespace.equals("create")) {
            return create(path);
        }

        if (namespace.equals("createbigcannons")) {
            return createBigCannons(path);
        }

        // Small integrations for the user's common Create-adjacent mods.
        if (path.contains("radar")) {
            return new WorkplaceDefinition(WorkforceSector.MILITARY, 5);
        }
        if (namespace.contains("diesel") && (path.contains("generator") || path.contains("engine"))) {
            return new WorkplaceDefinition(WorkforceSector.INDUSTRY, 5);
        }
        if (path.contains("vehicle_workshop") || path.contains("mechanic_garage")) {
            return new WorkplaceDefinition(WorkforceSector.TRADE_LOGISTICS, 6);
        }

        return null;
    }

    private static WorkplaceDefinition vanilla(String path) {
        return switch (path) {
            case "composter" -> new WorkplaceDefinition(WorkforceSector.AGRICULTURE, 4);
            case "smoker" -> new WorkplaceDefinition(WorkforceSector.AGRICULTURE, 3);
            case "furnace" -> new WorkplaceDefinition(WorkforceSector.INDUSTRY, 2);
            case "blast_furnace" -> new WorkplaceDefinition(WorkforceSector.INDUSTRY, 3);
            case "crafting_table" -> new WorkplaceDefinition(WorkforceSector.CONSTRUCTION_SERVICES, 2);
            case "stonecutter" -> new WorkplaceDefinition(WorkforceSector.CONSTRUCTION_SERVICES, 4);
            case "smithing_table" -> new WorkplaceDefinition(WorkforceSector.CONSTRUCTION_SERVICES, 3);
            case "loom" -> new WorkplaceDefinition(WorkforceSector.CONSTRUCTION_SERVICES, 2);
            case "cartography_table" -> new WorkplaceDefinition(WorkforceSector.CONSTRUCTION_SERVICES, 2);
            case "brewing_stand" -> new WorkplaceDefinition(WorkforceSector.CONSTRUCTION_SERVICES, 2);
            case "enchanting_table" -> new WorkplaceDefinition(WorkforceSector.CONSTRUCTION_SERVICES, 2);
            default -> null;
        };
    }

    private static WorkplaceDefinition create(String path) {
        return switch (path) {
            case "mechanical_harvester" -> new WorkplaceDefinition(WorkforceSector.AGRICULTURE, 8);
            case "mechanical_plough" -> new WorkplaceDefinition(WorkforceSector.AGRICULTURE, 6);

            case "mechanical_drill" -> new WorkplaceDefinition(WorkforceSector.EXTRACTION, 8);

            case "mechanical_press" -> new WorkplaceDefinition(WorkforceSector.INDUSTRY, 7);
            case "mechanical_mixer" -> new WorkplaceDefinition(WorkforceSector.INDUSTRY, 7);
            case "mechanical_crafter" -> new WorkplaceDefinition(WorkforceSector.INDUSTRY, 9);
            case "crushing_wheel" -> new WorkplaceDefinition(WorkforceSector.INDUSTRY, 9);
            case "mechanical_saw" -> new WorkplaceDefinition(WorkforceSector.INDUSTRY, 6);
            case "deployer" -> new WorkplaceDefinition(WorkforceSector.INDUSTRY, 5);
            case "mechanical_arm" -> new WorkplaceDefinition(WorkforceSector.INDUSTRY, 4);
            case "steam_engine" -> new WorkplaceDefinition(WorkforceSector.INDUSTRY, 4);
            case "steam_whistle" -> null;

            case "stockpile" -> new WorkplaceDefinition(WorkforceSector.TRADE_LOGISTICS, 7);
            case "depot" -> new WorkplaceDefinition(WorkforceSector.TRADE_LOGISTICS, 4);
            case "train_station" -> new WorkplaceDefinition(WorkforceSector.TRADE_LOGISTICS, 8);

            default -> null;
        };
    }

    private static WorkplaceDefinition createBigCannons(String path) {
        return switch (path) {
            case "cannon_builder" -> new WorkplaceDefinition(WorkforceSector.MILITARY, 8);
            case "cannon_drill" -> new WorkplaceDefinition(WorkforceSector.MILITARY, 6);
            case "fixed_cannon_mount" -> new WorkplaceDefinition(WorkforceSector.MILITARY, 4);
            case "cannon_loader" -> new WorkplaceDefinition(WorkforceSector.MILITARY, 4);
            default -> null;
        };
    }

    public record WorkplaceDefinition(WorkforceSector sector, int capacity) {
        public WorkplaceDefinition {
            capacity = Math.max(1, capacity);
        }
    }
}
