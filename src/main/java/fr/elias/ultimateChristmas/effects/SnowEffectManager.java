package fr.elias.ultimateChristmas.effects;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

/**
 * Player-local snowfall manager:
 * - Particles always visible around the player.
 * - Thin ground snow layers placed near the player, except in blocked WG regions or under a roof.
 * - Safe-zones near players and interactables to avoid misclicks.
 * - Toggleable per player via /snowify (handled by your command).
 */
public class SnowEffectManager implements Listener {

    private final Plugin plugin;

    // Runtime state
    private final Set<Player> activePlayers = new HashSet<>();
    private final Set<Player> optedOut = new HashSet<>();

    // Config
    private final boolean snowEnabled;
    private final boolean applyToAllPlayers;
    private final boolean keepSnowOnDisable;
    private final int radius;
    private final int particleDensity;
    private final long durationTicks;
    private final double noSnowNearPlayer;
    private final double noSnowNearEntity;

    private final boolean biasFront;
    private final double lookAheadFactor;

    // Ground placement rules
    private final boolean respectRoof;              // do not place ground snow when under a cover
    private final Set<String> blockedWGRegions;     // WG regions where ground snow is blocked

    public SnowEffectManager(Plugin plugin) {
        this.plugin = plugin;

        // Read config
        var cfg = plugin.getConfig();

        this.snowEnabled        = cfg.getBoolean("snow.enabled", true);
        this.applyToAllPlayers  = cfg.getBoolean("snow.apply-to-all-players", true);
        this.keepSnowOnDisable  = cfg.getBoolean("snow.keep-snow-on-disable", true);

        this.radius             = Math.max(2, cfg.getInt("snow.radius", 24));
        this.particleDensity    = Math.max(0, cfg.getInt("snow.density", 40));

        int seconds             = Math.max(0, cfg.getInt("snow.duration", 0));
        this.durationTicks      = seconds == 0 ? 0L : seconds * 20L;

        this.noSnowNearPlayer   = Math.max(0.25, cfg.getDouble("snow.no-snow-near-player-radius", 1.5));
        this.noSnowNearEntity   = Math.max(0.25, cfg.getDouble("snow.no-snow-near-entity-radius", 1.25));

        this.biasFront          = cfg.getBoolean("snow.presnow.bias-front", true);
        this.lookAheadFactor    = Math.min(2.0, Math.max(0.0, cfg.getDouble("snow.presnow.lookahead-factor", 0.66)));

        this.respectRoof        = cfg.getBoolean("snow.respect-roof", true);
        this.blockedWGRegions   = new HashSet<>(cfg.getStringList("snow.blocked-wg-regions"));

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Auto-enable around currently online players if globally enabled
        if (snowEnabled && applyToAllPlayers) {
            Bukkit.getScheduler().runTaskLater(plugin, this::enableForOnlinePlayers, 40L);
        }
    }

    /* =============================== Public API (used by /snowify) =============================== */

    /** Toggle the effect for a specific player. */
    public void toggleSnowCommand(Player player) {
        if (optedOut.contains(player)) {
            optedOut.remove(player);
            player.sendMessage("§b❄ Snow effect re-enabled.");
            enableFor(player);
            return;
        }
        if (activePlayers.contains(player)) {
            disableFor(player, !keepSnowOnDisable);
            optedOut.add(player);
            player.sendMessage("§7☁ Snow effect disabled for you.");
            return;
        }
        enableFor(player);
        player.sendMessage("§f❄ Snow begins to fall around you...");
    }

