package com.leomelonseeds.missilewars.invs;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class InventoryManager implements Listener {
    
    private Map<Player, MWInventory> inventoryCache;
    
    public InventoryManager() {
        inventoryCache = new HashMap<>();
    }
    
    public void registerInventory(Player player, MWInventory inv) {
        registerInventory(player, inv, true);
    }
    
    // Registers and opens an inventory
    public void registerInventory(Player player, MWInventory inv, boolean update) {
        // Run on next tick to give time for constructors to assign values
        Bukkit.getScheduler().runTask(MissileWarsPlugin.getPlugin(), () -> {
            unregister(player);
            player.openInventory(inv.getInventory());
            inventoryCache.put(player, inv);
            if (update) {
                inv.updateInventoryAsync();
            }
        });
    }
    
    /**
     * Forcibly unregister a player. Call for server
     * shutdowns only!
     * 
     * @param player
     */
    public void unregister(Player player) {
        MWInventory uinv = inventoryCache.remove(player);
        if (uinv == null) {
            return;
        }
        
        ConfigUtils.cancelTask(uinv.getAutoRefreshTask());
    }
    
    /** Handle clicking of custom GUIs */
    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        // Check if an inventory was even clicked
        Inventory inv = event.getClickedInventory();
        if (inv == null) {
            return;
        }
        
        // Check if inventory is custom
        Player player = (Player) event.getWhoClicked();
        MWInventory uinv = inventoryCache.get(player);
        if (uinv == null) {
            return;
        }
        
        // Do not allow shift clicking bottom inventory 
        if (inv.equals(event.getView().getBottomInventory()) && event.getClick().isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        
        // Allow clicking bottom inventory
        if (!inv.equals(event.getView().getTopInventory())){
            return; 
        }
        
        event.setCancelled(true);
        uinv.registerClick(event.getSlot(), event.getClick());
    }
    
    /** Unregister custom inventories when they are closed. */
    @EventHandler
    public void unregisterCustomInventories(InventoryCloseEvent event) {
        unregister((Player) event.getPlayer());
    }
}
