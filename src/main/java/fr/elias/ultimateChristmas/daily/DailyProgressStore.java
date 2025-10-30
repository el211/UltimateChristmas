package fr.elias.ultimateChristmas.daily;

import fr.elias.ultimateChristmas.UltimateChristmas;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Persists daily-claim progress per player.
 * Stores:
 *  - <uuid>.last_claim_epoch_day (long)
 *  - <uuid>.streak (int)
 *
 * Also provides calendar helpers (season start/length, timezone, reset-hour).
 */
public class DailyProgressStore {

    private final UltimateChristmas plugin;
    private final File file;
    private final FileConfiguration data;

    public DailyProgressStore(UltimateChristmas plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "daily_data.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /* ---------------- Config accessors ---------------- */

    /** Timezone used for daily reset / claims (falls back to UTC). */
    public ZoneId zone() {
        String id = plugin.getConfig("daily_gifts.yml").getString("daily-gifts.claim.timezone", "UTC");
        try {
            return ZoneId.of(id);
        } catch (Exception e) {
            plugin.getLogger().warning("[DAILY] Invalid timezone '" + id + "', falling back to UTC.");
            return ZoneId.of("UTC");
        }
    }

    /** Reset hour (0..23) in the configured timezone. */
    public int resetHour() {
        return plugin.getConfig("daily_gifts.yml").getInt("daily-gifts.claim.reset-hour", 0);
    }

    /** Calendar season start date (ISO yyyy-MM-dd). If not set or invalid, returns null (calendar disabled). */
    public LocalDate seasonStartDateOrNull() {
        String path = "daily-gifts.calendar.start.date";
        String raw = plugin.getConfig("daily_gifts.yml").getString(path, null);
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ex) {
            plugin.getLogger().warning("[DAILY] Invalid calendar.start.date '" + raw + "'; calendar disabled.");
            return null;
        }
    }

    /** Calendar timezone override (if present), else claim timezone. */
    public ZoneId calendarZone() {
        String tz = plugin.getConfig("daily_gifts.yml").getString("daily-gifts.calendar.start.timezone", null);
        if (tz == null || tz.isBlank()) return zone();
        try {
            return ZoneId.of(tz);
        } catch (Exception ex) {
            plugin.getLogger().warning("[DAILY] Invalid calendar.start.timezone '" + tz + "', using claim.timezone.");
            return zone();
        }
    }

    /** Calendar length (# of days to show). Defaults 25. */
    public int calendarLength() {
        return Math.max(1, plugin.getConfig("daily_gifts.yml").getInt("daily-gifts.calendar.start.length", 25));
    }

    /* ---------------- Day boundaries ---------------- */

    /**
     * "Server day" boundary in configured timezone & reset hour.
     * @return epochDay (UTC-based) that represents "today" for claims.
     */
    public long todayEpochDay() {
        ZoneId z = zone();
        int hour = resetHour();
        ZonedDateTime now = ZonedDateTime.now(z);
        ZonedDateTime resetToday = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        LocalDate effective = now.isBefore(resetToday) ? now.minusDays(1).toLocalDate() : now.toLocalDate();
        return effective.toEpochDay();
    }

    /** The "effective" LocalDate today using claim timezone & reset hour. */
    public LocalDate todayLocalDate() {
        ZoneId z = zone();
        int hour = resetHour();
        ZonedDateTime now = ZonedDateTime.now(z);
        ZonedDateTime resetToday = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        return now.isBefore(resetToday) ? now.minusDays(1).toLocalDate() : now.toLocalDate();
    }

    /**
     * Returns 1..length for the current day in the configured Advent season,
     * or -1 if today is outside the season window.
     */
    public int seasonDayIndex(LocalDate effectiveToday) {
        LocalDate start = seasonStartDateOrNull();
        if (start == null) return -1;

        // Calendar uses calendarZone(), not necessarily claim zone.
        ZoneId calZ = calendarZone();
        // Translate effectiveToday (claim-zone day) to calendar-zone day to compare correctly.
        // We’ll just compare LocalDates by converting "now" in calendar zone as well:
        ZonedDateTime calNow = ZonedDateTime.now(calZ);
        // Determine effective "calendar today" with the SAME reset hour as claims, for consistency:
        ZonedDateTime reset = calNow.withHour(resetHour()).withMinute(0).withSecond(0).withNano(0);
        LocalDate calToday = calNow.isBefore(reset) ? calNow.minusDays(1).toLocalDate() : calNow.toLocalDate();

        long diff = java.time.temporal.ChronoUnit.DAYS.between(start, calToday); // 0-based
        int idx = (int) diff + 1; // 1-based
        if (idx < 1 || idx > calendarLength()) return -1;
        return idx;
    }

    /* ---------------- Query state ---------------- */

    public boolean hasClaimedToday(UUID uuid) {
        long last = data.getLong(uuid + ".last_claim_epoch_day", Long.MIN_VALUE);
        return last == todayEpochDay();
    }

    /** Query if a specific epochDay was claimed by the player. Useful for GUI mapping of calendar cells. */
    public boolean hasClaimedEpochDay(UUID uuid, long epochDay) {
        long last = data.getLong(uuid + ".last_claim_epoch_day", Long.MIN_VALUE);
        return last == epochDay;
    }

    /** Current streak value (>=1) or 0 if none. */
    public int getStreak(UUID uuid) {
        return data.getInt(uuid + ".streak", 0);
    }

    /* ---------------- Mutations ---------------- */

    /**
     * Record today's claim:
     *  - If last == today      => keep streak as-is (already recorded)
     *  - If last == today - 1  => streak++
     *  - Else                  => streak = 1
     */
    public void recordClaim(UUID uuid) {
        long last = data.getLong(uuid + ".last_claim_epoch_day", Long.MIN_VALUE);
        long today = todayEpochDay();
        int streak = getStreak(uuid);

        if (last == today) {
            // already recorded; do nothing
        } else if (last == today - 1) {
            streak = Math.max(1, streak + 1);
        } else {
            streak = 1;
        }

        data.set(uuid + ".last_claim_epoch_day", today);
        data.set(uuid + ".streak", streak);
        save();
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[DAILY] Failed saving daily_data.yml: " + e.getMessage());
        }
    }
}
