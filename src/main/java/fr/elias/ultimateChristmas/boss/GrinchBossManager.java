package fr.elias.ultimateChristmas.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.santa.SantaPresentFactory;
import fr.elias.ultimateChristmas.util.WeightedRandomPicker;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.PlayerWatcher;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GrinchBossManager {

    private final UltimateChristmas plugin;
    private final Random rng = new Random();

    // Active state
    private UUID grinchUUID;
    private Mob grinch;
    private ProtectedRegion leashRegion;

    // Damage tracking (for fair rewards)
    private final Map<UUID, Double> damageDone = new ConcurrentHashMap<>();

    // Ability / autoscheduler
    private BukkitRunnable aiTask;

    // Persistence (auto-spawn)
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File storageFile;
    private GrinchSpawnData stored;   // persisted config
    private BukkitTask autoTask;
    private long lastSpawnEpoch = 0L;

    public GrinchBossManager(UltimateChristmas plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "bosssata.json"); // (spelling kept as requested)
        loadSpawnData();
        startAutoTaskIfConfigured();
    }

    // ----------------- PERSISTENCE -----------------

    public void loadSpawnData() {
        try {
            if (!storageFile.exists()) return;
            try (Reader r = new FileReader(storageFile)) {
                stored = gson.fromJson(r, GrinchSpawnData.class);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Grinch] Failed to read bosssata.json: " + e.getMessage());
        }
    }

    private void saveSpawnData() {
        if (stored == null) return;
        try {
            storageFile.getParentFile().mkdirs();
            try (Writer w = new FileWriter(storageFile)) {
                gson.toJson(stored, w);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Grinch] Failed to write bosssata.json: " + e.getMessage());
        }
    }

    /** Remember spot + timings and (re)start auto-scheduler. */
    public void setAutoSpawnAt(Location where, long cooldownMinutes, long stayMinutes) {
        if (where == null || where.getWorld() == null) return;
        long cooldownSec = Math.max(1, cooldownMinutes) * 60L;
        long staySec = Math.max(1, stayMinutes) * 60L;

        this.stored = new GrinchSpawnData(
                where.getWorld().getName(),
                where.getX(), where.getY(), where.getZ(),
                cooldownSec, staySec
        );
        saveSpawnData();
        restartAutoTask();
    }

    private void startAutoTaskIfConfigured() {
        if (stored == null) return;
        restartAutoTask();
    }

    private void restartAutoTask() {
        if (autoTask != null) { autoTask.cancel(); autoTask = null; }
        if (stored == null) return;

        autoTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (isAlive()) return;

            long now = System.currentTimeMillis() / 1000L;
            if (lastSpawnEpoch != 0L && now - lastSpawnEpoch < stored.cooldownSeconds) return;

            World w = plugin.getServer().getWorld(stored.world);
            if (w == null) return;
            Location loc = new Location(w, stored.x, stored.y, stored.z);

            if (spawn(loc, null)) {
                lastSpawnEpoch = now;
                long ticks = Math.max(1L, stored.staySeconds) * 20L;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (isAlive()) despawn();
                }, ticks);
            }
        }, 40L, 20L); // start after 2s, then every 1s
    }

    // ----------------- STATE -----------------

    public boolean isAlive() {
        return grinch != null && !grinch.isDead() && grinch.isValid();
    }

    public Entity getEntity() {
        if (grinch != null && grinch.isValid()) return grinch;
        if (grinchUUID != null) {
            Entity e = Bukkit.getEntity(grinchUUID);
            if (e instanceof Mob m && m.isValid()) {
                grinch = m;
                return m;
            }
        }
        return null;
    }

    // ----------------- SPAWN / DESPAWN -----------------

    public boolean spawn(Location where, ProtectedRegion leash) {
        if (isAlive()) return false;

        where.getChunk().load(true);
        this.leashRegion = leash;

        // Base mob (disguised anyway)
        Mob z = where.getWorld().spawn(where, Zombie.class, mob -> {
            mob.setCustomNameVisible(true);
            mob.setRemoveWhenFarAway(false);
            mob.setPersistent(true);
        });

        // Stats from config
        var cfg = plugin.getConfig("grinchboss.yml");
        String name = color(cfg.getString("display_name", "&2&lThe Grinch"));
        double health = Math.max(40.0, cfg.getDouble("stats.health", 500.0));
        double speed  = Math.max(0.05, cfg.getDouble("stats.speed", 0.30));
        boolean glowing = cfg.getBoolean("cosmetics.glowing", true);

        Objects.requireNonNull(z.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(health);
        z.setHealth(health);
        Objects.requireNonNull(z.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(speed);
        z.setCustomName(name);
        if (glowing) try { z.setGlowing(true); } catch (Throwable ignored) {}

        // Bossy resistances
        z.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,     20 * 6000, 1, true, false, true));
        z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,20 * 6000, 0, true, false, true));

        // Disguise with player skin (no armor so skin is fully visible)
        String skin = cfg.getString("skin", "GreenDude");
        applyDisguise(z, skin, name);

        this.grinch = z;
        this.grinchUUID = z.getUniqueId();
        this.damageDone.clear();

        // Spawn lines + cosmetic thunder
        say(cfg.getStringList("messages.spawn_lines"), where);
        if (cfg.getBoolean("cosmetics.thunder", true)) {
            where.getWorld().strikeLightningEffect(where);
        }

        startAI();
        return true;
    }

    public void despawn() {
        if (aiTask != null) { aiTask.cancel(); aiTask = null; }
        Entity e = getEntity();
        if (e != null) {
            try { DisguiseAPI.undisguiseToAll(e); } catch (Throwable ignored) {}
            e.remove();
        }
        grinch = null; grinchUUID = null; leashRegion = null;
        damageDone.clear();
    }

    // ----------------- ABILITIES / AI -----------------

    private void startAI() {
        var cfg = plugin.getConfig("grinchboss.yml");
        int tick = Math.max(5, cfg.getInt("ai.tick_rate", 20));

        aiTask = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!isAlive()) { cancel(); return; }

                // Keep inside leash region if provided
                if (leashRegion != null && !insideRegion(grinch.getLocation(), leashRegion)) {
                    Location safe = centerOnGround(grinch.getLocation().getWorld(), leashRegion);
                    if (safe != null) grinch.teleport(safe);
                }

                // Periodic taunts
                if (t % (cfg.getInt("ai.taunt_every_seconds", 20) * 20) == 0) {
                    sayOne(cfg.getStringList("messages.taunts"), grinch.getLocation());
                }

                // Ability chooser
                if (t % (cfg.getInt("ai.ability_every_seconds", 10) * 20) == 0) {
                    castRandomAbility();
                }

                t += tick;
            }
        };
        aiTask.runTaskTimer(plugin, tick, tick);
    }

    private void castRandomAbility() {
        var cfg = plugin.getConfig("grinchboss.yml");
        WeightedRandomPicker<String> pick = new WeightedRandomPicker<>();
        pick.add("roar_knockback",  cfg.getInt("abilities.roar_knockback.weight",  20));
        pick.add("snowstorm_slow",  cfg.getInt("abilities.snowstorm_slow.weight",  30));
        pick.add("shadow_dash",     cfg.getInt("abilities.shadow_dash.weight",     25));
        pick.add("summon_minions",  cfg.getInt("abilities.summon_minions.weight",  25));
        String chosen = pick.pick();
        if (chosen == null) return;

        switch (chosen) {
            case "roar_knockback" -> abilityRoar();
            case "snowstorm_slow" -> abilitySnowstorm();
            case "shadow_dash"    -> abilityDash();
            case "summon_minions" -> abilitySummon();
        }
    }

    private void abilityRoar() {
        Location c = grinch.getLocation();
        c.getWorld().playSound(c, Sound.ENTITY_WARDEN_ROAR, 1f, 0.7f);
        c.getWorld().spawnParticle(Particle.SONIC_BOOM, c, 1, 0, 0, 0, 0);
        for (Player p : getNearbyPlayers(8)) {
            Vector v = p.getLocation().toVector().subtract(c.toVector()).normalize().multiply(1.4);
            v.setY(0.6);
            p.setVelocity(v);
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, true, true));
        }
    }

    private void abilitySnowstorm() {
        Location c = grinch.getLocation();
        c.getWorld().playSound(c, Sound.WEATHER_RAIN_ABOVE, 1f, 0.8f);
        for (int i = 0; i < 150; i++) {
            double rx = (rng.nextDouble() - 0.5) * 10;
            double rz = (rng.nextDouble() - 0.5) * 10;
            c.getWorld().spawnParticle(Particle.SNOWFLAKE, c.clone().add(rx, 1.8, rz), 1, 0, 0, 0, 0.01);
        }
        for (Player p : getNearbyPlayers(10)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, true, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, true, true, true));
        }
    }

    private void abilityDash() {
        Player target = nearestPlayer(15);
        if (target == null) return;
        Vector dir = target.getLocation().toVector().subtract(grinch.getLocation().toVector()).normalize();
        grinch.setVelocity(dir.multiply(1.2).setY(0.3));
        grinch.getWorld().playSound(grinch.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.6f);
        // small delay then AOE hit
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : getNearbyPlayers(3)) {
                p.damage(6.0, grinch);
                p.getWorld().spawnParticle(Particle.CRIT, p.getLocation(), 20, .3, .5, .3, .1);
            }
        }, 10L);
    }

    private void abilitySummon() {
        var world = grinch.getWorld();
        for (int i = 0; i < 3; i++) {
            Location l = grinch.getLocation().clone().add(rng.nextDouble() * 4 - 2, 0, rng.nextDouble() * 4 - 2);
            world.spawn(l, Zombie.class, z -> {
                z.setCustomName(color("&2Grinch Minion"));
                Objects.requireNonNull(z.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(40.0);
                z.setHealth(40.0);
                z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 120, 1, true, false, true));
            });
            world.spawnParticle(Particle.LARGE_SMOKE, l, 20, .3, .4, .3, .05);
            world.playSound(l, Sound.ENTITY_ZOMBIE_AMBIENT, 1f, 0.8f);
        }
    }

    // ----------------- DAMAGE & REWARDS -----------------

    public void recordDamage(Player p, double amount, EntityDamageEvent.DamageCause cause) {
        if (!isAlive() || p == null || amount <= 0) return;
        damageDone.merge(p.getUniqueId(), amount, Double::sum);
    }

    public void handleDeathAndRewards() {
        if (!isAlive()) return;
        var cfg = plugin.getConfig("grinchboss.yml");

        // Broadcast death line
        say(cfg.getStringList("messages.death_lines"), grinch.getLocation());

        // Drop loot chest with Santa presents
        placeLootChestAtDeath(grinch.getLocation());

        // Weighted rewards to top contributors
        var rewardsSec = cfg.getConfigurationSection("rewards");
        if (rewardsSec != null) {
            WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
            for (String key : rewardsSec.getKeys(false)) {
                int w = cfg.getInt("rewards." + key + ".chance", 1);
                picker.add(key, w);
            }

            int topN = Math.max(1, cfg.getInt("rewarding.top_contributors", 5));
            List<Map.Entry<UUID, Double>> top = damageDone.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(topN)
                    .toList();

            for (var ent : top) {
                Player p = Bukkit.getPlayer(ent.getKey());
                if (p == null) continue;

                String chosen = picker.pick();
                if (chosen == null) continue;

                for (String raw : cfg.getStringList("rewards." + chosen + ".commands")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), raw.replace("%player%", p.getName()));
                }
                for (String msg : cfg.getStringList("rewards." + chosen + ".messages")) {
                    p.sendMessage(color(msg));
                }
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1, 0), 30, .3, .5, .3, .05);
            }
        }

        despawn();
    }

    // ----------------- LOOT CHEST -----------------

    private void placeLootChestAtDeath(Location where) {
        var gc = plugin.getConfig("grinchboss.yml");
        if (!gc.getBoolean("loot_chest.enabled", true)) return;

        where = where.clone();
        World world = where.getWorld();
        if (world == null) return;

        int y = world.getHighestBlockYAt(where.getBlockX(), where.getBlockZ());
        where.setY(y);

        Block b = world.getBlockAt(where);
        // Try to place a chest; if blocked, drop items on ground
        if (!b.getType().isAir()) {
            dropLootOnGround(where);
            return;
        }

        b.setType(Material.CHEST, false);

        if (!(b.getState() instanceof Chest chest)) {
            dropLootOnGround(where);
            return;
        }

        int min = Math.max(1, gc.getInt("loot_chest.min_gifts", 3));
        int max = Math.max(min, gc.getInt("loot_chest.max_gifts", 6));
        int count = min + rng.nextInt(Math.max(1, (max - min + 1)));

        for (int i = 0; i < count; i++) {
            chest.getBlockInventory().addItem(SantaPresentFactory.buildPresent(plugin, 1));
        }

        var extras = gc.getConfigurationSection("loot_chest.extra_items");
        if (extras != null) {
            for (String key : extras.getKeys(false)) {
                String mat = gc.getString("loot_chest.extra_items." + key + ".material", "COAL");
                int amt = Math.max(1, gc.getInt("loot_chest.extra_items." + key + ".amount", 1));
                Material m = Material.matchMaterial(mat);
                if (m != null) chest.getBlockInventory().addItem(new ItemStack(m, amt));
            }
        }

        chest.update(true);

        String msg = gc.getString("loot_chest.announce",
                "&aA loot chest appeared at &f%x%,%y%,%z%&a!");
        msg = msg.replace("%x%", String.valueOf(where.getBlockX()))
                .replace("%y%", String.valueOf(where.getBlockY()))
                .replace("%z%", String.valueOf(where.getBlockZ()))
                .replace('&', '§');
        for (Player p : world.getPlayers()) p.sendMessage(msg);

        world.spawnParticle(Particle.FIREWORK, where.clone().add(0.5, 1.2, 0.5), 30, .3, .4, .3, .05);
        world.playSound(where, Sound.BLOCK_CHEST_OPEN, 1f, 0.9f);
    }

    private void dropLootOnGround(Location where) {
        var gc = plugin.getConfig("grinchboss.yml");
        int min = Math.max(1, gc.getInt("loot_chest.min_gifts", 3));
        int max = Math.max(min, gc.getInt("loot_chest.max_gifts", 6));
        int count = min + rng.nextInt(Math.max(1, (max - min + 1)));
        for (int i = 0; i < count; i++) {
            where.getWorld().dropItemNaturally(where, SantaPresentFactory.buildPresent(plugin, 1));
        }
    }

    // ----------------- UTIL -----------------

    private void applyDisguise(Mob base, String skin, String displayName) {
        try {
            PlayerDisguise dis = new PlayerDisguise(skin);
            dis.setSkin(skin);
            PlayerWatcher w = dis.getWatcher();
            w.setCustomName(displayName);
            w.setCustomNameVisible(true);
            dis.setDisplayedInTab(false);
            dis.setReplaceSounds(true);
            DisguiseAPI.disguiseToAll(base, dis);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Grinch] Failed to apply disguise: " + t.getMessage());
        }
    }

    private boolean insideRegion(Location l, ProtectedRegion r) {
        if (l == null || r == null) return true;
        BlockVector3 v = BlockVector3.at(l.getX(), l.getY(), l.getZ());
        return r.contains(v);
    }

    private Location centerOnGround(World w, ProtectedRegion r) {
        if (w == null || r == null) return null;
        BlockVector3 min = r.getMinimumPoint(), max = r.getMaximumPoint();
        double cx = (min.x() + max.x()) / 2.0, cz = (min.z() + max.z()) / 2.0;
        int y = w.getHighestBlockYAt((int) cx, (int) cz);
        return new Location(w, cx + 0.5, y + 1, cz + 0.5);
    }

    private List<Player> getNearbyPlayers(double r) {
        Entity e = getEntity(); if (e == null) return List.of();
        List<Player> list = new ArrayList<>();
        for (Player p : e.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(e.getLocation()) <= r * r) list.add(p);
        }
        return list;
    }

    private Player nearestPlayer(double r) {
        Entity e = getEntity(); if (e == null) return null;
        Player best = null; double bestD2 = r * r;
        for (Player p : e.getWorld().getPlayers()) {
            double d2 = p.getLocation().distanceSquared(e.getLocation());
            if (d2 < bestD2) { bestD2 = d2; best = p; }
        }
        return best;
    }

    private void say(List<String> lines, Location where) {
        if (lines == null || lines.isEmpty()) return;
        String prefix = color(plugin.getConfig("config.yml").getString("messages.prefix", ""));
        for (String s : lines) {
            String msg = prefix + color(s)
                    .replace("%x%", String.valueOf(where.getBlockX()))
                    .replace("%y%", String.valueOf(where.getBlockY()))
                    .replace("%z%", String.valueOf(where.getBlockZ()));
            for (Player p : where.getWorld().getPlayers()) p.sendMessage(msg);
        }
    }

    private void sayOne(List<String> lines, Location l) {
        if (lines == null || lines.isEmpty()) return;
        say(List.of(lines.get(rng.nextInt(lines.size()))), l);
    }

    private String color(String s) { return s == null ? "" : s.replace('&', '§'); }

    // ----------------- INNER CLASS -----------------

    /** JSON structure for bosssata.json */
    public static final class GrinchSpawnData {
        public String world;
        public double x, y, z;
        public long cooldownSeconds;
        public long staySeconds;

        public GrinchSpawnData() {}

        public GrinchSpawnData(String world, double x, double y, double z, long cooldownSeconds, long staySeconds) {
            this.world = world;
            this.x = x; this.y = y; this.z = z;
            this.cooldownSeconds = cooldownSeconds;
            this.staySeconds = staySeconds;
        }
    }
}
