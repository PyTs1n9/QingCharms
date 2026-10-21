package net.pytsing.qingcharms.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CharmGuiHolder implements InventoryHolder {

    private final GuiDefinition definition;
    private Inventory inventory;

    public CharmGuiHolder(GuiDefinition definition) {
        this.definition = definition;
    }

    public GuiDefinition getDefinition() {
        return definition;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
