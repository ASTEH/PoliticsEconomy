package ru.zela.politicseconomy.economy;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Strategic resource categories used by the national upkeep system. */
public enum NationalResource {
    FOOD("food", "Еда", Items.BREAD),
    METAL("metal", "Металл", Items.IRON_INGOT),
    WOOD("wood", "Дерево", Items.OAK_LOG),
    FUEL("fuel", "Топливо", Items.COAL);

    private final String id;
    private final String displayName;
    private final Item icon;

    NationalResource(String id, String displayName, Item icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Item icon() {
        return icon;
    }

    public static NationalResource fromId(String value) {
        for (NationalResource resource : values()) {
            if (resource.id.equalsIgnoreCase(value)) {
                return resource;
            }
        }
        return null;
    }
}
