package com.leomelonseeds.missilewars.decks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class DeckItem {
    
    private ItemStack item;
    private int cooldown; // in ticks
    private int max;
    private int curCooldown;
    private Player player;
    BukkitTask cooldownTask;
    boolean unavailable;
    
    /**
     * @param item should correspond directly the player inventory's item
     * @param cooldown must be a multiple of 2 (updates every 100ms)
     * @param max
     */
    public DeckItem(ItemStack item, int cooldown, int max, Player player) {
        this.item = item;
        this.cooldown = cooldown;
        this.max = max;
        this.curCooldown = 0;
        this.player = player;
        this.unavailable = false;
    }
    
    /**
     * Checks if given itemstack is the deckitem
     * 
     * @param i
     * @return
     */
    public boolean matches(ItemStack i) {
        return item.isSimilar(i);
    }
    
    /**
     * Invoke after item is used, check matches()
     * Restocking the item if used should be handled
     * before calling this. This method simply registers
     * the cooldown changes
     * 
     * @param makeUnavailable whether the material cooldown should be applied
     * 
     * @return the cooldown after consuming in ticks, 0 if no cooldown
     */
    public int consume(boolean makeUnavailable) {
        if (makeUnavailable) {
            unavailable = true;
            player.setCooldown(item.getType(), cooldown);
        }
        curCooldown = cooldown;
        updateItem();
        return cooldown;
    }
    
    // Item update task, handles giving items
    private void updateItem() {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        cooldownTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (ConfigUtils.outOfBounds(player, plugin.getArenaManager().getArena(player.getUniqueId()))) {
                curCooldown = cooldown;
                if (unavailable) {
                    player.setCooldown(item.getType(), cooldown);
                }
            } else if (curCooldown == 0) {
                int amt = item.getAmount();
                if (amt >= max) {
                    return;
                }
                
                if (!unavailable) {
                    item.setAmount(++amt);
                }
                unavailable = false;
                
                if (amt < max) {
                    curCooldown = cooldown;
                    updateItem();
                }
            } else {
                curCooldown -= 2;
                updateItem(); 
            }
        }, 2);
    }
    
    /**
     * Update this method every 2 ticks to be exactly accurate
     * 
     * @return
     */
    public int getCurrentCooldown() {
        return curCooldown;
    }
    
    /**
     * Set the cooldown of an item. If c is 0, the item will be 
     * immediately available/given. The inputted value will be rounded
     * down to the nearest multiple of 2. Only works if the item
     * already has a cooldown.
     */
    public void setCurrentCooldown(int c) {
        if (curCooldown == 0) {
            return;
        }
        
        if (c % 2 == 1) {
            c--;
        }
        
        if (unavailable()) {
            player.setCooldown(item.getType(), c * 2);
        }
        
        curCooldown = c;
    }
    
    public int getCooldown() {
        return cooldown;
    }
    
    public void setCooldown(int c) {
        cooldown = c;
    }
    
    public int getMax() {
        return max;
    }
    
    /**
     * @return a modifiable ItemStack object!
     */
    public ItemStack getItem() {
        return item;
    }
    
    /**
     * @return true if the item is unavailable, eg visible cooldown
     */
    public boolean unavailable() {
        return unavailable;
    }
    
    /**
     * Effectively permanently stops the deck from running
     * Before calling this, make sure the player can no longer
     * use any of their deck items (cancel interacts)
     */
    public void stop() {
        if (cooldownTask != null) {
            cooldownTask.cancel();
        }
        
        curCooldown = 0;
        player.setCooldown(item.getType(), 0);
    }
    
    /**
     * Assuming the item is already checked for matches(), instructs
     * the item to pickup the specified amount.
     * 
     * @param amount
     * @return the amount of items left over after the pickup, and -1 if unable to pickup
     */
    public int pickup(int amount) {
        if (item.getAmount() >= max) {
            return -1;
        }
        
        int actualAmount = unavailable ? 0 : item.getAmount();
        int toPickup = Math.min(max - actualAmount, amount);
        int leftOver = amount - toPickup;
        
        if (unavailable) {
            player.setCooldown(item.getType(), 0);
        }
        
        item.setAmount(actualAmount + toPickup);
        if (actualAmount + toPickup >= max) {
            if (cooldownTask != null) {
                cooldownTask.cancel();
            }
            curCooldown = 0;
        }
        
        return leftOver;
    }
 }
