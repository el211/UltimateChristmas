package fr.elias.ultimateChristmas.listeners;

import fr.elias.ultimateChristmas.boss.GrinchBossManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class GrinchDeathListener implements Listener {
    private final GrinchBossManager boss;

    public GrinchDeathListener(fr.elias.ultimateChristmas.UltimateChristmas plugin, GrinchBossManager boss) {
        this.boss = boss;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        if (boss.isAlive() && boss.getEntity() != null &&
                e.getEntity().getUniqueId().equals(boss.getEntity().getUniqueId())) {
            // let the boss handle chest + rewards
            boss.handleDeathAndRewards();
            // prevent vanilla drops/xp if you want:
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }
}
