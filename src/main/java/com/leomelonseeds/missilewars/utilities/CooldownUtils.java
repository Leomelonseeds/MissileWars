package com.leomelonseeds.missilewars.utilities;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Util class for setting cooldowns that consider custom cooldown ids.
 * Currently only useful for Gunslinger passive, but may be used more
 * in the future...
 */
public class CooldownUtils {
    
    public static boolean hasCooldown(Player player, ItemStack item) {
        if (item == null) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasUseCooldown()) {
            return player.getCooldown(meta.getUseCooldown().getCooldownGroup()) > 0;
        } else {
            return player.hasCooldown(item.getType());
        }
    }
    
    /**
     * Sets a cooldown for an item. If the item has a custom cooldown, it will set the
     * cooldown for that key. Otherwise, it will use the material to set cooldown.
     * 
     * @param player
     * @param item
     * @param ticks
     */
    public static void setCooldown(Player player, ItemStack item, int ticks) {
        if (item == null) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasUseCooldown()) {
            player.setCooldown(meta.getUseCooldown().getCooldownGroup(), ticks);
        } else {
            player.setCooldown(item.getType(), ticks);
        }
    }
    
    /**
     * Sets cooldown for all items matching the specified material using
     * {@link CooldownUtils#setCooldown(Player, ItemStack, int)}
     * 
     * @param player
     * @param material
     * @param ticks
     */
    public static void setCooldown(Player player, Material material, int ticks) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                setCooldown(player, item, ticks);
            }
        }
    }
    
    /**
     * Remove cooldowns from every item a player has
     * 
     * @param player
     */
    public static void removeCooldowns(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            setCooldown(player, item, 0);
        }
    }
    
    /**
     * Updates cooldowns for crossbow
     * 
     * @param player
     */
    public static void updateCrossbowCooldowns(Player player) {
        //ConfigUtils.schedule(1, () -> {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || item.getType() != Material.CROSSBOW) {
                    continue;
                }
                
                if (hasCooldown(player, item)) {
                    continue;
                }
                
                CrossbowMeta cmeta = (CrossbowMeta) item.getItemMeta();
                int cooldown = cmeta.hasChargedProjectiles() ? 0 : 
                    Math.max(player.getCooldown(Material.ARROW), player.getCooldown(Material.TIPPED_ARROW));
                if (cmeta.hasUseCooldown()) {
                    player.setCooldown(cmeta.getUseCooldown().getCooldownGroup(), cooldown);
                } else {
                    player.setCooldown(item, cooldown);
                }
            }
        //});
    }

}
