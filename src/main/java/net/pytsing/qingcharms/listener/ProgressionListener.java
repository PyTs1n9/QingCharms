package net.pytsing.qingcharms.listener;

import net.pytsing.qingcharms.QingCharmsPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ProgressionListener implements Listener {

    private final QingCharmsPlugin plugin;

    public ProgressionListener(QingCharmsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer != null && !(entity instanceof Player)) {
            plugin.getProgressionService().recordKill(killer);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getProgressionService().refreshEquipment(event.getPlayer());
    }
}
