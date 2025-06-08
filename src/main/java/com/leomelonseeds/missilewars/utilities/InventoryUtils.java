package com.leomelonseeds.missilewars.utilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.decks.DeckItem;

import io.github.a5h73y.parkour.Parkour;

/** Utility class mw inventory management */
public class InventoryUtils {
    
    /**
     * clearInventory while clearCustom is false, keeping custom items
     * 
     * @param player
     */
    public static void clearInventory(Player player) {
        clearInventory(player, false);
    }
    
    /**
     * @param i
     * @return if the item provided is not null and is a potion or milkbucket
     */
    public static boolean isPotion(ItemStack i) {
        return i != null && (i.getType() == Material.POTION || i.getType() == Material.MILK_BUCKET);
    }

    /**
     * Clears everything except for helmet of player
     * and alcoholic beverages
     *
     * @param player
     * @param clearCustom whether custom items should be cleared
     */
    public static void clearInventory(Player player, boolean clearCustom) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack current = inventory.getItem(i);
            if (current == null) {
                continue;
            }
            
            // Don't clear custom items if clearCustom false
            if (!clearCustom && isHeldItem(current)) {
                continue;
            }
            
            // If it's not an illegal item, don't clear
            if (i == 39) {
                String cur = current.getType().toString();
                if (!cur.contains("HELMET") && !cur.contains("CREEPER") && !cur.contains("DRAGON")) {
                    continue;
                }
            }
            
            // Don't clear alcohol (potions)
            if (isPotion(current)) {
                continue;
            }   
            
