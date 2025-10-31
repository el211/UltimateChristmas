package fr.elias.ultimateChristmas.santa;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.util.*;

public final class SantaFooting {
    private static final Set<Material> SLIPPERY = Set.of(
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE
    );

    // Track changed blocks so we can revert later
    private static final Deque<Changed> CHANGED = new ArrayDeque<>();
    private static final int MAX_TRACK = 1024;            // larger ring memory
    private static final long REVERT_AFTER_MS = 15_000;   // keep pad longer (~15s)

    private record Changed(World w, int x, int y, int z, Material oldMat, long time) {
        boolean expired(long now) { return now - time > REVERT_AFTER_MS; }
    }

    /** Call this every tick while Santa walks. */
    public static void ensureGrip(Mob santa) {
        Location loc = santa.getLocation();
        World w = loc.getWorld(); if (w == null) return;

        int cx = loc.getBlockX();
        int cy = loc.getBlockY() - 1;
        int cz = loc.getBlockZ();

        boolean touchedSlippery = false;

        // Build a 3×3 “carpet” under/around Santa
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block base = w.getBlockAt(cx + dx, cy, cz + dz);
                if (SLIPPERY.contains(base.getType())) {
                    touchedSlippery = true;

                    // Convert the base (ICE -> SNOW_BLOCK) for friction
                    rememberAndSet(base, Material.SNOW_BLOCK);

                    // Remove the thin snow one block above (if any), so Santa stands flush
                    Block above = base.getRelative(0, 1, 0);
                    if (above.getType() == Material.SNOW) {
                        rememberAndSet(above, Material.AIR);
                    }
                }
            }
        }

        // If we interacted with slippery ground, damp horizontal velocity to kill the slide
        if (touchedSlippery) {
            Vector v = santa.getVelocity();
            santa.setVelocity(new Vector(v.getX() * 0.30, v.getY(), v.getZ() * 0.30));
        }

        // Periodically revert old pad pieces (cheap)
        revertOld();
    }

    private static void rememberAndSet(Block b, Material newMat) {
        if (b.getType() == newMat) return;
        CHANGED.addLast(new Changed(b.getWorld(), b.getX(), b.getY(), b.getZ(), b.getType(), System.currentTimeMillis()));
        if (CHANGED.size() > MAX_TRACK) CHANGED.removeFirst();
        b.setType(newMat, false); // no physics
    }

    /** Revert everything we ever changed (use on despawn/shutdown). */
    public static void revertAll() {
        while (!CHANGED.isEmpty()) {
            Changed c = CHANGED.removeFirst();
            Block b = c.w().getBlockAt(c.x(), c.y(), c.z());
            if (b.getType() != c.oldMat()) b.setType(c.oldMat(), false);
        }
    }

    /** Revert only the oldest entries whose timer elapsed. */
    private static void revertOld() {
        long now = System.currentTimeMillis();
        while (!CHANGED.isEmpty() && CHANGED.peekFirst().expired(now)) {
            Changed c = CHANGED.removeFirst();
            Block b = c.w().getBlockAt(c.x(), c.y(), c.z());
            if (b.getType() != c.oldMat()) b.setType(c.oldMat(), false);
        }
    }
}
