package com.leomelonseeds.missilewars.listener.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.decks.Ability;
import com.leomelonseeds.missilewars.decks.DeckItem;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class EngineerManager implements Listener {
    
    private static EngineerManager instance;
    
    private Map<UUID, EngineerSession> sessions;
    private Map<String, ItemStack> oldItems; // old data, new
    
    private EngineerManager() {
        this.sessions = new HashMap<>();
        this.oldItems = new HashMap<>();
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    private void onDropItem(PlayerDropItemEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        
        if (event.getItemDrop().getItemStack().getType() != Material.IRON_PICKAXE) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType() == Material.AIR) {
            return;
        }
        
        Arena arena = ArenaUtils.getArena(player);
        if (arena == null) {
            return;
        }
        
        int engineer = MissileWarsPlugin.getPlugin().getJSON().getLevel(player.getUniqueId(), Ability.ENGINEER);
        if (engineer <= 0) {
            return;
        }

        if (!player.isSneaking()) {
            endSession(player);
            return;
        }

        if (engineer <= 1 && !offhand.getType().toString().contains("SPAWN_EGG")) {
            return;
        }
        
        if (resetItem(player, offhand, arena)) {
            return;
        }

        startOrSaveSession(player, offhand, arena);
    }
    
    
    /**
     * Check if a player can start a session and if so, starts the session
     * by finding the player arena and instance deck item associated. If a
     * session is already started, then attempt to save the clipboard
     * 
     * @param player
     * @param item
     * 
     */
    private void startOrSaveSession(Player player, ItemStack item, Arena arena) {
        UUID uuid = player.getUniqueId();
        TeamName team = arena.getTeam(uuid);
        if (team == TeamName.NONE) {
            return;
        }
        
        if (sessions.containsKey(uuid)) {
            Bukkit.getScheduler().runTaskAsynchronously(MissileWarsPlugin.getPlugin(), () -> {
                if (sessions.get(uuid).save()) {
                    sessions.remove(uuid);
                } else {
                    Bukkit.getScheduler().runTask(MissileWarsPlugin.getPlugin(), () -> 
                        ConfigUtils.sendConfigSound("engineer.cancel", player));
                }
            });
            return;
        }
        
        String structure = InventoryUtils.getStringFromItem(item, "item-structure");
        if (structure == null) {
            return;
        }

        MissileWarsPlayer mwp = arena.getPlayerInArena(uuid);
        ItemStack instanceItem = findInstanceItem(mwp, item);
        sessions.put(uuid, new EngineerSession(player, instanceItem, structure, team));
    }
    
    /**
     * End a session for a player, if such session exists
     * 
     * @param player
     */
    public void endSession(Player player) {
        UUID uuid = player.getUniqueId();
        if (!sessions.containsKey(uuid)) {
            return;
        }
        
        sessions.remove(uuid).end(false, false);
    }
    
    public void removeSession(UUID uuid) {
        sessions.remove(uuid);
    }
    
    /**
     * Saves a clone of the given item in case it needs
     * to be restored later
     * 
     * @param newId
     * @param item
     */
    public void saveOldItem(String newId, ItemStack item) {
        oldItems.put(newId, item.clone());
    }
    
    /**
     * Resets player back to their old item if they are holding a custom one
     * 
     * @param player
     * @param item
     * @param arena
     * @return
     */
    private boolean resetItem(Player player, ItemStack item, Arena arena) {
        String structure = InventoryUtils.getStringFromItem(item, "item-structure");
        if (structure == null || !oldItems.containsKey(structure)) {
            return false;
        }
        
        MissileWarsPlayer mwp = arena.getPlayerInArena(player.getUniqueId());
        DeckItem di = mwp.getDeck().getDeckItem(item);
        if (di == null) {
            return false;
        }
        
        ItemStack oldItem = oldItems.remove(structure);
        di.setInstanceItem(oldItem);
        
        ItemStack toGive = oldItem.clone();
        toGive.addUnsafeEnchantments(item.getEnchantments());
        toGive.setAmount(item.getAmount());
        player.getInventory().setItemInOffHand(toGive);
        
        ConfigUtils.sendConfigMessage("engineer.reset", player);
        ConfigUtils.sendConfigSound("engineer-reset", player.getLocation());
        return true;
    }
    
    private ItemStack findInstanceItem(MissileWarsPlayer mwp, ItemStack item) {
        DeckItem di = mwp.getDeck().getDeckItem(item);
        if (di == null) {
            return null;
        }
        
        return di.getInstanceItem();
    }
    
    
    public static EngineerManager getInstance() {
        if (instance == null) {
            instance = new EngineerManager();
        }
        
        return instance;
    }

}
