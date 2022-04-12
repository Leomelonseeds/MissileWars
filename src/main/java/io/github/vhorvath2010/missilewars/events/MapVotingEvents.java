package io.github.vhorvath2010.missilewars.events;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;

public class MapVotingEvents implements Listener {

    @EventHandler
    public void onMapVote(InventoryClickEvent event) {
        // Check if player is in an Arena
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena == null) {
            return;
        }

        // Ensure map vote inventory is open
        if (!event.getView().getTitle().equals(ConfigUtils.getConfigText("inventories.map-voting.title", player, null, null))) {
            return;
        }

        // Cancel click and try to register vote
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
            return;
        }
        String mapVotedFor = arena.registerVote(player.getUniqueId(), clicked.getItemMeta().getDisplayName());
        player.sendMessage(ChatColor.GREEN + "Voted for " + mapVotedFor);
        arena.openMapVote(player);
    }

}