            inventory.clear(i);
        }
    }
    
    public static boolean isHeldItem(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has
                (new NamespacedKey(MissileWarsPlugin.getPlugin(), "held"), PersistentDataType.STRING);
    }

    /**
     * Saves a player's inventory to database.
     * Doesn't save potions to prevent duping.
     *
     * @param player
     */
    public static void saveInventory(Player player, Boolean async) {
        if (Parkour.getInstance().getParkourSessionManager().isPlayingParkourCourse(player)) {
            MissileWarsPlugin.getPlugin().log("Not saving player inventory since they are on a parkour");
            return;
        }
        Inventory inventory = player.getInventory();
        UUID uuid = player.getUniqueId();
        try {
            ByteArrayOutputStream str = new ByteArrayOutputStream();
            BukkitObjectOutputStream data = new BukkitObjectOutputStream(str);

            data.writeInt(inventory.getSize());
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack current = inventory.getItem(i);
                if (!isPotion(current)) {
                    data.writeObject(inventory.getItem(i));
                } else {
                    data.writeObject(null);
                }
            }
            String inventoryData = Base64.getEncoder().encodeToString(str.toByteArray());
            MissileWarsPlugin.getPlugin().getSQL().setInventory(uuid, inventoryData, async);
        } catch (final IOException e) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to save inventory to string of " + player.getName());
        }
    }
    
    
    /** 
     * Give a player an item by dropping it on the ground, setting
     * the player as the owner, and setting pickup delay to 0
     * 
     * @param player
     * @param item
     */
    public static void regiveItem(Player player, ItemStack item) {
        Item newitem = player.getWorld().dropItem(player.getLocation(), item);
        newitem.setOwner(player.getUniqueId());
        newitem.setPickupDelay(0);
        
        // If the player is falling into the void, the item may not get picked up.
        // Teleport the item back to player, slightly underneath them to make sure.
        ConfigUtils.schedule(1, () -> {
            if (!newitem.isDead()) {
                newitem.teleport(player.getLocation().add(0, -1, 0));
            }
        });
    }


    /**
     * Loads player inventory from database (no helmet)
     * Ignores potions if an item already exists
     * in that slot
     *
     * @param player
     */
    public static void loadInventory(Player player) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Inventory inventory = player.getInventory();
        UUID uuid = player.getUniqueId();
        plugin.getSQL().getInventory(uuid, result -> {
            try {
                String encodedString = (String) result;
                if (encodedString == null) {
                    return;
                }
                ByteArrayInputStream stream = new ByteArrayInputStream(Base64.getDecoder().decode(encodedString));
                BukkitObjectInputStream data = new BukkitObjectInputStream(stream);
                int invSize = data.readInt();
                for (int i = 0; i < invSize; i++) {
                    ItemStack invItem = (ItemStack) data.readObject();
                    boolean empty = invItem == null;
                    ItemStack current = inventory.getItem(i);
                    if (!(i == 39 || (isPotion(current) && empty))) {
                        inventory.setItem(i, invItem);
                    }
                }
                
                // Add elytra if ranked
                if (player.hasPermission("umw.elytra")) {
                    if (!inventory.contains(Material.ELYTRA)) {
                        ItemStack elytra = plugin.getDeckManager().createItem("elytra", 0, false);
                        ItemMeta meta = elytra.getItemMeta();
                        meta.setUnbreakable(true);
                        elytra.setItemMeta(meta);
                        inventory.setItem(38, elytra);
                    }
                }
                
                // Add menu item
                ItemStack menu = plugin.getDeckManager().createItem("held.main-menu", 0, false);
                ItemMeta meta = menu.getItemMeta();
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "held"),
                        PersistentDataType.STRING, "main-menu");
                menu.setItemMeta(meta);
                inventory.setItem(4, menu);
            } catch (final Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "Failed to read inventory string of " + player.getName());
            }
        });
    }
    
    /**
     * Tell plugin that an item was consumed
     * 
     * @param player
     * @param arena
     * @param item
     * @param slot -1 if should be manually depleted, provide slot otherwise
     */
    public static void consumeItem(Player player, Arena arena, ItemStack item, int slot) {
        consumeItem(player, arena, item, slot, false);
    }
    
    /**
     * Tell plugin that an item was consumed, specifically for bow shots
     * 
     * @param player
     * @param arena
     * @param item
     * @param slot -1 if should be manually depleted, provide slot otherwise
     * @param isBowShot set to true to indicate that the item amount should 
     * be returned to the amount before the bow was fired
     */
    public static void consumeItem(Player player, Arena arena, ItemStack item, int slot, boolean isBowShot) {
        MissileWarsPlayer mwp = arena.getPlayerInArena(player.getUniqueId());
        if (mwp == null) {
            return;
        }
        
        Deck deck = mwp.getDeck();
        if (deck == null) {
            return;
        }

        DeckItem di = deck.getDeckItem(item);
        int amt = isBowShot ? item.getAmount() + 1: item.getAmount();
        boolean deplete = slot == -1;
        
        // Add cooldown to offhand item if item is manually depleted, to prevent offhand items
        // from being used in the same tick without warning
        PlayerInventory pinv = player.getInventory();
        if (deplete && pinv.getItem(EquipmentSlot.HAND).equals(item)) {
            int cooldown = MissileWarsPlugin.getPlugin().getConfig().getInt("experimental.missile-cooldown");
            Material off = pinv.getItem(EquipmentSlot.OFF_HAND).getType();
            if (off != Material.AIR && !player.hasCooldown(off)) {
                player.setCooldown(off, cooldown);
            }
        }
        
        if (di == null) {
            if (deplete) {
                item.setAmount(item.getAmount() - 1);
            }
            return;
        }
        
        boolean makeUnavailable = false;
        if (amt == 1) {
            makeUnavailable = true;
            if (!deplete) {
                Bukkit.getScheduler().runTask(MissileWarsPlugin.getPlugin(), () -> {
                    item.setAmount(1);
                    pinv.setItem(slot, item);
                    if (item.getType() == Material.ENDER_PEARL) {
                        di.setVisualCooldown(di.getCurrentCooldown()); 
                    }
                });
            }
        } else if (deplete) {
            item.setAmount(amt - 1);
        }
        
        di.consume(makeUnavailable);
    }
    
    /**
     * Reset the player's visual cooldowns for all items
     * 
     * @param player
     */
    public static void resetCooldowns(Player player) {
        for (ItemStack i : player.getInventory().getContents()) {
            if (i == null) continue;
            player.setCooldown(i.getType(), 0);
        }
    }

    /**
     * Get string data from custom item
     * 
     * @param item
     * @param id
     * @return
     */
    public static String getStringFromItem(ItemStack item, String id) {
        if (item == null) {
            return null;
        }
        
        if ((item.getItemMeta() == null) || !item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), id),
                PersistentDataType.STRING)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(MissileWarsPlugin.getPlugin(),
                id), PersistentDataType.STRING);
    }
}
