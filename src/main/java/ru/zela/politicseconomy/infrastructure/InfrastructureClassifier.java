package ru.zela.politicseconomy.infrastructure;

/**
 * Lightweight first-pass classifier. It intentionally uses registry IDs and
 * common Vanilla/Create naming conventions so new mods can be supported
 * without hardcoding every individual block.
 */
public final class InfrastructureClassifier {
    private InfrastructureClassifier() {}

    public static InfrastructureBlockInfo classify(String id) {
        String normalized = id.toLowerCase();
        int colon = normalized.indexOf(':');
        String namespace = colon >= 0 ? normalized.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? normalized.substring(colon + 1) : normalized;

        if (isMilitary(namespace, path)) {
            return new InfrastructureBlockInfo(id, InfrastructureCategory.MILITARY, 1.50);
        }
        if (isTransport(namespace, path)) {
            return new InfrastructureBlockInfo(id, InfrastructureCategory.TRANSPORT, 0.70);
        }
        if (isIndustrial(namespace, path)) {
            return new InfrastructureBlockInfo(id, InfrastructureCategory.INDUSTRIAL, 0.80);
        }
        if (isAdvanced(namespace, path)) {
            return new InfrastructureBlockInfo(id, InfrastructureCategory.ADVANCED, 1.20);
        }
        if (isResource(path)) {
            return new InfrastructureBlockInfo(id, InfrastructureCategory.RESOURCE, 0.20);
        }
        if (isResidential(path)) {
            return new InfrastructureBlockInfo(id, InfrastructureCategory.RESIDENTIAL, 0.10);
        }

        return new InfrastructureBlockInfo(id, InfrastructureCategory.DECORATIVE, 0.02);
    }

    private static boolean isMilitary(String namespace, String path) {
        return namespace.contains("cbc")
            || namespace.contains("military")
            || namespace.contains("pwic")
            || path.contains("cannon")
            || path.contains("gun")
            || path.contains("ammo")
            || path.contains("machine_gun")
            || path.contains("turret")
            || path.contains("radar");
    }

    private static boolean isTransport(String namespace, String path) {
        return namespace.contains("immersivevehicles")
            || namespace.equals("mts")
            || namespace.contains("vehicle")
            || namespace.contains("aviation")
            || path.contains("rail")
            || path.contains("track")
            || path.contains("station")
            || path.contains("loader")
            || path.contains("conveyor")
            || path.contains("road");
    }

    private static boolean isIndustrial(String namespace, String path) {
        return namespace.equals("create")
            || namespace.contains("create")
            || namespace.contains("factory")
            || path.contains("machine")
            || path.contains("press")
            || path.contains("mixer")
            || path.contains("crusher")
            || path.contains("drill")
            || path.contains("assembler")
            || path.contains("generator")
            || path.contains("refinery")
            || path.contains("furnace");
    }

    private static boolean isAdvanced(String namespace, String path) {
        return path.contains("advanced")
            || path.contains("controller")
            || path.contains("computer")
            || path.contains("reactor")
            || path.contains("power");
    }

    private static boolean isResource(String path) {
        return path.contains("ore")
            || path.contains("log")
            || path.contains("crop")
            || path.contains("farm")
            || path.contains("mine")
            || path.contains("quarry")
            || path.contains("storage_block");
    }

    private static boolean isResidential(String path) {
        return path.contains("bed")
            || path.contains("house")
            || path.contains("residential")
            || path.contains("door")
            || path.contains("fence")
            || path.contains("window");
    }
}
