package fr.elias.ultimateChristmas.daily;

import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.util.WeightedRandomPicker;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class DailyGiftManager {

    private final UltimateChristmas plugin;
    private DailyProgressStore progressStore;

    public DailyGiftManager(UltimateChristmas plugin) {
        this.plugin = plugin;
    }

    public void setProgressStore(DailyProgressStore store) {
        this.progressStore = store;
        dbg("Progress store injected: " + (store != null));
    }

    /* ---------------- Advent helpers ---------------- */

    public boolean canClaimNow(Player p) {
        if (p == null) return false;
        if (progressStore == null) return true;
        return !progressStore.hasClaimedToday(p.getUniqueId());
    }

    public String timeUntilResetText() {
        if (progressStore == null) return "unknown";
        var z = progressStore.zone();
        int hour = plugin.getConfig("daily_gifts.yml").getInt("daily-gifts.claim.reset-hour", 0);

        ZonedDateTime now = ZonedDateTime.now(z);
        ZonedDateTime resetToday = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime next = now.isBefore(resetToday) ? resetToday : resetToday.plusDays(1);

        long secs = Duration.between(now, next).getSeconds();
        long h = secs / 3600;
        long m = (secs % 3600) / 60;
        return h + "h " + m + "m";
    }

    public void claimFromGUI(Player p) {
        FileConfiguration cfg = plugin.getConfig("daily_gifts.yml");
        if (!canClaimNow(p)) {
            String msg = cfg.getString("daily-gifts.messages.already-claimed",
                    "&cYou've already claimed today's reward.");
            p.sendMessage(msg.replace("&", "§").replace("%remaining%", timeUntilResetText()));
            dbg("Claim blocked (already claimed) for " + p.getName());
            return;
        }
        redeemGift(p);
        if (progressStore != null) {
            int streak = progressStore.getStreak(p.getUniqueId());
            String ok = cfg.getString("daily-gifts.messages.claimed",
                    "&aYou claimed today's reward!");
            p.sendMessage(ok.replace("&", "§").replace("%streak%", String.valueOf(streak)));
        }
    }

    /* ---------------- Core redeem logic ---------------- */

    public void redeemGift(Player p) {
        if (p == null) return;
        UUID uuid = p.getUniqueId();

        if (progressStore != null && progressStore.hasClaimedToday(uuid)) {
            String already = plugin.getConfig("daily_gifts.yml")
                    .getString("daily-gifts.messages.already-claimed",
                            "&cYou've already opened today's gift. Come back tomorrow!");
            p.sendMessage(already.replace("&", "§").replace("%remaining%", timeUntilResetText()));
            dbg("Claim denied (already claimed today): " + p.getName());
            return;
        }

        FileConfiguration cfg = plugin.getConfig("daily_gifts.yml");

        String group = LuckPermsHook.getPrimaryGroupOrDefault(p, "default");
        String basePath = "daily-gifts.rewards." + group;
        ConfigurationSection sec = cfg.getConfigurationSection(basePath);
        if (sec == null) {
            dbg("No rewards for group '" + group + "', falling back to 'default'.");
            basePath = "daily-gifts.rewards.default";
            sec = cfg.getConfigurationSection(basePath);
            if (sec == null) {
                String msg = cfg.getString("daily-gifts.messages.no-reward-config", "&cNo gifts configured.");
                p.sendMessage(msg.replace("&", "§"));
                dbg("No rewards configured at daily-gifts.rewards.default either.");
                return;
            }
        }

        String chosen = calendarPick(cfg, sec, group);
        if (chosen == null) {
            dbg("No valid calendar mapping for today; falling back to weighted-random.");
            chosen = weightedPick(sec);
            if (chosen == null) {
                p.sendMessage("§cGift roll failed.");
                dbg("Picker returned null (no valid entries?).");
                return;
            }
        }

        String path = basePath + "." + chosen;
        dbg("Chosen reward: " + path);

        List<String> cmds = cfg.getStringList(path + ".commands");
        for (String raw : cmds) {
            String cmd = raw.replace("%player%", p.getName());
            dbg("Dispatching: " + cmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        for (String msg : cfg.getStringList(path + ".messages")) {
            p.sendMessage(msg.replace("&", "§"));
        }

        boolean firework = cfg.getBoolean(path + ".event.firework", false);
        String soundName = cfg.getString(path + ".event.sound", "");
        if (firework) launchFirework(p.getLocation());

        if (soundName != null && !soundName.isEmpty()) {
            String normalized = soundName.trim().toUpperCase(Locale.ROOT);
            try {
                p.playSound(p.getLocation(), normalized, 1f, 1f);
            } catch (Throwable t) {
                dbg("Failed to play sound '" + normalized + "': " + t.getMessage());
            }
        }

        p.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                p.getLocation().add(0, 1, 0),
                20, 0.4, 0.4, 0.4, 0.01
        );

        if (progressStore != null) {
            progressStore.recordClaim(uuid); // overload present
            dbg("Recorded claim for " + p.getName()
                    + " (epochDay=" + progressStore.todayEpochDay()
                    + ", streak=" + progressStore.getStreak(uuid) + ")");
        } else {
            dbg("No progress store set — claim not persisted.");
        }
    }

    /* ---------------- Calendar helpers (also used by GUI) ---------------- */

    /**
     * GUI helper: get the reward key configured for a specific calendar index (day-of-month).
     * Uses group-specific mapping if present, else global mapping. May return null.
     */
    public String getTodayCalendarRewardKeyForIndex(int dayIndex) {
        FileConfiguration cfg = plugin.getConfig("daily_gifts.yml");
        String group = "default"; // GUI preview doesn’t know the player’s group; default is fine for visuals
        String groupKey = "daily-gifts.calendar." + group + "." + dayIndex;
        String forced = cfg.getString(groupKey, null);
        if (forced == null) {
            forced = cfg.getString("daily-gifts.calendar." + dayIndex, null);
        }
        return (forced == null || forced.isEmpty()) ? null : forced;
    }

    /** Visual metadata for the calendar cell (icon/name/lore). */
    public Visual getVisualForKey(String rewardKey) {
        FileConfiguration cfg = plugin.getConfig("daily_gifts.yml");
        // Prefer default group’s reward section for visuals
        String base = "daily-gifts.rewards.default." + rewardKey;

        String iconMat = cfg.getString(base + ".icon.material", "PAPER");
        String name = cfg.getString(base + ".icon.name", "&e" + rewardKey);
        List<String> lore = cfg.getStringList(base + ".icon.lore");
        if (lore == null) lore = new ArrayList<>();

        return new Visual(iconMat, name, lore);
    }

    public record Visual(String iconMaterial, String displayName, List<String> lore) {}

    /* ---------------- Internal calendar pick ---------------- */

    private String calendarPick(FileConfiguration cfg, ConfigurationSection rewardsSection, String group) {
        int resetHour = cfg.getInt("daily-gifts.claim.reset-hour", 0);
        var zone = (progressStore != null) ? progressStore.zone() : java.time.ZoneId.systemDefault();

        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime resetToday = now.withHour(resetHour).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime effective = now.isBefore(resetToday) ? now.minusDays(1) : now;
        int dayOfMonth = effective.getDayOfMonth();

        String groupKey = "daily-gifts.calendar." + group + "." + dayOfMonth;
        String forcedId = cfg.getString(groupKey, null);
        if (forcedId == null) {
            String globalKey = "daily-gifts.calendar." + dayOfMonth;
            forcedId = cfg.getString(globalKey, null);
        }
        if (forcedId == null || forcedId.isEmpty()) return null;

        Set<String> keys = rewardsSection.getKeys(false);
        if (!keys.contains(forcedId)) return null;
        return forcedId;
    }

    private String weightedPick(ConfigurationSection sec) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        for (String giftId : sec.getKeys(false)) {
            int weight = sec.getInt(giftId + ".chance", 1);
            if (weight > 0) {
                picker.add(giftId, weight);
                dbg("Added pick '" + giftId + "' weight=" + weight);
            }
        }
        return picker.pick();
    }

    /* ---------------- FX & debug ---------------- */

    private void launchFirework(Location loc) {
        try {
            Firework fw = loc.getWorld().spawn(loc, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(org.bukkit.Color.RED)
                    .withFade(org.bukkit.Color.WHITE)
                    .flicker(true)
                    .trail(true)
                    .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        } catch (Throwable t) {
            dbg("Failed to spawn firework: " + t.getMessage());
        }
    }

    private boolean debugEnabled() {
        try {
            if (plugin.getConfig("daily_gifts.yml").getBoolean("daily-gifts.debug", false)) return true;
        } catch (Throwable ignored) {}
        try {
            return plugin.getConfig().getBoolean("debug", false);
        } catch (Throwable ignored) {}
        return false;
    }

    private void dbg(String s) {
        if (debugEnabled()) plugin.getLogger().info("[DailyGift] " + s);
    }
}
