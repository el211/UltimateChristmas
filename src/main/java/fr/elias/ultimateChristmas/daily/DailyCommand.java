// fr/elias/ultimateChristmas/daily/DailyCommand.java
package fr.elias.ultimateChristmas.daily;

import fr.minuskube.inv.InventoryManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DailyCommand implements CommandExecutor {

    private final InventoryManager invManager;
    private final DailyGiftManager giftManager;
    private final DailyProgressStore store;

    public DailyCommand(InventoryManager invManager, DailyGiftManager giftManager, DailyProgressStore store) {
        this.invManager = invManager;
        this.giftManager = giftManager;
        this.store = store;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        new DailyCalendarView(invManager, giftManager, store).open(p);
        return true;
    }
}
