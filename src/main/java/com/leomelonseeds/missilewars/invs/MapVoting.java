package com.leomelonseeds.missilewars.invs;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.text.Component;

public class MapVoting implements MWInventory {
    
    private Inventory inv;
    private Player player;
    
    public MapVoting(Player player) {
        this.player = player;
        
        String title = ConfigUtils.getConfigText("inventories.map-voting.title", null, null, null);
        inv = Bukkit.createInventory(null, 27, Component.text(title));
        manager.registerInventory(player, this);
        
        // Refresh inventory once in a while
        new BukkitRunnable() {
            @Override
            public void run() {
                if (manager.getInventory(player) != null) {
                    updateInventory();
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), 20, 20);
    }
    
    // Register all maps and their votes to items
    @Override
    public void updateInventory() {
        Arena arena = getPlayerArena(player);
        inv.clear();
        for (String mapName : arena.getVoteManager().getVotes().keySet()) {
            ItemStack mapItem = new ItemStack(Material.PAPER);
            int votes = arena.getVoteManager().getVotes().get(mapName);
            mapItem.setAmount(Math.max(votes, 1));
            ItemMeta mapItemMeta = mapItem.getItemMeta();
            String display = ConfigUtils.getMapText(arena.getGamemode(), mapName, "name");
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
    
    private Arena getPlayerArena(Player player) {
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        return manager.getArena(player.getUniqueId());
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        Arena arena = getPlayerArena(player);
        
        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
            return;
        }
        String map = ConfigUtils.toPlain(clicked.getItemMeta().displayName());
        arena.getVoteManager().registerVote(player, map, type == ClickType.RIGHT);
        player.sendMessage(ChatColor.GREEN + "Voted for " + map);
        updateInventory();
    }
}
