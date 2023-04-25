package com.leomelonseeds.missilewars.decks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class DeckItem {
    
    private ItemStack item;
    private int cooldown; // in seconds
    private int max;
    private int curCooldown;
    private Player player;
    BukkitTask cooldownTask;
    boolean unavailable;
    
    /**
     * @param item should correspond directly the player inventory's item
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
    public void consume() {
        if (item.getAmount() == 0) {
            item.setAmount(1);
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
            if (ConfigUtils.outOfBounds(player, plugin.getArenaManager().getArena(player.getUniqueId()))) {
                curCooldown = cooldown;
                if (unavailable) {
                    setVisualCooldown(cooldown);
                }
            } else if (curCooldown == 0) {
                int amt = getActualAmount();
                if (amt >= max) {
                    return;
                }
                
                item.setAmount(++amt);
                unavailable = false;
                
                if (amt < max) {
                    curCooldown = cooldown;
                    updateItem();
                }
            } else {
                curCooldown--;
                updateItem(); 
            }
        }, 20L);
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
     * Set the cooldown of an item, use for game starts.
     * Aadds visual cooldown of c. If 0 then no cooldown added
     */
    public void initCooldown(int c) {
        if (c == 0) {
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
        return unavailable ? 0 : item.getAmount();
    }

    // Sets a visual cooldown
    private void setVisualCooldown(int c) {
        player.setCooldown(item.getType(), c * 20);
    }
}
