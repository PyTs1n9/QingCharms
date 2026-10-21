package net.pytsing.qingcharms.gui;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public final class GuiDefinition {

    /** Actions the GUI actually handles. Anything else renders but does nothing. */
    private static final Set<String> ACTIONS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    "equip", "unequip", "refresh", "reopen", "close", "none")));
    private static final String ACTION_LIST = "equip, unequip, refresh, reopen, close, none";

    private final String title;
    private final int rows;
    private final String permission;
    private final List<GuiSlot> slots;

    public GuiDefinition(String title, int rows, String permission, List<GuiSlot> slots) {
        this.title = title == null || title.trim().isEmpty() ? "&8青玉护符装备" : title;
        this.rows = clampRows(rows);
        this.permission = permission == null ? "" : permission;
        this.slots = Collections.unmodifiableList(new ArrayList<GuiSlot>(
                slots == null ? Collections.<GuiSlot>emptyList() : slots));
    }

    public static GuiDefinition fromConfig(ConfigurationSection section) {
        return fromConfig(section, null);
    }

    public static GuiDefinition fromConfig(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return null;
        }

        String title = section.getString("title", "&8青玉护符装备");
        int configuredRows = section.contains("rows")
                ? section.getInt("rows", 3) : section.getInt("row", 3);
        int rows = clampRows(configuredRows);
        if (configuredRows != rows && logger != null) {
            logger.warning("GUI row count " + configuredRows
                    + " is outside 1-6; using " + rows + ".");
        }

        String permission = section.getString("permission", "qingcharms.gui");
        List<GuiSlot> slots = new ArrayList<GuiSlot>();
        ConfigurationSection slotSection = section.getConfigurationSection("slots");
        if (slotSection != null) {
            for (String key : slotSection.getKeys(false)) {
                ConfigurationSection child = slotSection.getConfigurationSection(key);
                if (child == null) {
                    warn(logger, "Ignoring GUI slot '" + key + "': expected a configuration section.");
                    continue;
                }

                Integer position = parseSlotPosition(child, key);
                if (position == null) {
                    warn(logger, "Ignoring GUI slot '" + key
                            + "': slot must be a non-negative integer or a numeric key.");
                    continue;
                }
                if (position.intValue() >= rows * 9) {
                    warn(logger, "Ignoring GUI slot '" + key + "': position "
                            + position + " does not fit in " + rows + " row(s); valid range is 0-"
                            + (rows * 9 - 1) + ".");
                    continue;
                }

                GuiSlot parsed = parseSlot(child, position.intValue());
                if (findSlot(slots, parsed.getSlot()) != null) {
                    warn(logger, "Ignoring GUI slot '" + key + "': position "
                            + parsed.getSlot() + " is already used.");
                    continue;
                }
                if (!ACTIONS.contains(parsed.getAction())) {
                    warn(logger, "GUI slot '" + key + "' uses unsupported action '"
                            + parsed.getAction() + "'; it renders but will not respond to clicks. "
                            + "Supported actions: " + ACTION_LIST + ".");
                }
                if (parsed.requiresType() && !parsed.hasUsableType()) {
                    warn(logger, "Ignoring GUI slot '" + key + "': action '"
                            + parsed.getAction() + "' requires 'type' (or 'slot-id') to be a real "
                            + "charm type; empty and NONE are not allowed.");
                    continue;
                }
                slots.add(parsed);
            }
        }
        return new GuiDefinition(title, rows, permission, slots);
    }

    private static int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }

    private static Integer parseSlotPosition(ConfigurationSection section, String key) {
        Integer explicit = getInteger(section, "slot");
        if (explicit == null) {
            explicit = getInteger(section, "position");
        }
        if (explicit != null) {
            return explicit.intValue() >= 0 ? explicit : null;
        }

        try {
            int parsed = Integer.parseInt(key.trim());
            return parsed >= 0 ? Integer.valueOf(parsed) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer getInteger(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        if (value instanceof String) {
            try {
                return Integer.valueOf(Integer.parseInt(((String) value).trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static GuiSlot parseSlot(ConfigurationSection section, int slot) {
        int itemId = section.getInt("id",
                section.getInt("game-id",
                        section.getInt("game_id", 1)));
        short data = (short) section.getInt("id_data",
                section.getInt("data",
                        section.getInt("durability", 0)));
        String name = first(section.getString("name", null),
                section.getString("display-name", null));
        List<String> lore = section.getStringList("lore");
        String typeName = first(section.getString("slot-id", null),
                first(section.getString("slot_id", null),
                        section.getString("type", "NONE")));
        String action = section.getString("action", "none");
        String permission = section.getString("permission", "");
        return new GuiSlot(slot, itemId, data, name, lore, typeName,
                action, permission);
    }

    private static GuiSlot findSlot(List<GuiSlot> slots, int position) {
        for (GuiSlot slot : slots) {
            if (slot.getSlot() == position) {
                return slot;
            }
        }
        return null;
    }

    private static void warn(Logger logger, String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    private static String first(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }

    public String getTitle() {
        return title;
    }

    public int getRows() {
        return rows;
    }

    public String getPermission() {
        return permission;
    }

    public List<GuiSlot> getSlots() {
        return slots;
    }

    public GuiSlot getSlot(int slot) {
        return findSlot(slots, slot);
    }
}