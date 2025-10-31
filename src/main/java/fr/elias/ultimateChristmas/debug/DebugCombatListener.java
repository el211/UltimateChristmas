// fr/elias/ultimateChristmas/debug/DebugCombatListener.java
package fr.elias.ultimateChristmas.debug;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldedit.bukkit.BukkitAdapter;

public class DebugCombatListener implements Listener {

    private String wgLine(Player p) {
        try {
            var query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            var loc = BukkitAdapter.adapt(p.getLocation());
            ApplicableRegionSet set = query.getApplicableRegions(loc);
            var sb = new StringBuilder();
            sb.append("WG[in=");
            boolean first = true;
            for (var r : set.getRegions()) { if(!first) sb.append(","); sb.append(r.getId()); first=false; }
            StateFlag mobDamage = com.sk89q.worldguard.protection.flags.Flags.MOB_DAMAGE;
            StateFlag inv = com.sk89q.worldguard.protection.flags.Flags.INVINCIBILITY; // ✅
            var md = set.queryState(null, mobDamage);
            var iv = set.queryState(null, inv);
            sb.append("] mob-damage=").append(md==null?"DEFAULT":md.name());
            sb.append(" invincible=").append(iv==null?"DEFAULT":iv.name());
            return sb.toString();
        } catch (Throwable t) {
            return "WG[n/a]";
        }
    }

    private void header(Player p, String tag) {
        GameMode gm = p.getGameMode();
        Difficulty df = p.getWorld().getDifficulty();
        boolean invul = p.isInvulnerable();
        System.out.println("[UC-DEBUG] " + tag +
                " player=" + p.getName() +
                " gm=" + gm + " diff=" + df +
                " invulnerable=" + invul + " " + wgLine(p));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player p) {
            header(p, "EntityTargetEvent reason=" + e.getReason() + " from=" + e.getEntityType());
            if (e.isCancelled()) System.out.println("[UC-DEBUG] -> Target CANCELLED");
            if (e.getTarget()==null) System.out.println("[UC-DEBUG] -> Target set to NULL");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p) {
            header(p, "EntityDamageByEntityEvent cause=" + e.getCause() +
                    " damager=" + e.getDamager().getType() +
                    " raw=" + e.getDamage() + " final=" + e.getFinalDamage());
            if (e.isCancelled()) System.out.println("[UC-DEBUG] -> Damage CANCELLED");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onAnyDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.isCancelled()) {
            header(p, "EntityDamageEvent CANCELLED cause=" + e.getCause());
        }
    }
}
