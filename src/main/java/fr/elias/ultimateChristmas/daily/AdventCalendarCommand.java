// fr/elias/ultimateChristmas/daily/AdventCalendarCommand.java
package fr.elias.ultimateChristmas.daily;

import fr.minuskube.inv.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class AdventCalendarCommand implements CommandExecutor, TabCompleter {

    private final InventoryManager invManager;
    private final DailyGiftManager giftManager;
    private final DailyProgressStore store;

    // Permissions:
    // - ultimatechristmas.advent              : use /advent (open self, remaining)
    // - ultimatechristmas.advent.admin        : claim/open others, streak, reload (optional)
    public AdventCalendarCommand(InventoryManager invManager,
                                 DailyGiftManager giftManager,
                                 DailyProgressStore store) {
        this.invManager = invManager;
        this.giftManager = giftManager;
        this.store = store;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String[] args) {
        // /advent -> open self
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            new DailyCalendarView(invManager, giftManager, store).open(p);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "open": { // /advent open [player]
                if (args.length == 1) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }
                    new DailyCalendarView(invManager, giftManager, store).open(p);
                    return true;
                }
                // open others (admin)
                if (!sender.hasPermission("ultimatechristmas.advent.admin")) {
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                new DailyCalendarView(invManager, giftManager, store).open(target);
                sender.sendMessage("§aOpened Advent Calendar for §f" + target.getName());
                return true;
            }

            case "claim": { // /advent claim [player]
                if (args.length == 1) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }
                    giftManager.claimFromGUI(p);
                    return true;
                }
                // claim for others (admin)
                if (!sender.hasPermission("ultimatechristmas.advent.admin")) {
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                giftManager.claimFromGUI(target);
                sender.sendMessage("§aForced claim for §f" + target.getName());
                return true;
            }

            case "remaining": { // /advent remaining
                String txt = giftManager.timeUntilResetText();
                sender.sendMessage("§eNext reset in §f" + txt + "§e.");
                return true;
            }

            case "streak": { // /advent streak [player]
                if (!(sender.hasPermission("ultimatechristmas.advent") || sender instanceof Player)) {
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                if (args.length == 1) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage("§cUsage: /" + label + " streak <player>");
                        return true;
                    }
                    int s = store.getStreak(p.getUniqueId());
                    sender.sendMessage("§eYour current streak: §f" + s);
                    return true;
                } else {
                    if (!sender.hasPermission("ultimatechristmas.advent.admin")) {
                        sender.sendMessage("§cYou don't have permission.");
                        return true;
                    }
                    OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                    int s = store.getStreak(op.getUniqueId());
                    sender.sendMessage("§eStreak for §f" + op.getName() + "§e: §f" + s);
                    return true;
                }
            }

            case "help": {
                help(sender, label);
                return true;
            }

            default:
                help(sender, label);
                return true;
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§6§lAdvent Calendar Commands:");
        sender.sendMessage("§e/" + label + " §7- open your calendar");
        sender.sendMessage("§e/" + label + " open [player] §7- open calendar (self or others*)");
        sender.sendMessage("§e/" + label + " claim [player] §7- claim today's reward (self or others*)");
        sender.sendMessage("§e/" + label + " remaining §7- show time until reset");
        sender.sendMessage("§e/" + label + " streak [player] §7- show streak");
        if (sender.hasPermission("ultimatechristmas.advent.admin")) {
            sender.sendMessage("§8* Requires §7ultimatechristmas.advent.admin");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("open", "claim", "remaining", "streak", "help"));
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return base.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("open") || sub.equals("claim") || sub.equals("streak")) {
                if (sender.hasPermission("ultimatechristmas.advent.admin")) {
                    String prefix = args[1].toLowerCase(Locale.ROOT);
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .sorted()
                            .collect(Collectors.toList());
                }
            }
        }
        return Collections.emptyList();
    }
}
