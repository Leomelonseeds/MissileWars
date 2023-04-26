package com.leomelonseeds.missilewars.decks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
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
            setVisualCooldown(curCooldown > 0 ? curCooldown : cooldown);
        }
        
        if (curCooldown == 0) {
            curCooldown = cooldown;
            updateItem();
        }
    }
    
    // Item update task, handles giving items
    public void updateItem() {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        cooldownTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (curCooldown - 0.5 <= 0) {
                int amt = getActualAmount();
                curCooldown = 0;
                if (amt >= max) {
                    return;
                }
                
                getItem().setAmount(++amt);
                setVisualCooldown(0);
                unavailable = false;
                player.updateInventory();
                
                if (amt < max) {
                    curCooldown = cooldown;
                    updateItem();
                }
            } else if (ConfigUtils.outOfBounds(player, plugin.getArenaManager().getArena(player.getUniqueId()))) {
                player.sendActionBar(ConfigUtils.toComponent(ConfigUtils.getConfigText("messages.out-of-bounds", player, null, null)));
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
        if (c < 0) {
            c = 0;
        }
        
        if (curCooldown > 0) {
            setVisualCooldown(c);
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
     * Stops the item from running. The item can be stored away
     * and re-used at a later state by calling updateItem()
     */
    public void stop() {
        if (cooldownTask != null) {
            cooldownTask.cancel();
        }
    }
    
    /**
     * Assuming the item is already checked for matches(), instructs
     * the item to pickup the specified item entity. Cooldowns will continue
     * if there is still space left in the stack afterwards.
     * 
     * @param amount
     * @return if the item pickup was successful
     */
    public boolean pickup(Item itemEntity) {
        int actamount = getActualAmount();
        if (actamount >= max) {
            return false;
        }

        ItemStack item = itemEntity.getItemStack();
        int amount = item.getAmount();
        int toPickup = Math.min(max - actamount, amount);
        if (amount > toPickup) {
            ItemStack drop = new ItemStack(item);
            drop.setAmount(amount - toPickup);
            player.getWorld().dropItem(itemEntity.getLocation(), drop);
        }
        
        ItemStack pick = new ItemStack(item);
        pick.setAmount(toPickup);
        itemEntity.setItemStack(pick);
        
        if (actamount + toPickup >= max) {
            if (cooldownTask != null) {
                cooldownTask.cancel();
            }
            curCooldown = 0;
        }
        
        if (unavailable) {
            setVisualCooldown(0);
            ItemStack cur = getItem();
            cur.setAmount(cur.getAmount() - 1);
        }
        return true;
    }
    
    // Returns 0 if on visual cooldown
    private int getActualAmount() {
        return unavailable ? 0 : getItem().getAmount();
    }

    // Sets a visual cooldown, do 1 tick later to allow some items to be used
    // If the item is an arrow, set a cooldown for the bow/crossbow too
    // Also sets unavailable to true
    public void setVisualCooldown(double c) {
        int cd = Math.max((int) (c * 20) - 1, 0);
        unavailable = c != 0;
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            player.setCooldown(item.getType(), cd);
            
            // Due to the way crossbow loading and bow firing are handled,
            // setting the item cooldowns for them differs slightly
            if (item.getType().toString().contains("ARROW")) {
                player.setCooldown(Material.BOW, cd);
                if (cd == 0) {
                    player.setCooldown(Material.CROSSBOW, 0);
                }
            }
        }, 1);
    }
}
