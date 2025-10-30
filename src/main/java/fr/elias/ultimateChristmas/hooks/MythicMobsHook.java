package fr.elias.ultimateChristmas.hooks;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Optional;

public final class MythicMobsHook {
    private MythicMobsHook() {}

    public static boolean isPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs") && MythicBukkit.inst() != null;
    }

    /** Returns internal name (e.g. "SkeletalKnight") or null. */
    public static String getInternalName(Entity e) {
        if (!(e instanceof LivingEntity)) return null;
        try {
            Optional<ActiveMob> opt = MythicBukkit.inst().getMobManager().getActiveMob(e.getUniqueId());
            if (opt.isEmpty()) return null;
            String internal = opt.get().getType().getInternalName();
            return (internal == null || internal.isBlank()) ? null : internal;
        } catch (Throwable t) {
            return null;
        }
    }
}
