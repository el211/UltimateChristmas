// File: src/main/java/fr/elias/ultimateChristmas/integration/WorldGuardIntegration.java
package fr.elias.ultimateChristmas.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import fr.elias.ultimateChristmas.util.Debug;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.NoSuchElementException;

public class WorldGuardIntegration {

    private final Plugin plugin;
    private final boolean isEnabled;

    public WorldGuardIntegration(Plugin plugin) {
        this.plugin = plugin;
        this.isEnabled = plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard");
    }

    public boolean isEnabled() { return isEnabled; }

    public ProtectedRegion getRegion(World world, String regionId) {
        if (!isEnabled || world == null) {
            Debug.warn("WG: disabled or world null. enabled=" + isEnabled + ", world=" + (world == null ? "null" : world.getName()));
            return null;
        }
        try {
            var container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            var rm = container.get(BukkitAdapter.adapt(world));
            if (rm == null) {
                Debug.warn("WG: RegionManager is null for world=" + world.getName());
                return null;
            }
            ProtectedRegion pr = rm.getRegion(regionId);
            if (pr == null) {
                Debug.warn("WG: region '" + regionId + "' not found in world=" + world.getName());
            } else {
                BlockVector3 min = pr.getMinimumPoint();
                BlockVector3 max = pr.getMaximumPoint();
                Debug.info("WG: region '" + regionId + "' bounds min=" + min + " max=" + max);
            }
            return pr;
        } catch (NoSuchElementException | NullPointerException e) {
            Debug.warn("WG: exception while retrieving region '" + regionId + "': " + e.getMessage());
            return null;
        } catch (Exception e) {
            Debug.error("WG: API error: " + e.getMessage());
            return null;
        }
    }
}
