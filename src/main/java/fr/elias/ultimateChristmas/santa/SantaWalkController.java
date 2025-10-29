package fr.elias.ultimateChristmas.santa;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

/**
 * SantaWalkController (hard leash version)
 *
 * - Runs every 5 ticks (~0.25s) instead of every 40 ticks.
 * - Picks random walk targets INSIDE the WG region.
 * - Uses Paper pathfinder to walk there.
 * - If Santa is ever outside region, he is immediately teleported
 *   back to a "leashHome" point (the region's center-on-ground),
 *   not 2 seconds later, basically instantly.
 */
public class SantaWalkController {

    private final UltimateChristmas plugin;
    private final Mob santa;
    private final ProtectedRegion region;
    private final Random random = new Random();
    private BukkitTask walkTask;

    // speed for santa.getPathfinder().moveTo()
    private final double walkSpeed = 0.4D;

    // Strong leash point: safe ground near the region center
    private Location leashHome;

    public SantaWalkController(UltimateChristmas plugin, Mob santa, ProtectedRegion region) {
        this.plugin = plugin;
        this.santa = santa;
        this.region = region;
    }

    public void startWalking() {
        if (region == null) {
            debug("WalkController: region == null; Santa will NOT walk.");
            return;
        }
        if (santa == null || santa.isDead() || !santa.isValid()) {
            debug("WalkController: santa invalid at start; abort.");
            return;
        }

        // build leashHome = ground at region center
        leashHome = computeRegionCenterGround();
        if (leashHome == null) {
            // fallback to santa spawn loc if region lookup failed somehow
            leashHome = santa.getLocation().clone();
        }

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        debug("WalkController: START for " + santa.getUniqueId() +
                " at " + loc(santa.getLocation()) +
                " bounds min=" + vec(min) + " max=" + vec(max) +
                " leashHome=" + loc(leashHome));

        walkTask = new BukkitRunnable() {
            @Override
            public void run() {

                // if Santa is gone, stop
                if (santa.isDead() || !santa.isValid()) {
                    debug("WalkController: santa invalid/dead; stopping.");
                    stopWalking();
                    return;
                }

                // HARD LEASH: if he's outside the region at ALL, snap him home instantly.
                if (!insideRegion(santa.getLocation())) {
                    debug("WalkController: OUTSIDE region -> TP leashHome " + loc(leashHome));
                    santa.teleport(leashHome);
                    // after teleport, kill any existing path so he doesn't try to walk back out following old path
                    try {
                        if (santa.getPathfinder().hasPath()) {
                            santa.getPathfinder().stopPathfinding();
                        }
                    } catch (Throwable ignored) {}
                }

                // Path maintenance / new wander goal
                try {
                    boolean hasPath = santa.getPathfinder().hasPath();
                    boolean forceNewTarget = (random.nextInt(100) < 5); // ~5% random repath
                    if (!hasPath || forceNewTarget) {
                        pickNewTargetAndWalk();
                    }
                } catch (Throwable t) {
                    debug("WalkController: pathfinder error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                    stopWalking();
                }
            }
        }.runTaskTimer(plugin, 5L, 5L); // every 5 ticks (~0.25s)
    }

    public void stopWalking() {
        if (walkTask != null) {
            walkTask.cancel();
            walkTask = null;
            debug("WalkController: STOPPED for " + santa.getUniqueId());
        }
    }

    /**
     * Picks a random valid ground location inside region and tells the mob to walk there.
     * If nothing good after 20 tries, we try leashHome instead.
     */
    private void pickNewTargetAndWalk() {
        Location best = null;

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        World world = santa.getWorld();

        for (int i = 0; i < 20; i++) {
            double randX = min.x() + random.nextDouble() * (max.x() - min.x());
            double randZ = min.z() + random.nextDouble() * (max.z() - min.z());

            double minY = min.y();
            double maxY = max.y();

            Location ground = findGround(world, randX, randZ, minY, maxY);
            if (ground == null) continue;
            if (!insideRegion(ground)) continue;

            best = ground;
            break;
        }

        if (best == null) {
            // no random candidate, use leashHome
            best = leashHome;
        }

        if (best == null) return; // absolutely nothing to do

        // update leashHome to something deeper inside region (helps keep teleports inside, not at edge)
        if (insideRegion(best)) {
            leashHome = best.clone();
        }

        try {
            santa.getPathfinder().moveTo(best, walkSpeed);
            debug("WalkController: moveTo " + loc(best) + " speed=" + walkSpeed);
        } catch (Throwable t) {
            debug("WalkController: moveTo error " + t.getMessage());
        }
    }

    /**
     * Find solid ground at (x,z) between minY..maxY and return a spot where the mob can stand.
     */
    private Location findGround(World world, double x, double z, double minY, double maxY) {
        int top = (int) Math.min(maxY, world.getMaxHeight());
        int bottom = (int) Math.max(1, minY);

        for (int y = top; y >= bottom; y--) {
            Location test = new Location(world, x, y, z);

            // block at y must be solid
            if (!test.getBlock().isSolid()) continue;

            // block above must be air/passable
            if (!test.clone().add(0, 1, 0).getBlock().isPassable()) continue;

            // stand one above that solid block, centered
            return test.clone().add(0.5, 1, 0.5);
        }
        return null;
    }

    /**
     * Compute a leash "home" position: center of region's bounding box, snapped to ground.
     * This is the point we TP Santa back to if he slips outside.
     */
    private Location computeRegionCenterGround() {
        if (region == null) return null;
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        World w = santa.getWorld();
        if (w == null) return null;

        double centerX = (min.x() + max.x()) / 2.0;
        double centerZ = (min.z() + max.z()) / 2.0;
        double minY = min.y();
        double maxY = max.y();

        return findGround(w, centerX, centerZ, minY, maxY);
    }

    /**
     * Check if a Location is inside the WG region (using block coords).
     */
    private boolean insideRegion(Location loc) {
        if (loc == null || loc.getWorld() == null || region == null) return false;
        BlockVector3 vec = BlockVector3.at(loc.getX(), loc.getY(), loc.getZ());
        return region.contains(vec);
    }

    private void debug(String msg) {
        try {
            boolean dbg = plugin.getConfig().getBoolean("debug", false);
            if (dbg) {
                plugin.getLogger().info("[DEBUG] " + msg);
            }
        } catch (Throwable ignored) {}
    }

    private String loc(Location l) {
        if (l == null || l.getWorld() == null) return "(null)";
        return "(" + l.getWorld().getName() + " "
                + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ")";
    }

    private String vec(BlockVector3 v) {
        if (v == null) return "(null)";
        return "(" + v.x() + "," + v.y() + "," + v.z() + ")";
    }
}
