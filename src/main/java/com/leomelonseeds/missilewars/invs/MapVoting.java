package com.leomelonseeds.missilewars.invs;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class MapVoting implements Listener, InventoryHolder {
    
    private Inventory inv;
    
    public MapVoting(Player player) {
        String title = ConfigUtils.getConfigText("inventories.map-voting.title", null, null, null);
        inv = Bukkit.createInventory(this, 27, Component.text(title));
        updateInventory(player);
        player.openInventory(inv);
    }
    
    // Blank constructor to register events
    public MapVoting() {}
    
    // Register all maps and their votes to items
    public void updateInventory(Player player) {
        Arena arena = getPlayerArena(player);
        for (String mapName : arena.getMapVotes().keySet()) {
            ItemStack mapItem = new ItemStack(Material.PAPER);
            int votes = arena.getMapVotes().get(mapName);
            mapItem.setAmount(Math.max(votes, 1));
            ItemMeta mapItemMeta = mapItem.getItemMeta();
            String display = ConfigUtils.getMapText(arena.getMapType(), mapName, "name");
            mapItemMeta.displayName(Component.text(display));
            List<Component> lore = new ArrayList<>();
            for (String s : ConfigUtils.getConfigTextList("inventories.map-voting.vote-item.lore", player, null, null)) {
                String sn = s.replaceAll("%umw_map_votes%", "" + votes);
                lore.add(Component.text(sn));
            }
            mapItemMeta.lore(lore);
            mapItem.setItemMeta(mapItemMeta);
            inv.addItem(mapItem);
        }
    }
    
    @EventHandler
    public void onMapVote(InventoryClickEvent event) {
        // Check if player is in an Arena
        if (!(event.getInventory().getHolder() instanceof MapVoting)) {
            return;
        }
        
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        Arena arena = getPlayerArena(player);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
            return;
        }
        String map = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
        String mapVotedFor = arena.registerVote(player.getUniqueId(), map);
        player.sendMessage(ChatColor.GREEN + "Voted for " + mapVotedFor);
        updateInventory(player);
    }
    
    private Arena getPlayerArena(Player player) {
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        return manager.getArena(player.getUniqueId());
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

}
