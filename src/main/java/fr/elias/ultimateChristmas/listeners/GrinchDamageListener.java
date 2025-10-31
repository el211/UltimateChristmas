package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.boss.GrinchBossManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class GrinchDamageListener implements Listener {

    private final UltimateChristmas plugin;
    private final GrinchBossManager boss;

    public GrinchDamageListener(UltimateChristmas plugin, GrinchBossManager boss) {
        this.plugin = plugin;
        this.boss = boss;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (!boss.isAlive() || boss.getEntity() == null || !victim.getUniqueId().equals(boss.getEntity().getUniqueId()))
            return;

        Player damager = null;
        if (e.getDamager() instanceof Player p) damager = p;
        else if (e.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) damager = p;

        if (damager != null) {
            boss.recordDamage(damager, e.getFinalDamage(), e.getCause());
        }
    }
}
