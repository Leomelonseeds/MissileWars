package com.leomelonseeds.missilewars.invs;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public abstract class MWInventory {
    
    protected static InventoryManager manager = MissileWarsPlugin.getPlugin().getInvs();
    protected Inventory inv;
    protected Player player;

    /**
     * Create a chest inventory
     * 
     * @param player
     * @param size must be a multiple of 9, <= 54
     * @param title
     */
    public MWInventory(Player player, int size, String title) {
        inv = Bukkit.createInventory(null, size, ConfigUtils.toComponent(title));
        manager.registerInventory(player, this);
        this.player = player;
    }

    /**
     * Create any non-chest inventory
     * 
     * @param player
     * @param type
     * @param title
     */
    public MWInventory(Player player, InventoryType type, String title) {
        inv = Bukkit.createInventory(null, type, ConfigUtils.toComponent(title));
        manager.registerInventory(player, this);
        this.player = player;
    }

    /**
     * Auto refreshes inventory every specified ticks until closed
     * 
     * @param ticks
     */
    protected void autoRefresh(int ticks) {
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
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), ticks, ticks);
    }
    
    /**
     * Change the title of the inventory
     * 
     * @param title
     */
    @SuppressWarnings("deprecation")
    public void setTitle(String title) {
        player.getOpenInventory().setTitle(title);
    }
    
    public abstract void updateInventory();
    
    public abstract void registerClick(int slot, ClickType type);
    
    public Inventory getInventory() {
        return inv;
    }
}