    /* =============================== Join auto-enable =============================== */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (snowEnabled && applyToAllPlayers && !optedOut.contains(p)) {
            enableFor(p);
        }
    }

    public void enableForOnlinePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!optedOut.contains(p)) enableFor(p);
        }
    }

    /* =============================== Core loop =============================== */

    private void enableFor(Player player) {
        if (!snowEnabled) return;
        if (activePlayers.contains(player)) return;

        activePlayers.add(player);

        new BukkitRunnable() {
            long ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !activePlayers.contains(player)) {
                    cancel();
                    return;
                }

                // 1) Always show particles (even in blocked regions)
                spawnSnowParticles(player);

                // 2) Place thin ground snow (every 10 ticks), but NEVER inside blocked WG regions
                if ((ticks % 10) == 0) {
                    applyPreSnow(player);
                }

                ticks += 2;

                // 3) Optional auto-timeout per player
                if (durationTicks > 0 && ticks > durationTicks) {
                    disableFor(player, !keepSnowOnDisable);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void disableFor(Player player, boolean cleanupArea) {
        activePlayers.remove(player);
        if (cleanupArea) {
            revertThinSnowNear(player);
        }
    }

    /* =============================== Particles =============================== */

    private void spawnSnowParticles(Player player) {
        if (particleDensity <= 0) return;
        World world = player.getWorld();
        Location base = player.getLocation();

        for (int i = 0; i < particleDensity; i++) {
            double x = base.getX() + (Math.random() * radius * 2 - radius);
            double y = base.getY() + 3.0 + Math.random() * 2.5;
            double z = base.getZ() + (Math.random() * radius * 2 - radius);
            world.spawnParticle(Particle.SNOWFLAKE, x, y, z, 1, 0, 0, 0, 0.01);
        }
    }

    /* =============================== Ground pre-snow =============================== */

    private void applyPreSnow(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();

        // Skip ground placement when under a roof (configurable)
        if (respectRoof && isUnderCover(player)) return;

        int px = loc.getBlockX();
        int pz = loc.getBlockZ();

        // Optional look-ahead shaping (places more in front of the player)
        Vector dir = loc.getDirection().normalize();
        int lookAheadX = biasFront ? (int) (dir.getX() * (radius * lookAheadFactor)) : 0;
        int lookAheadZ = biasFront ? (int) (dir.getZ() * (radius * lookAheadFactor)) : 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {

                // Light “front bias” mask (skip some behind the player)
                if (biasFront && dx < -radius / 2 && Math.abs(dz) < radius / 2) continue;

                int bx = px + dx + lookAheadX;
                int bz = pz + dz + lookAheadZ;

                // Keep a small click-safe ring around the player's feet
                if (Math.abs(bx - px) <= noSnowNearPlayer && Math.abs(bz - pz) <= noSnowNearPlayer) continue;

                Block ground = world.getHighestBlockAt(bx, bz);
                Block top = ground.getRelative(0, 1, 0);

                if (!ground.getType().isSolid() || ground.isLiquid()) continue;
                if (top.getType() != Material.AIR) continue;

                // DO NOT place ground snow inside any configured WG region
                if (isInsideBlockedWGRegion(top.getLocation().add(0.5, 0.0, 0.5))) continue;

                // Avoid interactables and Santa (so we don't steal right-clicks)
                if (isNearInteractable(top.getLocation().add(0.5, 0.5, 0.5), noSnowNearEntity, 1.5)) continue;

                top.setType(Material.SNOW, false); // thin layer
            }
        }
    }

    private boolean isNearInteractable(Location center, double xr, double yr) {
        World w = center.getWorld();
        if (w == null) return false;

        for (Entity e : w.getNearbyEntities(center, xr, yr, xr)) {
            if (isInteractableOrSanta(e)) return true;
        }
        return false;
    }

    private boolean isInteractableOrSanta(Entity e) {
        // Treat our Santa as an interactable target as well
        try {
            UltimateChristmas uc = UltimateChristmas.get();
            if (uc != null && uc.isSantaEntity(e)) return true;
        } catch (Throwable ignored) {}

        EntityType type = e.getType();
        if (e instanceof Villager) return true;
        if (e instanceof ArmorStand) return true;
        if (e instanceof ItemFrame) return true;
        if (type.name().equalsIgnoreCase("GLOW_ITEM_FRAME")) return true; // version-safe
        return isInteractionEntity(e);
    }

    private boolean isInteractionEntity(Entity e) {
        // Version-safe check for the Interaction entity (no hard dependency)
        return e.getType().name().equalsIgnoreCase("INTERACTION");
    }

    /* =============================== Cleanup =============================== */

    private void revertThinSnowNear(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation();
        int px = base.getBlockX();
        int pz = base.getBlockZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block top = world.getHighestBlockAt(px + dx, pz + dz);
                if (top.getType() == Material.SNOW) {
                    top.setType(Material.AIR, false); // only remove thin layer, not SNOW_BLOCK
                }
            }
        }
    }

    /* =============================== Helpers =============================== */

    /**
     * True if any non-air block exists above the player's head (i.e., under a roof).
     */
    private boolean isUnderCover(Player p) {
        Location l = p.getLocation();
        World w = l.getWorld();
        if (w == null) return false;

        int x = l.getBlockX();
        int z = l.getBlockZ();
        int startY = Math.min(w.getMaxHeight() - 1, l.getBlockY() + 1);

        for (int y = startY; y < w.getMaxHeight(); y++) {
            Material m = w.getBlockAt(x, y, z).getType();
            if (m != Material.AIR && m != Material.CAVE_AIR && m != Material.VOID_AIR) return true;
        }
        return false;
    }

    /**
     * Returns true if the location is inside any configured WorldGuard region to block ground snow.
     * Particles are NOT affected by this — only ground placement is skipped.
     */
    private boolean isInsideBlockedWGRegion(Location loc) {
        if (blockedWGRegions.isEmpty() || loc == null || loc.getWorld() == null) return false;

        try {
            if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return false;

            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

            for (var region : set.getRegions()) {
                String id = region.getId();
                for (String blocked : blockedWGRegions) {
                    if (id.equalsIgnoreCase(blocked)) return true;
                }
            }
        } catch (Throwable ignored) {
            // Stay resilient to WG API changes
        }
        return false;
    }
}
