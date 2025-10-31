package fr.elias.ultimateChristmas.commands;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.boss.GrinchBossManager;
import fr.elias.ultimateChristmas.integration.WorldGuardIntegration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GrinchBossCommand implements CommandExecutor, TabCompleter {

    private final UltimateChristmas plugin;
    private final GrinchBossManager boss;
    private final WorldGuardIntegration wg;

    public GrinchBossCommand(UltimateChristmas plugin, GrinchBossManager boss, WorldGuardIntegration wg) {
        this.plugin = plugin;
        this.boss = boss;
        this.wg = wg;
    }

    // ============================ Command ============================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            err(sender, "Players only.");
            return true;
        }
        if (!p.hasPermission("ultimatechristmas.grinch.admin")) {
            err(p, "No permission.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(p, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> {
                if (args.length < 3) {
                    err(p, "Usage: /" + label + " spawn <world> <regionId>");
                    return true;
                }
                World w = Bukkit.getWorld(args[1]);
                if (w == null) {
                    err(p, "World not found: " + args[1]);
                    return true;
                }
                ProtectedRegion region = wg.getRegion(w, args[2]);
                if (region == null) {
                    err(p, "Region not found in world '" + w.getName() + "': " + args[2]);
                    return true;
                }

                Location spawnLoc = centerOnGround(w, region);
                if (spawnLoc == null) {
                    err(p, "Could not find a safe ground position in that region.");
                    return true;
                }

                boolean ok = boss.spawn(spawnLoc, region);
                if (ok) ok(p, "Grinch spawned at " + coords(spawnLoc) + " in region '" + region.getId() + "'.");
                else err(p, "Grinch is already alive.");
            }

            case "despawn" -> {
                boss.despawn();
                ok(p, "Grinch despawned.");
            }

            case "setspawn" -> {
                if (args.length < 3) {
                    err(p, "Usage: /" + label + " setspawn <cooldownMinutes> <stayMinutes>");
                    return true;
                }
                long cooldownMin, stayMin;
                try {
                    cooldownMin = Long.parseLong(args[1]);
                    stayMin = Long.parseLong(args[2]);
                } catch (NumberFormatException ex) {
                    err(p, "Numbers expected. Example: /" + label + " setspawn 30 10");
                    return true;
                }

                boss.setAutoSpawnAt(p.getLocation(), cooldownMin, stayMin);
                ok(p, "Saved auto-spawn at your position " + coords(p.getLocation())
                        + " (cooldown " + cooldownMin + " min, stay " + stayMin + " min).");
            }

            case "status" -> {
                boolean alive = boss.isAlive();
                if (alive) {
                    ok(p, "Grinch status: §aALIVE§r.");
                } else {
                    info(p, "Grinch status: §cNOT SPAWNED§r.");
                }
            }

            default -> help(p, label);
        }

        return true;
    }

    // ============================ Tab Complete ============================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            out.add("spawn");
            out.add("despawn");
            out.add("setspawn");
            out.add("status");
            out.add("help");
            return filter(out, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            for (World w : Bukkit.getWorlds()) out.add(w.getName());
            return filter(out, args[1]);
        }

        return out;
    }

    // ============================ Helpers ============================

    private Location centerOnGround(World w, ProtectedRegion r) {
        if (w == null || r == null) return null;
        BlockVector3 min = r.getMinimumPoint();
        BlockVector3 max = r.getMaximumPoint();
        double cx = (min.x() + max.x()) / 2.0;
        double cz = (min.z() + max.z()) / 2.0;
        int y = w.getHighestBlockYAt((int) Math.floor(cx), (int) Math.floor(cz));
        return new Location(w, cx + 0.5, y + 1, cz + 0.5);
        // (If you want stricter safety checks, add passable/solid checks here.)
    }

    private List<String> filter(List<String> base, String token) {
        if (token == null || token.isEmpty()) return base;
        String low = token.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : base) if (s.toLowerCase().startsWith(low)) out.add(s);
        return out;
    }

    private String coords(Location l) {
        return "(" + l.getWorld().getName() + " "
                + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ")";
    }

    private String prefix() {
        String p = "";
        try {
            p = plugin.getConfig("config.yml").getString("messages.prefix", "");
        } catch (Throwable ignored) {}
        return color(p);
    }

    private String color(String s) {
        return s == null ? "" : s.replace('&', '§');
    }

    private void info(CommandSender to, String msg) { to.sendMessage(prefix() + color(msg)); }
    private void ok(CommandSender to, String msg)   { to.sendMessage(prefix() + "§a" + color(msg)); }
    private void err(CommandSender to, String msg)  { to.sendMessage(prefix() + "§c" + color(msg)); }

    private void help(CommandSender to, String label) {
        info(to, "§e/" + label + " spawn <world> <regionId>§7 — spawn Grinch at the region center.");
        info(to, "§e/" + label + " despawn§7 — force despawn.");
        info(to, "§e/" + label + " setspawn <cooldownMin> <stayMin>§7 — save auto-spawn at your location (bosssata.json).");
        info(to, "§e/" + label + " status§7 — show boss state.");
    }
}
