package fr.elias.ultimateChristmas.effects;

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
 * Player-local snowfall + ground pre-snow with safe-zones to avoid stealing right-clicks
 * on interactables (e.g., Santa, holograms, gifts, frames). Lightweight & configurable.
 */
public class SnowEffectManager implements Listener {

    private final Plugin plugin;

    // State
    private final Set<Player> activePlayers = new HashSet<>();
    private final Set<Player> optedOut = new HashSet<>();

    // Config
    private final boolean snowEnabled;
    private final boolean applyToAllPlayers;
    private final boolean keepSnowOnDisable; // if false, we attempt to clean thin layers when a player disables
    private final int radius;                 // horizontal radius around player we affect
    private final int particleDensity;        // snowflake particles / tick burst
    private final long duration;              // per-player duration in ticks (0 = infinite)
    private final double noSnowNearPlayer;    // small ring around feet (prevents click-steal)
    private final double noSnowNearEntity;    // avoid placing near interactables
    private final boolean biasFront;          // pre-snow looks ahead, not behind
    private final double lookAheadFactor;     // how far in front (relative to radius)

    public SnowEffectManager(Plugin plugin) {
        this.plugin = plugin;

        // Read config (with sane defaults)
        var cfg = plugin.getConfig();
        this.snowEnabled        = cfg.getBoolean("snow.enabled", false);
        this.applyToAllPlayers  = cfg.getBoolean("snow.apply-to-all-players", true);
        this.keepSnowOnDisable  = cfg.getBoolean("snow.keep-snow-on-disable", true);
        this.radius             = Math.max(2, cfg.getInt("snow.radius", 6));
        this.particleDensity    = Math.max(0, cfg.getInt("snow.density", 40));
        int seconds             = cfg.getInt("snow.duration", 0);
        this.duration           = seconds <= 0 ? 0L : seconds * 20L;

        // Safe-zones
        this.noSnowNearPlayer   = Math.max(0.5, cfg.getDouble("snow.no-snow-near-player-radius", 1.5));
        this.noSnowNearEntity   = Math.max(0.5, cfg.getDouble("snow.no-snow-near-entity-radius", 1.25));

        // Look-ahead shaping
        this.biasFront          = cfg.getBoolean("snow.presnow.bias-front", true);
        this.lookAheadFactor    = Math.min(2.0, Math.max(0.0, cfg.getDouble("snow.presnow.lookahead-factor", 0.66)));

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Auto-enable for those online if globally enabled
        if (snowEnabled && applyToAllPlayers) {
            Bukkit.getScheduler().runTaskLater(plugin, this::enableForOnlinePlayers, 40L);
        }
    }

    /* ---------------------------------------------------------
     * Public hooks used by your /snowify command
     * --------------------------------------------------------- */

    /** Called by the /snowify command to toggle per-player preference. */
    public void toggleSnowCommand(Player player) {
        if (optedOut.contains(player)) {
            optedOut.remove(player);
            player.sendMessage("§b❄ You’ve re-enabled the snow effect!");
            enableFor(player);
            return;
        }
        if (activePlayers.contains(player)) {
            disableFor(player, !keepSnowOnDisable); // optionally clean up local thin snow
            optedOut.add(player);
            player.sendMessage("§7☁ Snow effect disabled for you.");
            return;
        }
        enableFor(player);
        player.sendMessage("§f❄ Snow begins to fall around you...");
    }

    /* ---------------------------------------------------------
     * Auto-enable on join if globally enabled
     * --------------------------------------------------------- */

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

    /* ---------------------------------------------------------
     * Core loop
     * --------------------------------------------------------- */

    private void enableFor(Player player) {
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

                // 1) Snowfall visuals
                spawnSnowParticles(player);

                // 2) Pre-snow the ground (every 10 ticks for light cost)
                if ((ticks % 10) == 0) {
                    applyPreSnow(player);
                }

                ticks += 2;
                if (duration > 0 && ticks > duration) {
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

    /* ---------------------------------------------------------
     * Visual snowfall
     * --------------------------------------------------------- */

    private void spawnSnowParticles(Player player) {
        if (particleDensity <= 0) return;
        World world = player.getWorld();
        Location base = player.getLocation();

        for (int i = 0; i < particleDensity; i++) {
            double x = base.getX() + (Math.random() * radius * 2 - radius);
            double y = base.getY() + 3 + Math.random() * 2;
            double z = base.getZ() + (Math.random() * radius * 2 - radius);
            world.spawnParticle(Particle.SNOWFLAKE, x, y, z, 1, 0, 0, 0, 0.01);
        }
    }

    /* ---------------------------------------------------------
     * Pre-snow ground (safe around player & interactables)
     * --------------------------------------------------------- */

    private void applyPreSnow(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        int px = loc.getBlockX();
        int pz = loc.getBlockZ();

        // Look-ahead vector (optional)
        Vector dir = loc.getDirection().normalize();
        int lookAheadX = biasFront ? (int) (dir.getX() * (radius * lookAheadFactor)) : 0;
        int lookAheadZ = biasFront ? (int) (dir.getZ() * (radius * lookAheadFactor)) : 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {

                // Bias: skip a chunk directly behind player for a nicer forward “pre-snow” look
                if (biasFront && dx < -radius / 2 && Math.abs(dz) < radius / 2) continue;

                int bx = px + dx + lookAheadX;
                int bz = pz + dz + lookAheadZ;

                // Tiny keep-clear ring around feet (prevents click-steal near player)
                if (Math.abs(bx - px) <= noSnowNearPlayer && Math.abs(bz - pz) <= noSnowNearPlayer) {
                    continue;
                }

                Block ground = world.getHighestBlockAt(bx, bz);
                Block top = ground.getRelative(0, 1, 0);

                // Require solid ground, air above (don’t overwrite blocks)
                if (!ground.getType().isSolid() || ground.isLiquid()) continue;
                if (top.getType() != Material.AIR) continue;

                // Avoid placing near interactables (Santa, holograms, item frames, villager gifts…)
                if (isNearInteractable(top.getLocation().add(0.5, 0.5, 0.5), noSnowNearEntity, 1.5)) {
                    continue;
                }

                // Place a thin snow layer (no physics to keep it cheap)
                top.setType(Material.SNOW, false);
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
        // Your Santa?
        if (UltimateChristmas.get() != null && UltimateChristmas.get().isSantaEntity(e)) return true;

        // Common interactables
        return (e instanceof Villager)
                || (e instanceof ArmorStand)         // holograms / gift stands
                || (e instanceof ItemFrame)
                || (e instanceof GlowItemFrame)
                || (isInteractionEntity(e));         // 1.19+ interaction hitbox
    }

    private boolean isInteractionEntity(Entity e) {
        // Keep it reflection-safe for versions without this class
        return e.getType().name().equalsIgnoreCase("INTERACTION");
    }

    /* ---------------------------------------------------------
     * Cleanup helper (only thin snow layers we likely placed)
     * --------------------------------------------------------- */

    private void revertThinSnowNear(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation();
        int px = base.getBlockX();
        int pz = base.getBlockZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block top = world.getHighestBlockAt(px + dx, pz + dz);
                if (top.getType() == Material.SNOW) {
                    // Only remove the thin layer (do not touch SNOW_BLOCK)
                    top.setType(Material.AIR, false);
                }
            }
        }
    }
}
