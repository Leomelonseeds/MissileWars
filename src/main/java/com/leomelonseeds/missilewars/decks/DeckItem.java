package com.leomelonseeds.missilewars.decks;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.teams.MissileWarsTeam;

public class DeckItem {
    
    private ItemStack item;
    private int cooldown; // The actual cooldown
    private int max;
    private int curCooldown;
    private MissileWarsPlayer mwp;
    BukkitTask cooldownTask;
    boolean unavailable;
    MissileWarsTeam team; // Where to fetch the cooldown multiplier
    
    /**
     * @param item should correspond directly the player inventory's item (but its fine if it doesn't I guess)
     * @param cooldown is in seconds
     * @param max
     */
    public DeckItem(ItemStack item, int cooldown, int max, MissileWarsPlayer mwp) {
        this.item = item;
        this.cooldown = cooldown;
        this.max = max;
        this.curCooldown = 0;
        this.mwp = mwp;
        this.unavailable = false;
    }
    
    public void registerTeam(MissileWarsTeam team) {
        this.team = team;
    }
    
    /**
     * Checks if given itemstack is the deckitem
     * 
     * @param i
     * @return
     */
    public boolean matches(ItemStack i) {
        if (i == null) {
            return false;
        }
        
        ItemStack toMatch = i.clone();
        toMatch.removeEnchantment(Enchantment.DURABILITY);
        return item.isSimilar(toMatch);
    }
    
    /**
     * Invoke after item is used or dropped, check matches()
     */
    public void consume(boolean makeUnavailable) {
        if (makeUnavailable) {
            setVisualCooldown(curCooldown > 0 ? curCooldown : getCooldown());
        }
        
        if (curCooldown == 0) {
            curCooldown = getCooldown();
            updateItem();
        }
    }
    
    // Item update task, handles giving items
    public void updateItem() {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        cooldownTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (curCooldown - 1 <= 0) {
                curCooldown = 0;
                int amt = getActualAmount();
                if (amt >= max) {
                    return;
                }
                
                getItem().setAmount(++amt);
                if (unavailable) {
                    setVisualCooldown(0);
                }
                
                if (amt < max) {
                    curCooldown = getCooldown();
                    updateItem();
                }
            } else if (mwp.outOfBounds()) {
                if (unavailable) {
                    setVisualCooldown(curCooldown);
                }
                updateItem();
            } else {
                curCooldown--;
                updateItem(); 
            }
        }, 20L);
    }
    
    /**
     * Accurate to the second
     * 
     * @return
     */
    public int getCurrentCooldown() {
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
     * Initializes the cooldown of an item, use for game starts/item drops.
     * Adds visual cooldown of c. If there is existing cooldown
     * no cooldown will be added. Otherwise if c == 0 then max cooldown
     * will be applied, since this method only called after dropping the
     * last item.
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
            c = getCooldown();
        }

        setVisualCooldown(c);
        curCooldown = c;
        updateItem();
    }
    
    public int getInitCooldown() {
        return cooldown;
    }
    
    public int getCooldown() {
        return (int) (cooldown * team.getMultiplier() / (team.isChaos() ? 2 : 1));
    }
     
    public int getMax() {
        return max;
    }
    
    /**
     * @return a modifiable ItemStack object that represents the item
     * in the player's inventory. Should not return null.
     */
    public ItemStack getItem() {
        return getItem(true);
    }
    
    public ItemStack getItem(boolean reAdd) {
        Player player = mwp.getMCPlayer();
        for (ItemStack i : player.getInventory().getContents()) {
            if (matches(i)) {
                return i;
            }
        }
        
        if (reAdd) {
            player.getInventory().addItem(item);
            Bukkit.getLogger().log(Level.WARNING, "A player is missing an item " + item.getType() + ", so it was re-added to their inventory.");
            return getItem(true);
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

        Player player = mwp.getMCPlayer();
        if (player != null && player.isOnline()) {
            setVisualCooldown(0);
        }
    }
    
    /**
     * Assuming the item is already checked for matches(), instructs
     * the item to pickup the specified item entity. Cooldowns will continue
     * if there is still space left in the stack afterwards.
     * 
     * @param amount
     */
    public void pickup(Item itemEntity) {
        Player player = mwp.getMCPlayer();
        int actamount = getActualAmount();
        if (actamount >= max) {
            return;
        }

        ItemStack item = itemEntity.getItemStack();
        int amount = item.getAmount();
        int toPickup = Math.min(max - actamount, amount);
        if (amount > toPickup) {
            ItemStack drop = new ItemStack(item);
            drop.setAmount(amount - toPickup);
            player.getWorld().dropItem(itemEntity.getLocation(), drop);
        }
        
        if (actamount + toPickup >= max) {
            if (cooldownTask != null) {
                cooldownTask.cancel();
            }
            curCooldown = 0;
        }
        
        if (unavailable) {
            setVisualCooldown(0);
        }
        
        ItemStack cur = getItem();
        player.playPickupItemAnimation(itemEntity);
        cur.setAmount(actamount + toPickup);
        itemEntity.remove();
    }
    
    // Returns 0 if on visual cooldown
    public int getActualAmount() {
        return unavailable ? 0 : getItem().getAmount();
    }

    // Sets a visual cooldown
    // If the item is an arrow, set a cooldown for the bow/crossbow too
    // Also sets unavailable to true
    // No workie if player in creative mode
    public void setVisualCooldown(int c) {
        Player player = mwp.getMCPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        
        int cd = c * 20;
        unavailable = cd != 0;
        player.setCooldown(item.getType(), cd);
        
        // Give item enchantment if 0 reached
        ItemStack actual = getItem(false);
        if (actual != null) {
            if (c == 0) {
                actual.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
            } else {
                actual.removeEnchantment(Enchantment.DURABILITY);
            } 
        }
            
        // Due to the way crossbow loading and bow firing are handled,
        // setting the item cooldowns for them differs slightly
        if (item.getType().toString().contains("ARROW")) {
            player.setCooldown(Material.BOW, cd);
            if (cd == 0) {
                player.setCooldown(Material.CROSSBOW, 0);
            }
        }
        
    }
}
