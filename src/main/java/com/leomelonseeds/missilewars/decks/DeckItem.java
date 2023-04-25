package com.leomelonseeds.missilewars.decks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class DeckItem {
    
    private ItemStack item;
    private int cooldown; // in seconds
    private int max;
    private double curCooldown;
    private Player player;
    BukkitTask cooldownTask;
    boolean unavailable;
    
    /**
     * @param item should correspond directly the player inventory's item (but its fine if it doesn't I guess)
     * @param cooldown is in seconds
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
     * Invoke after item is used or dropped, check matches()
     */
    public void consume(boolean makeUnavailable) {
        if (makeUnavailable) {
            unavailable = true;
            setVisualCooldown(curCooldown > 0 ? curCooldown : cooldown);
        }
        
        if (curCooldown == 0) {
            curCooldown = cooldown;
            updateItem();
        }
    }
    
    // Item update task, handles giving items
    private void updateItem() {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        cooldownTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (curCooldown - 0.5 == 0) {
                int amt = getActualAmount();
                if (amt >= max) {
                    return;
                }
                
                getItem().setAmount(++amt);
                unavailable = false;
                curCooldown = 0;
                player.updateInventory();
                
                if (amt < max) {
                    curCooldown = cooldown;
                    updateItem();
                }
            } else if (ConfigUtils.outOfBounds(player, plugin.getArenaManager().getArena(player.getUniqueId()))) {
                if (unavailable) {
                    setVisualCooldown(curCooldown);
                }
                updateItem();
            } else {
                curCooldown -= 0.5;
                updateItem(); 
            }
        }, 10L);
    }
    
    /**
     * Accurate to 1/2 of second
     * 
     * @return
     */
    public double getCurrentCooldown() {
        return curCooldown;
    }
    
    /**
     * If the item has a cooldown, it will be changed to c. If c is
     * 0 in that case, then the item will be instantly given.
     * Otherwise the method does nothing.
     * 
     * @param c
     */
    public void setCurrentCooldown(int c) {
        curCooldown = c;
    }
    
    /**
     * Initializes the cooldown of an item, use for game starts.
     * Adds visual cooldown of c. If 0 or existing cooldown then 
     * no cooldown will be added added
     */
    public void initCooldown(int c) {
        if (curCooldown > 0) {
            return;
        }
        
        if (c == 0) {
            if (getActualAmount() < max) {
                curCooldown = cooldown;
                updateItem();
            }
            return;
        }

        setVisualCooldown(c);
        unavailable = true;
        curCooldown = c;
        updateItem();
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
     * @return a modifiable ItemStack object that represents the item
     * in the player's inventory. Should not return null.
     */
    public ItemStack getItem() {
        for (ItemStack i : player.getInventory().getContents()) {
            if (matches(i)) {
                return i;
            }
        }
        return null;
    }
    
    /**
     * A faster method that doesn't necessarily return an itemstack associated with a player
     * 
     * @return
     */
    public ItemStack getInstanceItem() {
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
        setVisualCooldown(0);
    }
    
    /**
     * Assuming the item is already checked for matches(), instructs
     * the item to pickup the specified amount (req >= 1). Cooldowns will continue
     * if there is still space left in the stack afterwards.
     * 
     * @param amount
     * @return the amount of items that can be picked up, and 0 if unable to pickup
     */
    public int pickup(int amount) {
        int actamount = getActualAmount();
        if (actamount >= max) {
            return 0;
        }
        
        if (unavailable) {
            setVisualCooldown(0);
        }

        int toPickup = Math.min(max - actamount, amount);
        if (actamount + toPickup >= max) {
            if (cooldownTask != null) {
                cooldownTask.cancel();
            }
            curCooldown = 0;
        }
        
        return toPickup;
    }
    
    // Returns 0 if on visual cooldown
    private int getActualAmount() {
        return unavailable ? 0 : getItem().getAmount();
    }

    // Sets a visual cooldown, do 1 tick later to allow some items to be used
    // If the item is an arrow, set a cooldown for the bow/crossbow too
    public void setVisualCooldown(double c) {
        int cd = Math.max((int) (c * 20) - 1, 0);
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            player.setCooldown(item.getType(), cd);
            if (item.getType().toString().contains("ARROW")) {
                player.setCooldown(Material.BOW, cd);
            }
        }, 1);
    }
}
