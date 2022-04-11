package io.github.vhorvath2010.missilewars.utilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;

/** Utility class mw inventory management */
public class InventoryUtils {
    
    /**
     * Clears everything except for helmet of player
     * and alcoholic beverages
     * 
     * @param player
     */
    public static void clearInventory(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack current = inventory.getItem(i);
            boolean isPotion = current != null && current.getType() == Material.POTION ? true : false;
            if (!(i == 39 || isPotion)) {
                inventory.clear(i);
            }
        }
    }
    
    /**
     * Saves a player's inventory to file.
     * Doesn't save potions to prevent duping.
     * 
     * @param player
     */
    public static void saveInventory(Player player) {
        Inventory inventory = player.getInventory();
        String uuid = player.getUniqueId().toString();
        try {
            ByteArrayOutputStream str = new ByteArrayOutputStream();
            BukkitObjectOutputStream data = new BukkitObjectOutputStream(str);
        
            data.writeInt(inventory.getSize());
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack current = inventory.getItem(i);
                boolean isPotion = current != null && current.getType() == Material.POTION ? true : false;
                if (!isPotion) {
                    data.writeObject(inventory.getItem(i));
                } else {
                    data.writeObject(null);
                }
            }
            String inventoryData = Base64.getEncoder().encodeToString(str.toByteArray());
            FileConfiguration inventoryConfig = getInventoryConfig();
            inventoryConfig.set(uuid, inventoryData);
            inventoryConfig.save(new File(MissileWarsPlugin.getPlugin().getDataFolder(), "inventories.yml"));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
    
    
    /**
     * Loads player inventory from file (no helmet)
     * Ignores potions if an item already exists
     * in that slot
     * 
     * @param player
     */
    public static void loadInventory(Player player) {
        Inventory inventory = player.getInventory();
        String uuid = player.getUniqueId().toString();
        String encodedString = getInventoryConfig().getString(uuid);
        if (encodedString == null) {
            return;
        }
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream(Base64.getDecoder().decode(encodedString)); 
            BukkitObjectInputStream data = new BukkitObjectInputStream(stream);
            int invSize = data.readInt();
            for (int i = 0; i < invSize; i++) {
                ItemStack invItem = (ItemStack) data.readObject();
                Boolean empty = invItem == null;
                ItemStack current = inventory.getItem(i);
                boolean isPotion = current != null && current.getType() == Material.POTION ? true : false;
                if (!(i == 39 || (isPotion && empty))) {
                    inventory.setItem(i, invItem);
                }
            }
        } catch (final IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    
    /**
     * Gets the inventory configuration file. Creates one if not exist.
     * 
     * @return The inventory configuration
     */
    public static FileConfiguration getInventoryConfig() {
        File inventoryFile = new File(MissileWarsPlugin.getPlugin().getDataFolder(), "inventories.yml");
        
        if (!inventoryFile.exists()) {
            try {
                inventoryFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        return YamlConfiguration.loadConfiguration(inventoryFile);
    }

}
