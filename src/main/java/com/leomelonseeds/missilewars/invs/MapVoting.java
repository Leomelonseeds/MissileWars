package com.leomelonseeds.missilewars.invs;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
        int size = getPlayerArena(player).getVoteManager().getVotes().size();
        inv = Bukkit.createInventory(null, (int) Math.ceil((double) size / 9) * 9, ConfigUtils.toComponent(title));
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
            // Set item + amount
            ItemStack mapItem = new ItemStack(Material.PAPER);
            int votes = arena.getVoteManager().getVotes().get(mapName);
            mapItem.setAmount(Math.max(votes, 1));
            
            // Set display name to map name
            ItemMeta mapItemMeta = mapItem.getItemMeta();
            String display = ConfigUtils.getMapText(arena.getGamemode(), mapName, "name");
            mapItemMeta.displayName(ConfigUtils.toComponent(display));
            
            // Add lore
            List<Component> lore = new ArrayList<>();
            for (String s : ConfigUtils.getConfigTextList("inventories.map-voting.vote-item.lore", player, null, null)) {
                String sn = s.replaceAll("%umw_map_votes%", "" + votes);
                lore.add(ConfigUtils.toComponent(sn));
            }
            mapItemMeta.lore(lore);
            
            // Add map name meta
            mapItemMeta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "map"),
                    PersistentDataType.STRING, mapName);
            
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
        
        // Get meta
        ItemMeta meta = clicked.getItemMeta();
        if (!meta.getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "map"))) {
            return;
        }
        
        // Get map name
        String map = meta.getPersistentDataContainer().get(new NamespacedKey(MissileWarsPlugin.getPlugin(), "map"),
                PersistentDataType.STRING);
        arena.getVoteManager().registerVote(player, map, type == ClickType.RIGHT);
        ConfigUtils.sendConfigSound("use-skillpoint", player);
        updateInventory();
    }
}
