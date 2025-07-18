package com.leomelonseeds.missilewars.invs;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.text.Component;

public class ArenaSelector extends MWInventory {

    private String gamemode;
    
    public ArenaSelector(Player player, String gamemode) {
        super(player, 27, ConfigUtils.getConfigText("inventories.game-selector.title", null, null, null));
        this.gamemode = gamemode;
        autoRefresh(20);
    }
    
    // Create a list of all arenas from ArenaManager list
    @Override
    public void updateInventory() {
        inv.clear();
        for (Arena arena : MissileWarsPlugin.getPlugin().getArenaManager().getLoadedArenas(gamemode)) {
            int playerCount = arena.getNumPlayers();
            ItemStack arenaItem = new ItemStack(Material.TNT, Math.max(1, playerCount));
            ItemMeta arenaItemMeta = arenaItem.getItemMeta();
            String display = ConfigUtils.getConfigText("inventories.game-selector.game-item.name", player, arena, null);
            List<Component> lore = new ArrayList<>();
            for (String s : ConfigUtils.getConfigTextList("inventories.game-selector.game-item.lore", player, arena, null)) {
                lore.add(ConfigUtils.toComponent(s));
            }
            arenaItemMeta.displayName(ConfigUtils.toComponent(display));
            arenaItemMeta.lore(lore);
            
            // Add glowing effect if people are inside
            if (playerCount >= 1) {
                arenaItemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                arenaItemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }

            arenaItem.setItemMeta(arenaItemMeta);
            inv.addItem(arenaItem);
        }
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena selectedArena = manager.getArena(slot, gamemode);
        if (selectedArena == null) {
            return;
        }

        // Attempt to send player to arena
        if (selectedArena.joinPlayer(player)) {
            player.closeInventory();
        }
    }
}
