package net.pytsing.qingcharms.command;

import net.pytsing.qingcharms.QingCharmsPlugin;
import net.pytsing.qingcharms.progression.ProgressionRecord;
import net.pytsing.qingcharms.util.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ProgressionCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACTIONS = Collections.unmodifiableList(
            Arrays.asList("status", "level", "enhance", "breakthrough", "star",
                    "awaken", "help"));

    private final QingCharmsPlugin plugin;

    public ProgressionCommand(QingCharmsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label,
                             String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, "command.gui.player-only");
            return true;
        }
        if (!sender.hasPermission("qingcharms.progression")) {
            MessageUtils.send(sender, "progression.no-permission");
            return true;
        }
        Player player = (Player) sender;
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.US);
        if ("help".equals(action)) {
            MessageUtils.send(player, "progression.help.header");
            MessageUtils.send(player, "progression.help.status");
            MessageUtils.send(player, "progression.help.level");
            MessageUtils.send(player, "progression.help.enhance");
            MessageUtils.send(player, "progression.help.breakthrough");
            MessageUtils.send(player, "progression.help.star");
            MessageUtils.send(player, "progression.help.awaken");
            return true;
        }
        if ("status".equals(action)) {
            if (args.length < 2) {
                sendAllStatus(player);
            } else {
                sendStatus(player, args[1]);
            }
            return true;
        }

        if (args.length < 2) {
            MessageUtils.send(player, "progression.usage");
            return true;
        }
        String slotId = args[1];
        boolean success;
        if ("level".equals(action)) {
            success = plugin.getProgressionService().levelUp(player, slotId);
        } else if ("enhance".equals(action)) {
            success = plugin.getProgressionService().enhance(player, slotId);
        } else if ("breakthrough".equals(action) || "break".equals(action)) {
            success = plugin.getProgressionService().breakthrough(player, slotId);
        } else if ("star".equals(action)) {
            success = plugin.getProgressionService().star(player, slotId);
        } else if ("awaken".equals(action) || "awakening".equals(action)) {
            success = plugin.getProgressionService().awaken(player, slotId);
        } else {
            MessageUtils.send(player, "progression.usage");
            return true;
        }
        return success;
    }

    private void sendAllStatus(Player player) {
        net.pytsing.qingcharms.model.PlayerData data =
                plugin.getDataManager().loadPlayer(player.getUniqueId());
        boolean found = false;
        for (String slotId : data.getEquippedSlotIds()) {
            if (sendStatus(player, slotId)) {
                found = true;
            }
        }
        if (!found) {
            MessageUtils.send(player, "progression.status.empty");
        }
    }

    private boolean sendStatus(Player player, String slotId) {
        ProgressionRecord state = plugin.getProgressionService().record(player, slotId);
        if (state == null) {
            return false;
        }
        MessageUtils.send(player, "progression.status.header", slotId);
        MessageUtils.send(player, "progression.status.level",
                Integer.valueOf(state.getLevel()),
                Integer.valueOf(plugin.getProgressionService().maxLevel(state)));
        MessageUtils.send(player, "progression.status.enhance",
                Integer.valueOf(state.getEnhance()),
                Integer.valueOf(plugin.getProgressionService().maxEnhance(state)));
        MessageUtils.send(player, "progression.status.breakthrough",
                Integer.valueOf(state.getBreakthrough()));
        MessageUtils.send(player, "progression.status.star",
                Integer.valueOf(state.getStar()));
        MessageUtils.send(player, "progression.status.awakened",
                Boolean.valueOf(state.isAwakened()));
        MessageUtils.send(player, "progression.status.progress",
                Long.valueOf(state.getWearSeconds()), Long.valueOf(state.getKills()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!(sender instanceof Player)
                || !sender.hasPermission("qingcharms.progression")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return startsWith(ACTIONS, args[0]);
        }
        if (args.length == 2 && !"help".equalsIgnoreCase(args[0])) {
            Player player = (Player) sender;
            List<String> slots = new ArrayList<String>(
                    plugin.getDataManager().loadPlayer(player.getUniqueId())
                            .getEquippedSlotIds());
            slots.add("hand");
            return startsWith(slots, args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> startsWith(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.US);
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value.toLowerCase(Locale.US).startsWith(prefix)) {
                result.add(value);
            }
        }
        return result;
    }
}
