package com.leomelonseeds.missilewars.invs;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.text.Component;

public class ArenaSelector implements MWInventory {

    private Inventory inv;
    private Player player;
    
    public ArenaSelector(Player player) {
        this.player = player;
        
        String title = ConfigUtils.getConfigText("inventories.game-selector.title", null, null, null);
        inv = Bukkit.createInventory(null, 27, Component.text(title));
        manager.registerInventory(player, this);
    }
    
    // Create a list of all arenas from ArenaManager list
    @Override
    public void updateInventory() {
        for (Arena arena : MissileWarsPlugin.getPlugin().getArenaManager().getLoadedArenas()) {
            ItemStack arenaItem = new ItemStack(Material.TNT, Math.max(1, arena.getNumPlayers()));
            ItemMeta arenaItemMeta = arenaItem.getItemMeta();
            assert arenaItemMeta != null;
            String display = ConfigUtils.getConfigText("inventories.game-selector.game-item.name", player, arena, null);
            List<Component> lore = new ArrayList<>();
            for (String s : ConfigUtils.getConfigTextList("inventories.game-selector.game-item.lore", player, arena, null)) {
                lore.add(Component.text(s));
            }
            arenaItemMeta.displayName(Component.text(display));
            arenaItemMeta.lore(lore);
            arenaItem.setItemMeta(arenaItemMeta);
            inv.addItem(arenaItem);
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    @Override
    public void registerClick(int slot) {
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena selectedArena = manager.getArena(slot);
        if (selectedArena == null) {
            return;
        }

        // Attempt to send player to arena
        if (selectedArena.joinPlayer(player)) {
            player.closeInventory();
        } else {
            ConfigUtils.sendConfigMessage("messages.arena-full", player, selectedArena, null);
        }
    }
}
