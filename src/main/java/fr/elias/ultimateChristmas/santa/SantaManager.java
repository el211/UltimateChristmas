package fr.elias.ultimateChristmas.santa;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import fr.elias.ultimateChristmas.UltimateChristmas;
import fr.elias.ultimateChristmas.economy.ShardManager;
import fr.elias.ultimateChristmas.integration.WorldGuardIntegration;
import fr.elias.ultimateChristmas.util.WeightedRandomPicker;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.PlayerWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class SantaManager {

    private final UltimateChristmas plugin;
    private final WorldGuardIntegration wgIntegration;
    private final Random random = new Random();

    // Active Santa state
    private UUID santaUUID;
    private Mob currentSanta;
    private ProtectedRegion currentSantaRegion; // WG leash region
    private final ShardManager shardManager;

    // Timestamps (seconds)
    private long spawnUnixSeconds = 0L;
    private long lastGiftDropSeconds = 0L;

    // Per-player cooldown for right-click rewards
    private final Map<UUID, Long> lastGiftTime = new HashMap<>();

    // Repeating scheduler task
    private BukkitTask schedulerTask;

    public SantaManager(UltimateChristmas plugin,
                        WorldGuardIntegration wgIntegration,
                        ShardManager shardManager) {
        this.plugin = plugin;
        this.wgIntegration = wgIntegration;
        this.shardManager = shardManager;
    }


    // --------------------------------------------------------------------
    // Scheduler lifecycle
    // --------------------------------------------------------------------

    public void startScheduler() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
        }

        // FORCE LOG ON START so you see something no matter what
        plugin.getLogger().info("[SantaManager] startScheduler(): called. debugEnabled()=" + debugEnabled());

        dbg("Scheduler: starting...");

        schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            FileConfiguration cfg = plugin.getConfig("santa.yml");
            if (!cfg.getBoolean("enabled", true)) {
                dbg("Scheduler: santa.yml enabled=false, skipping tick.");
                return;
            }

            long now = nowSeconds();

            long cooldown = cfg.getLong("spawn.cooldown", 3600L);
            long stayTime = cfg.getLong("spawn.stay_time", 600L);
            long giftCooldownCfg = cfg.getLong("gifts.drop_cooldown", 60L);

            // clamp runtime drop cooldown so it's not insanely slow
            long giftCooldown = Math.min(giftCooldownCfg, 15L);

            // keep currentSanta pointer synced
            Entity santaEntity = getSantaEntity();
            if (santaEntity instanceof Mob mob) {
                currentSanta = mob;
            }

            boolean santaAlive = (santaEntity != null && !santaEntity.isDead());

            if (!santaAlive) {
                long sinceLastSpawn = now - spawnUnixSeconds;
                dbg("Scheduler: no Santa. sinceLastSpawn=" + sinceLastSpawn + " cooldown=" + cooldown);

                if (sinceLastSpawn >= cooldown) {
                    dbg("Scheduler: cooldown met -> spawning Santa...");
                    spawnSantaFromConfig();
                }
                return;
            }

            long aliveFor = now - spawnUnixSeconds;
            dbg("Scheduler: Santa aliveFor=" + aliveFor + " / stayTime=" + stayTime);

            // Make sure Santa can't wander too far outside region
            leashSantaInsideRegion();

            if (aliveFor >= stayTime) {
                dbg("Scheduler: stay time exceeded; despawning Santa.");

                broadcast(
                        cfg.getString("spawn.leave-message", "&cSanta has finished giving gifts and left from %location%&c."),
                        santaEntity.getLocation()
                );

                despawnSantaIfAny();
                return;
            }

            // timed drop
            long sinceLastDrop = now - lastGiftDropSeconds;
            if (giftCooldown > 0 && (sinceLastDrop >= giftCooldown)) {
                dbg("Scheduler: dropGift() allowed. sinceLastDrop=" + sinceLastDrop + " cooldown=" + giftCooldown);
                dropGift(cfg);
                lastGiftDropSeconds = now;
            } else {
                dbg("Scheduler: not dropping gift yet. sinceLastDrop=" + sinceLastDrop + " < cooldown=" + giftCooldown);
            }

        }, 20L, 20L); // 1s
    }

    // --------------------------------------------------------------------
    // Leash
    // --------------------------------------------------------------------

    /**
     * Hard clamp Santa inside currentSantaRegion.
     * If he's out, teleport him back toward center of region and snap Y to safe ground.
     */
    private void leashSantaInsideRegion() {
        if (currentSanta == null || currentSanta.isDead() || currentSantaRegion == null) {
            dbg("Leash: skipped (currentSanta=" + (currentSanta == null ? "null" : currentSanta.getUniqueId()) +
                    ", region=" + (currentSantaRegion == null ? "null" : currentSantaRegion.getId()) + ")");
            return;
        }

        Location curLoc = currentSanta.getLocation();
        if (curLoc.getWorld() == null) {
            dbg("Leash: Santa world is null? skipping.");
            return;
        }

        BlockVector3 curVec = BlockVector3.at(
                curLoc.getBlockX(),
                curLoc.getBlockY(),
                curLoc.getBlockZ()
        );

        boolean inside = currentSantaRegion.contains(curVec);
        dbg("Leash: check inside=" + inside + " pos=" + loc(curLoc) +
                " region=" + currentSantaRegion.getId());

        if (inside) {
            return;
        }

        BlockVector3 min = currentSantaRegion.getMinimumPoint();
        BlockVector3 max = currentSantaRegion.getMaximumPoint();

        double midX = (min.x() + max.x()) / 2.0;
        double midZ = (min.z() + max.z()) / 2.0;
        double guessY = Math.max(min.y(), Math.min(curLoc.getY(), max.y()));

        Location targetGuess = new Location(curLoc.getWorld(), midX, guessY, midZ);
        Location safe = snapToGround(targetGuess);

        dbg("Leash: OUTSIDE REGION. Teleporting Santa to safe=" + loc(safe) +
                " (mid " + midX + "," + guessY + "," + midZ + ")");
        currentSanta.teleport(safe);
    }

    // --------------------------------------------------------------------
    // Public helpers
    // --------------------------------------------------------------------

    /**
     * Clean despawn Santa.
     */
    public void despawnSantaIfAny() {
        dbg("despawnSantaIfAny(): begin");

        // stop walking controller
        plugin.setActiveWalkController(null);

        Entity santa = getSantaEntity();
        if (santa != null && !santa.isDead()) {
            dbg("despawnSantaIfAny(): found Santa entity " + santa.getUniqueId() + " removing...");
            try {
                DisguiseAPI.undisguiseToAll(santa);
                dbg("despawnSantaIfAny(): undisguised successfully");
            } catch (Throwable t) {
                warn("despawnSantaIfAny(): undisguise error: " + t.getMessage());
            }
            santa.remove();
        } else {
            dbg("despawnSantaIfAny(): no living Santa to remove.");
        }

        // IMPORTANT:
        // treat "now" as the last-spawn timestamp baseline so cooldown works.
        long now = nowSeconds();
        spawnUnixSeconds = now;
        lastGiftDropSeconds = now;

        // clear refs
        santaUUID = null;
        currentSanta = null;
        currentSantaRegion = null;

        dbg("despawnSantaIfAny(): cleared refs.");
    }


    public boolean isSanta(Entity clicked) {
        return clicked != null
                && santaUUID != null
                && clicked.getUniqueId().equals(santaUUID);
    }

    public Entity getSantaEntity() {
        if (santaUUID == null) {
            dbg("getSantaEntity(): santaUUID is null -> no Santa");
            return null;
        }

        if (currentSanta != null &&
                !currentSanta.isDead() &&
                currentSanta.getUniqueId().equals(santaUUID)) {
            return currentSanta;
        }

        dbg("getSantaEntity(): refetching by UUID " + santaUUID);
        Entity refetched = Bukkit.getEntity(santaUUID);
        if (refetched instanceof Mob mob && !mob.isDead()) {
            dbg("getSantaEntity(): refetched OK");
            currentSanta = mob;
            return currentSanta;
        }

        dbg("getSantaEntity(): refetch failed / santa dead. clearing refs.");
        currentSanta = null;
        santaUUID = null;
        currentSantaRegion = null;
        return null;
    }

    // --------------------------------------------------------------------
    // Spawn logic
    // --------------------------------------------------------------------

    public void spawnSantaFromConfig() {
        dbg("spawnSantaFromConfig(): begin");
        despawnSantaIfAny();
        dbg("spawnSantaFromConfig(): previous Santa despawned (if any)");

        FileConfiguration cfg = plugin.getConfig("santa.yml");

        SpawnChoice choice = pickSpawnChoice(cfg);
        if (choice == null) {
            plugin.getLogger().severe("[UltimateChristmas] No valid spawn regions in santa.yml.");
            warn("spawnSantaFromConfig(): ABORT (choice==null)");
            return;
        }

        dbg("spawnSantaFromConfig(): picked spawnKey=" + choice.key + " rawLoc=" + loc(choice.rawLocation));

        Location spawnLoc = snapToGround(choice.rawLocation);
        dbg("spawnSantaFromConfig(): snapToGround -> " + loc(spawnLoc));

        loadChunk(spawnLoc);

        String santaSkinName = cfg.getString("skin", "brcdev");
        String santaDisplayName = choice.displayNameColored;

        String walkingRegionId = cfg.getString("spawn.walking_region_id", "santa_zone");
        ProtectedRegion walkRegion = wgIntegration.getRegion(spawnLoc.getWorld(), walkingRegionId);

        dbg("spawnSantaFromConfig(): walking_region_id='" + walkingRegionId + "'");
        dbg("spawnSantaFromConfig(): wgIntegration.isEnabled()=" + wgIntegration.isEnabled());
        dbg("spawnSantaFromConfig(): walkRegion = " +
                (walkRegion == null ? "null" :
                        (walkRegion.getId() + " bounds=" +
                                walkRegion.getMinimumPoint() + " -> " +
                                walkRegion.getMaximumPoint())));

        // Spawn the sheep Santa
        Entity spawnedEntity = spawnLoc.getWorld().spawn(spawnLoc, Sheep.class, sheep -> {
            dbg("spawnSantaFromConfig(): Sheep init / configure");

            sheep.setInvulnerable(true);
            sheep.setSilent(true);
            sheep.setGravity(true);
            sheep.setPersistent(true);
            sheep.setCustomNameVisible(true);
            sheep.setCustomName(santaDisplayName);
            try {
                sheep.setGlowing(true);
            } catch (Throwable ignored) {}

            // AI MUST be true so pathfinder.moveTo works
            sheep.setAI(true);

            dbg("spawnSantaFromConfig(): Sheep configured uuid=" + sheep.getUniqueId());
        });

        dbg("spawnSantaFromConfig(): spawned=" + spawnedEntity.getType() + " uuid=" + spawnedEntity.getUniqueId());

        if (!(spawnedEntity instanceof Mob mobSanta)) {
            warn("spawnSantaFromConfig(): spawned entity is not Mob -> removing");
            spawnedEntity.remove();
            return;
        }

        santaUUID = mobSanta.getUniqueId();
        currentSanta = mobSanta;
        currentSantaRegion = walkRegion;
        spawnUnixSeconds = nowSeconds();
        lastGiftDropSeconds = spawnUnixSeconds;

        dbg("spawnSantaFromConfig(): tracking Santa uuid=" + santaUUID +
                " spawnUnixSeconds=" + spawnUnixSeconds +
                " lastGiftDropSeconds=" + lastGiftDropSeconds +
                " region=" + (currentSantaRegion == null ? "null" : currentSantaRegion.getId()));

        // Apply disguise
        applyDisguise(currentSanta, santaSkinName, santaDisplayName);

        // Walking controller (random pathing inside region)
        if (walkRegion == null) {
            dbg("spawnSantaFromConfig(): walkRegion is null -> NO SantaWalkController, only leash fallback.");
            plugin.setActiveWalkController(null);
        } else {
            dbg("spawnSantaFromConfig(): creating SantaWalkController for region '" + walkingRegionId + "'");
            SantaWalkController controller = new SantaWalkController(plugin, currentSanta, walkRegion);
            controller.startWalking();
            plugin.setActiveWalkController(controller);
        }

        // Broadcast spawn message
        broadcast(
                cfg.getString("spawn.spawn-message", "&aSanta has spawned at &f%location%&a! Hurry and find him!"),
                spawnLoc
        );

        dbg("spawnSantaFromConfig(): DONE");
    }

    private SpawnChoice pickSpawnChoice(FileConfiguration cfg) {
        ConfigurationSection regionsSec = cfg.getConfigurationSection("spawn.regions");
        if (regionsSec == null) {
            dbg("pickSpawnChoice(): spawn.regions missing");
            return null;
        }

        List<String> keys = new ArrayList<>(regionsSec.getKeys(false));
        dbg("pickSpawnChoice(): keys=" + keys);

        if (keys.isEmpty()) return null;

        String chosenKey = keys.get(random.nextInt(keys.size()));
        ConfigurationSection sec = regionsSec.getConfigurationSection(chosenKey);
        if (sec == null) {
            dbg("pickSpawnChoice(): chosen section '" + chosenKey + "' null");
            return null;
        }

        Location rawLoc = createLocationFromConfig(sec);
        if (rawLoc == null) {
            dbg("pickSpawnChoice(): location for '" + chosenKey + "' is null");
            return null;
        }

        String display = sec.getString("displayname", "&cSanta").replace("&", "§");

        dbg("pickSpawnChoice(): chose key=" + chosenKey + " world=" +
                rawLoc.getWorld().getName() + " xyz=" +
                rawLoc.getX() + "," + rawLoc.getY() + "," + rawLoc.getZ() +
                " display=" + display);

        return new SpawnChoice(chosenKey, rawLoc, display);
    }

    // --------------------------------------------------------------------
    // Right-click gift logic
    // --------------------------------------------------------------------

    public void tryGiveSantaGift(Player player) {
        FileConfiguration cfg = plugin.getConfig("santa.yml");
        long now = nowSeconds();
        long cooldownSeconds = cfg.getLong("gifts.delay", 5L);

        long last = lastGiftTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < cooldownSeconds) {
            player.sendMessage("§cSanta: slow down ho ho ho!");
            return;
        }
        lastGiftTime.put(player.getUniqueId(), now);

        ConfigurationSection giftsSec = cfg.getConfigurationSection("gifts.rewards");
        if (giftsSec == null) {
            player.sendMessage("§cSanta has no gifts configured!");
            return;
        }

        // build weighted picker
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        for (String giftKey : giftsSec.getKeys(false)) {
            String base = "gifts.rewards." + giftKey;
            if (cfg.isConfigurationSection(base)) {
                int weight = cfg.getInt(base + ".chance", 1);
                picker.add(giftKey, weight);
                if (debugEnabled()) {
                    plugin.getLogger().info("[DEBUG] tryGiveSantaGift(): added reward '" +
                            giftKey + "' weight=" + weight);
                }
            }
        }

        String chosen = picker.pick();
        if (chosen == null) {
            player.sendMessage("§cSanta couldn't decide on a gift!");
            return;
        }

        if (debugEnabled()) {
            plugin.getLogger().info("[DEBUG] tryGiveSantaGift(): chosen reward='" + chosen + "'");
        }

        String basePath = "gifts.rewards." + chosen;

        // 1) run commands OR intercept shards
        List<String> commands = cfg.getStringList(basePath + ".commands");
        for (String rawCmd : commands) {

            String cmd = rawCmd.replace("%player%", player.getName()).trim();

            // pattern: shards give <name> <amount>
            if (cmd.toLowerCase(Locale.ROOT).startsWith("shards give ")) {
                String[] parts = cmd.split("\\s+");
                // expect: ["shards","give","PlayerName","25"]
                if (parts.length >= 4) {
                    String targetName = parts[2];
                    String amountStr = parts[3];

                    int toAdd = 0;
                    try {
                        toAdd = Integer.parseInt(amountStr);
                    } catch (NumberFormatException ignored) {}

                    if (debugEnabled()) {
                        plugin.getLogger().info("[DEBUG] Santa shards handler: parsed command='" + cmd +
                                "', targetName=" + targetName +
                                ", toAdd=" + toAdd);
                    }

                    if (toAdd > 0 && player.getName().equalsIgnoreCase(targetName)) {
                        int before = shardManager.getShards(player.getUniqueId());
                        shardManager.addShards(player.getUniqueId(), toAdd);
                        int after = shardManager.getShards(player.getUniqueId());

                        if (debugEnabled()) {
                            plugin.getLogger().info("[DEBUG] Santa shards handler: added " + toAdd +
                                    " shards to " + player.getName() +
                                    " before=" + before + " after=" + after);
                        }

                        // tell player
                        player.sendMessage("§a+" + toAdd + " shards (§f" + after + "§a total)");
                    } else {
                        if (debugEnabled()) {
                            plugin.getLogger().warning("[DEBUG] Santa shards handler: "
                                    + "couldn't apply shards for cmd='" + cmd + "'");
                        }
                    }
                    // IMPORTANT: do NOT dispatch this to console now, we already handled it
                    continue;
                } else {
                    if (debugEnabled()) {
                        plugin.getLogger().warning("[DEBUG] Santa shards handler: malformed shards cmd '" + cmd + "'");
                    }
                    continue;
                }
            }

            // fallback = let console execute normal commands like /give cookie, /say, etc.
            if (debugEnabled()) {
                plugin.getLogger().info("[DEBUG] tryGiveSantaGift(): dispatch console cmd='" + cmd + "'");
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        // 2) messages (with prefix)
        String prefix = plugin.getConfig("config.yml")
                .getString("messages.prefix", "")
                .replace("&", "§");

        List<String> messages = cfg.getStringList(basePath + ".messages");
        for (String line : messages) {
            player.sendMessage(prefix + line.replace("&", "§"));
        }

        // 3) fx
        boolean fw = cfg.getBoolean(basePath + ".event.firework", false);
        String soundKey = cfg.getString(basePath + ".event.sound", "");

        if (fw) {
            player.getWorld().spawnParticle(
                    Particle.FIREWORK,
                    player.getLocation(),
                    20,
                    0.4, 0.4, 0.4,
                    0.1
            );
        }

        if (soundKey != null && !soundKey.isEmpty()) {
            String normalizedSound = soundKey.trim()
                    .toLowerCase(Locale.ROOT)
                    .replace(' ', '_');
            try {
                player.playSound(player.getLocation(), normalizedSound, 1f, 1f);
            } catch (Exception e) {
                plugin.getLogger().warning(
                        "[UltimateChristmas] Failed to play sound '" + normalizedSound + "'."
                );
            }
        }
    }


    // --------------------------------------------------------------------
    // Ambient gift drop logic
    // --------------------------------------------------------------------

    private void dropGift(FileConfiguration cfg) {
        dbg("dropGift(): begin");

        Entity santaEntity = getSantaEntity();
        if (santaEntity == null || santaEntity.isDead()) {
            dbg("dropGift(): aborted - no living Santa.");
            return;
        }

        ConfigurationSection giftItemSec = cfg.getConfigurationSection("gifts.drop_item");
        if (giftItemSec == null) {
            dbg("dropGift(): gifts.drop_item missing, skipping ambient drop.");
            return;
        }

        String matName = giftItemSec.getString("material", "PAPER");
        String upperMat = (matName == null ? "PAPER" : matName.toUpperCase(Locale.ROOT));
        Material material = Material.getMaterial(upperMat);
        dbg("dropGift(): matName(raw)=" + matName + " resolved=" + material);

        if (material == null) {
            plugin.getLogger().warning("[UltimateChristmas] Invalid material for dropped gift: " + matName);
            material = Material.PAPER;
        }

        int amt = giftItemSec.getInt("amount", 1);
        dbg("dropGift(): amount=" + amt);

        String displayName = giftItemSec.getString("display_name", "&c&lSanta's Present");
        List<String> loreLines = giftItemSec.getStringList("lore");
        String textureBase64 = giftItemSec.getString("head_texture", "");
        dbg("dropGift(): displayName=" + displayName +
                " loreLines=" + loreLines +
                " head_texture.len=" + (textureBase64 == null ? 0 : textureBase64.length()));

        ItemStack stack;
        if (material == Material.PLAYER_HEAD) {
            dbg("dropGift(): building custom textured head via buildCustomPresentHead()");
            stack = buildCustomPresentHead(
                    textureBase64,
                    amt,
                    displayName,
                    loreLines
            );
        } else {
            stack = new ItemStack(material, amt);
            stack = applyNameAndLore(stack, displayName, loreLines);
        }

        // Add glint
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            dbg("dropGift(): adding glint to final stack");
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        } else {
            dbg("dropGift(): WARNING getItemMeta() is null after build, skipping enchant");
        }

        // final sanity dump for skull texture
        ItemMeta finalMeta = stack.getItemMeta();
        if (finalMeta instanceof SkullMeta skullFinal) {
            dbg("dropGift(): final stack meta IS SkullMeta");
            try {
                // Can't directly read base64 from SkullMeta, but we can see if it has an owner
                dbg("dropGift(): final stack SkullMeta owningPlayer=" +
                        (skullFinal.getOwningPlayer() == null
                                ? "null"
                                : skullFinal.getOwningPlayer().getName() + "/" +
                                skullFinal.getOwningPlayer().getUniqueId()));
            } catch (Throwable t) {
                dbg("dropGift(): SkullMeta#getOwningPlayer() threw " + t.getClass().getSimpleName() +
                        ": " + t.getMessage());
            }
        } else {
            dbg("dropGift(): final stack meta class=" +
                    (finalMeta == null ? "null" : finalMeta.getClass().getName()));
        }

        Location dropLoc = santaEntity.getLocation().add(0, 0.5, 0);
        dbg("dropGift(): dropLoc=" + loc(dropLoc));

        if (dropLoc.getWorld() == null) {
            dbg("dropGift(): world is null, aborting drop");
            return;
        }

        dropLoc.getWorld().dropItemNaturally(dropLoc, stack);
        dropLoc.getWorld().spawnParticle(
                Particle.END_ROD,
                dropLoc,
                10,
                0.2, 0.2, 0.2,
                0
        );

        dbg("dropGift(): DONE drop");
    }
    /**
     * FORCE a custom texture on a PLAYER_HEAD using raw base64.
     *
     * Strategies:
     *  1. CraftPlayerProfile#setPlayerProfile (modern Paper/Bukkit)
     *  2. legacy Paper CraftPlayerProfile
     *  3. Direct "profile" field with GameProfile (old Spigot)
     *  4. Direct "profile" field with ResolvableProfile (new Paper 1.20+/1.21+ style)
     *
     * We spam dbg() so we know exactly which one wins on your server.
     */
    private ItemStack buildCustomPresentHead(String textureBase64,
                                             int amount,
                                             String displayName,
                                             List<String> loreLines) {

        dbg("buildCustomPresentHead(): start amount=" + amount +
                " texture.len=" + (textureBase64 == null ? 0 : textureBase64.length()));

        ItemStack head = new ItemStack(Material.PLAYER_HEAD, amount);
        ItemMeta rawMeta = head.getItemMeta();

        if (!(rawMeta instanceof SkullMeta skullMeta)) {
            dbg("buildCustomPresentHead(): ItemMeta NOT SkullMeta -> " +
                    (rawMeta == null ? "null" : rawMeta.getClass().getName()));
            return head;
        }

        // set display name
        if (displayName != null && !displayName.isEmpty()) {
            String coloredName = displayName.replace("&", "§");
            skullMeta.setDisplayName(coloredName);
            dbg("buildCustomPresentHead(): set displayName -> " + coloredName);
        } else {
            dbg("buildCustomPresentHead(): no displayName");
        }

        // set lore
        if (loreLines != null && !loreLines.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : loreLines) {
                coloredLore.add(line.replace("&", "§"));
            }
            skullMeta.setLore(coloredLore);
            dbg("buildCustomPresentHead(): set lore -> " + coloredLore);
        } else {
            dbg("buildCustomPresentHead(): no loreLines");
        }

        if (textureBase64 == null || textureBase64.isEmpty()) {
            dbg("buildCustomPresentHead(): textureBase64 empty -> returning plain named head");
            head.setItemMeta(skullMeta);
            return head;
        }

        // ------------------------------------------------
        // Step 0: build com.mojang.authlib.GameProfile w/ textures
        // ------------------------------------------------
        Object gameProfile = null;
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

            gameProfile = gameProfileClass
                    .getConstructor(UUID.class, String.class)
                    .newInstance(UUID.randomUUID(), "santa-present");

            Object textureProperty = propertyClass
                    .getConstructor(String.class, String.class)
                    .newInstance("textures", textureBase64);

            Object propsMap = gameProfileClass
                    .getMethod("getProperties")
                    .invoke(gameProfile);

            propsMap.getClass()
                    .getMethod("put", Object.class, Object.class)
                    .invoke(propsMap, "textures", textureProperty);

            dbg("buildCustomPresentHead(): built GameProfile with custom textures OK");
        } catch (Throwable t) {
            dbg("buildCustomPresentHead(): FAILED building GameProfile: " +
                    t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        boolean applied = false;

        // ------------------------------------------------
        // Strategy 1: modern CraftPlayerProfile path
        // ------------------------------------------------
        if (!applied && gameProfile != null) {
            try {
                dbg("buildCustomPresentHead(): trying modern CraftPlayerProfile path");
                Class<?> cbProfileClass = Class.forName("org.bukkit.craftbukkit.profile.CraftPlayerProfile");

                // try .fromProfile(GameProfile) first, then .of(GameProfile)
                Method wrapMethod;
                try {
                    wrapMethod = cbProfileClass.getMethod(
                            "fromProfile",
                            Class.forName("com.mojang.authlib.GameProfile")
                    );
                } catch (NoSuchMethodException nope) {
                    wrapMethod = cbProfileClass.getMethod(
                            "of",
                            Class.forName("com.mojang.authlib.GameProfile")
                    );
                }

                Object wrappedProfile = wrapMethod.invoke(null, gameProfile);

                Method setProfileMethod = skullMeta.getClass().getMethod(
                        "setPlayerProfile",
                        Class.forName("org.bukkit.profile.PlayerProfile")
                );
                setProfileMethod.invoke(skullMeta, wrappedProfile);

                applied = true;
                dbg("buildCustomPresentHead(): SUCCESS via modern CraftPlayerProfile#setPlayerProfile");
            } catch (Throwable t) {
                dbg("buildCustomPresentHead(): modern path fail: " +
                        t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }

        // ------------------------------------------------
        // Strategy 2: legacy Paper path
        // ------------------------------------------------
        if (!applied && gameProfile != null) {
            try {
                dbg("buildCustomPresentHead(): trying legacy Paper CraftPlayerProfile path");
                Class<?> legacyCP = Class.forName("com.destroystokyo.paper.profile.CraftPlayerProfile");
                Constructor<?> cons = legacyCP.getDeclaredConstructor(
                        Class.forName("com.mojang.authlib.GameProfile")
                );
                cons.setAccessible(true);

                Object legacyWrapped = cons.newInstance(gameProfile);

                Method setLegacy = skullMeta.getClass().getMethod(
                        "setPlayerProfile",
                        Class.forName("com.destroystokyo.paper.profile.PlayerProfile")
                );
                setLegacy.invoke(skullMeta, legacyWrapped);

                applied = true;
                dbg("buildCustomPresentHead(): SUCCESS via legacy Paper CraftPlayerProfile#setPlayerProfile");
            } catch (Throwable t) {
                dbg("buildCustomPresentHead(): legacy path fail: " +
                        t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }

        // ------------------------------------------------
        // Strategy 3: direct "profile" field with GameProfile (old Spigot)
        // ------------------------------------------------
        if (!applied && gameProfile != null) {
            try {
                dbg("buildCustomPresentHead(): trying direct SkullMeta.profile (GameProfile) path");
                Field profileField = skullMeta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);

                // try setting raw GameProfile first
                profileField.set(skullMeta, gameProfile);

                applied = true;
                dbg("buildCustomPresentHead(): SUCCESS via profile field (GameProfile) injection");
            } catch (IllegalArgumentException iae) {
                // This is exactly what we're seeing now: CraftMetaSkull.profile is not a GameProfile,
                // it's a net.minecraft.world.item.component.ResolvableProfile.
                dbg("buildCustomPresentHead(): profile(GameProfile) injection fail: " +
                        iae.getClass().getSimpleName() + ": " + iae.getMessage());

                // We'll handle ResolvableProfile below (Strategy 4).
            } catch (Throwable t) {
                dbg("buildCustomPresentHead(): profile(GameProfile) injection other fail: " +
                        t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }

        // ------------------------------------------------
        // Strategy 4: direct "profile" field with ResolvableProfile (new Paper)
        // ------------------------------------------------
        if (!applied && gameProfile != null) {
            try {
                dbg("buildCustomPresentHead(): trying ResolvableProfile injection");

                // 4a. Get the CraftMetaSkull.profile field
                Field profileField = skullMeta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);

                Class<?> resolvableProfileClass =
                        Class.forName("net.minecraft.world.item.component.ResolvableProfile");

                dbg("buildCustomPresentHead(): located ResolvableProfile class=" + resolvableProfileClass.getName());

                // We now need to construct a ResolvableProfile that wraps our GameProfile.
                // Different Paper builds expose different constructors / factory methods.
                // Try common patterns:

                Object resolvableProfile = null;

                // Pattern A: new ResolvableProfile(GameProfile)
                try {
                    Constructor<?> rpCtorA = resolvableProfileClass.getDeclaredConstructor(
                            Class.forName("com.mojang.authlib.GameProfile")
                    );
                    rpCtorA.setAccessible(true);
                    resolvableProfile = rpCtorA.newInstance(gameProfile);
                    dbg("buildCustomPresentHead(): built ResolvableProfile via (GameProfile) ctor");
                } catch (NoSuchMethodException nopeA) {
                    dbg("buildCustomPresentHead(): no (GameProfile) ctor on ResolvableProfile, trying other patterns");
                }

                // Pattern B: static of(GameProfile)
                if (resolvableProfile == null) {
                    try {
                        Method ofMethod = resolvableProfileClass.getDeclaredMethod(
                                "of",
                                Class.forName("com.mojang.authlib.GameProfile")
                        );
                        ofMethod.setAccessible(true);
                        resolvableProfile = ofMethod.invoke(null, gameProfile);
                        dbg("buildCustomPresentHead(): built ResolvableProfile via static of(GameProfile)");
                    } catch (NoSuchMethodException nopeB) {
                        dbg("buildCustomPresentHead(): no static of(GameProfile) on ResolvableProfile");
                    }
                }

                // Pattern C: static create(GameProfile, boolean)
                if (resolvableProfile == null) {
                    try {
                        Method createMethod = resolvableProfileClass.getDeclaredMethod(
                                "create",
                                Class.forName("com.mojang.authlib.GameProfile"),
                                boolean.class
                        );
                        createMethod.setAccessible(true);
                        // second arg guess: "true" to mark complete
                        resolvableProfile = createMethod.invoke(null, gameProfile, true);
                        dbg("buildCustomPresentHead(): built ResolvableProfile via static create(GameProfile, boolean)");
                    } catch (NoSuchMethodException nopeC) {
                        dbg("buildCustomPresentHead(): no static create(GameProfile, boolean) either");
                    }
                }

                if (resolvableProfile == null) {
                    dbg("buildCustomPresentHead(): FAILED to build any ResolvableProfile wrapper -> cannot inject texture");
                } else {
                    // We actually SET the profile field to our resolvableProfile now:
                    profileField.set(skullMeta, resolvableProfile);
                    applied = true;
                    dbg("buildCustomPresentHead(): SUCCESS ResolvableProfile injected into SkullMeta.profile");
                }

            } catch (Throwable t) {
                dbg("buildCustomPresentHead(): ResolvableProfile injection fail: " +
                        t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }

        // ------------------------------------------------
        // Finalize and verify
        // ------------------------------------------------
        head.setItemMeta(skullMeta);

        ItemMeta verifyMeta = head.getItemMeta();
        if (verifyMeta instanceof SkullMeta verifySkull) {
            dbg("buildCustomPresentHead(): post-set verify -> meta is SkullMeta class=" +
                    verifySkull.getClass().getName());
            try {
                dbg("buildCustomPresentHead(): post-set verify -> owningPlayer=" +
                        (verifySkull.getOwningPlayer() == null
                                ? "null"
                                : verifySkull.getOwningPlayer().getName() + "/" +
                                verifySkull.getOwningPlayer().getUniqueId()));
            } catch (Throwable t) {
                dbg("buildCustomPresentHead(): post-set verify getOwningPlayer() threw " +
                        t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        } else {
            dbg("buildCustomPresentHead(): post-set verify -> meta is NOT SkullMeta, it's " +
                    (verifyMeta == null ? "null" : verifyMeta.getClass().getName()));
        }

        dbg("buildCustomPresentHead(): done. appliedTexture=" + applied);
        return head;
    }



    /**
     * Makes a PLAYER_HEAD using setOwningPlayer(owner) so we get that account's skin.
     */
    private ItemStack buildOwnerHead(String headOwnerName,
                                     int amount,
                                     String displayName,
                                     List<String> loreLines) {

        dbg("buildOwnerHead(): start headOwnerName='" + headOwnerName + "', amount=" + amount);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD, amount);
        ItemMeta rawMeta = head.getItemMeta();

        if (!(rawMeta instanceof SkullMeta)) {
            dbg("buildOwnerHead(): ItemMeta is not SkullMeta (" +
                    (rawMeta == null ? "null" : rawMeta.getClass().getName()) +
                    "). Returning plain head.");
            return head;
        }

        SkullMeta skullMeta = (SkullMeta) rawMeta;

        // Name/lore
        if (displayName != null && !displayName.isEmpty()) {
            String coloredName = displayName.replace("&", "§");
            skullMeta.setDisplayName(coloredName);
            dbg("buildOwnerHead(): set display name -> " + coloredName);
        } else {
            dbg("buildOwnerHead(): no custom display name");
        }

        if (loreLines != null && !loreLines.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : loreLines) {
                coloredLore.add(line.replace("&", "§"));
            }
            skullMeta.setLore(coloredLore);
            dbg("buildOwnerHead(): set lore -> " + coloredLore);
        } else {
            dbg("buildOwnerHead(): no lore lines");
        }

        if (headOwnerName != null && !headOwnerName.isEmpty()) {
            try {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(headOwnerName);
                dbg("buildOwnerHead(): fetched OfflinePlayer uuid=" +
                        offline.getUniqueId() + " name=" + offline.getName());
                skullMeta.setOwningPlayer(offline);
                dbg("buildOwnerHead(): skullMeta.setOwningPlayer success");
            } catch (Throwable t) {
                dbg("buildOwnerHead(): setOwningPlayer FAILED for '" + headOwnerName + "': " + t.getMessage());
            }
        } else {
            dbg("buildOwnerHead(): headOwnerName empty -> leaving default skin (Steve/Alex)");
        }

        head.setItemMeta(skullMeta);
        dbg("buildOwnerHead(): final meta applied. type=" + head.getType());

        return head;
    }

    // --------------------------------------------------------------------
    // Cosmetic helpers
    // --------------------------------------------------------------------

    private ItemStack applyNameAndLore(ItemStack base,
                                       String displayName,
                                       List<String> loreLines) {
        dbg("applyNameAndLore(): start for material=" + base.getType());

        if (base == null) return null;
        ItemMeta meta = base.getItemMeta();
        if (meta == null) {
            dbg("applyNameAndLore(): meta is null (can't set name/lore)");
            return base;
        }

        if (displayName != null && !displayName.isEmpty()) {
            String coloredName = displayName.replace("&", "§");
            meta.setDisplayName(coloredName);
            dbg("applyNameAndLore(): set name -> " + coloredName);
        } else {
            dbg("applyNameAndLore(): no displayName");
        }

        if (loreLines != null && !loreLines.isEmpty()) {
            List<String> colored = new ArrayList<>();
            for (String line : loreLines) {
                colored.add(line.replace("&", "§"));
            }
            meta.setLore(colored);
            dbg("applyNameAndLore(): set lore -> " + colored);
        } else {
            dbg("applyNameAndLore(): no lore lines");
        }

        base.setItemMeta(meta);
        dbg("applyNameAndLore(): done");
        return base;
    }

    // --------------------------------------------------------------------
    // Utility helpers
    // --------------------------------------------------------------------

    private void broadcast(String rawMessage, Location where) {
        if (where == null) {
            dbg("broadcast(): where==null, skipping");
            return;
        }

        String msg = (rawMessage == null ? "&aSanta spawned at %location%" : rawMessage)
                .replace("%location%",
                        where.getBlockX() + "," + where.getBlockY() + "," + where.getBlockZ())
                .replace("&", "§");

        String prefix = plugin.getConfig("config.yml")
                .getString("messages.prefix", "")
                .replace("&", "§");

        dbg("broadcast(): finalMessage='" + prefix + msg + "' playerCount=" + Bukkit.getOnlinePlayers().size());

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(prefix + msg);
        }
    }

    private Location createLocationFromConfig(ConfigurationSection sec) {
        if (sec == null) {
            dbg("createLocationFromConfig(): section null");
            return null;
        }

        String worldName = sec.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("[UltimateChristmas] World not found for Santa spawn: " + worldName);
            dbg("createLocationFromConfig(): world '" + worldName + "' NOT FOUND");
            return null;
        }

        double x = sec.getDouble("location.x", 0.0);
        double z = sec.getDouble("location.z", 0.0);

        double y = sec.getDouble(
                "location.y",
                world.getHighestBlockYAt((int) Math.round(x), (int) Math.round(z))
        );

        dbg("createLocationFromConfig(): world=" + worldName +
                " raw=(" + x + "," + y + "," + z + ")");

        return new Location(world, x, y, z);
    }

    private Location snapToGround(Location loc) {
        if (loc == null) {
            dbg("snapToGround(): loc null");
            return null;
        }
        World w = loc.getWorld();
        if (w == null) {
            dbg("snapToGround(): world null for loc");
            return loc;
        }

        int groundY = Math.max(1, w.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()));
        Location snapped = new Location(
                w,
                loc.getBlockX() + 0.5,
                groundY + 1,
                loc.getBlockZ() + 0.5
        );

        dbg("snapToGround(): in=" + loc(loc) + " out=" + loc(snapped) + " groundY=" + groundY);
        return snapped;
    }

    private void loadChunk(Location l) {
        if (l == null || l.getWorld() == null) {
            dbg("loadChunk(): skipped (location/world null)");
            return;
        }
        Chunk c = l.getChunk();
        if (!c.isLoaded()) {
            boolean ok = c.load(true);
            dbg("loadChunk(): loaded=" + ok +
                    " chunk=(" + c.getX() + "," + c.getZ() + ") world=" + l.getWorld().getName());
        } else {
            dbg("loadChunk(): already loaded chunk=(" + c.getX() + "," + c.getZ() + ")");
        }
    }

    private void applyDisguise(Mob base, String skinName, String displayNameColored) {
        dbg("applyDisguise(): start skinName=" + skinName +
                " entity=" + (base == null ? "null" : base.getUniqueId()));

        if (base == null) {
            dbg("applyDisguise(): base mob null, skipping disguise");
            return;
        }

        try {
            PlayerDisguise disguise = new PlayerDisguise(skinName);
            disguise.setSkin(skinName);

            PlayerWatcher watcher = disguise.getWatcher();
            watcher.setCustomName(displayNameColored);
            watcher.setCustomNameVisible(true);

            disguise.setDisplayedInTab(false);
            disguise.setReplaceSounds(true);

            DisguiseAPI.disguiseToAll(base, disguise);

            boolean ok = DisguiseAPI.isDisguised(base);
            dbg("applyDisguise(): disguise applied. success=" + ok);
            if (!ok) {
                warn("applyDisguise(): entity not marked disguised. Possibly bad skin '" + skinName + "'");
            }
        } catch (Throwable t) {
            warn("applyDisguise(): failure -> " + t.getMessage());
        }
    }

    // --------------------------------------------------------------------
    // Low-level helpers
    // --------------------------------------------------------------------

    private long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    private String loc(Location l) {
        if (l == null || l.getWorld() == null) return "(null)";
        return "(" + l.getWorld().getName() + " " +
                l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ")";
    }

    /**
     * DEBUG SWITCH.
     *
     * We now check BOTH:
     * - main config.yml ("debug": true)
     * - santa.yml ("debug": true)
     *
     * If either is true, debug logs print.
     */
    private boolean debugEnabled() {
        boolean mainDebug = false;
        boolean santaDebug = false;

        try {
            mainDebug = plugin.getConfig().getBoolean("debug", false);
        } catch (Throwable ignored) {}

        try {
            FileConfiguration santaCfg = plugin.getConfig("santa.yml");
            if (santaCfg != null) {
                santaDebug = santaCfg.getBoolean("debug", false);
            }
        } catch (Throwable ignored) {}

        return mainDebug || santaDebug;
    }

    private void dbg(String msg) {
        if (debugEnabled()) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }

    private void warn(String msg) {
        if (debugEnabled()) {
            plugin.getLogger().warning("[DEBUG] " + msg);
        } else {
            plugin.getLogger().warning(msg);
        }
    }

    private static final class SpawnChoice {
        final String key;
        final Location rawLocation;
        final String displayNameColored;

        private SpawnChoice(String key, Location rawLocation, String displayNameColored) {
            this.key = key;
            this.rawLocation = rawLocation;
            this.displayNameColored = displayNameColored;
        }
    }
}
