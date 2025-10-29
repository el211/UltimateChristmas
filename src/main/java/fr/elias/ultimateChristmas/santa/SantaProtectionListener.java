package fr.elias.ultimateChristmas.santa;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.entity.Entity;

public class SantaProtectionListener implements Listener {

    private final SantaManager santaManager;
    private final UltimateChristmas plugin;

    public SantaProtectionListener(UltimateChristmas plugin, SantaManager santaManager) {
        this.plugin = plugin;
        this.santaManager = santaManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (santaManager.isSanta(victim)) {
            // Debug so we know if something is trying to hurt him
            plugin.getLogger().info("[UltimateChristmas] [DEBUG] SantaProtectionListener: cancelled generic damage "
                    + event.getCause() + " amount=" + event.getDamage());
            event.setCancelled(true);
            event.setDamage(0.0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (santaManager.isSanta(victim)) {
            plugin.getLogger().info("[UltimateChristmas] [DEBUG] SantaProtectionListener: cancelled PvP/PvE damage from "
                    + event.getDamager().getType() + " amount=" + event.getDamage());
            event.setCancelled(true);
            event.setDamage(0.0);
        }
    }
}
