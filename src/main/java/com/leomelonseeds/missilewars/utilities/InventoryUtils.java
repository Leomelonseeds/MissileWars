package com.leomelonseeds.missilewars.utilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import com.leomelonseeds.missilewars.MissileWarsPlugin;

import io.github.a5h73y.parkour.Parkour;

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
                boolean isPotion = current != null && current.getType() == Material.POTION ? true : false;
                if (!isPotion) {
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
     * Loads player inventory from database (no helmet)
     * Ignores potions if an item already exists
     * in that slot
     *
     * @param player
     */
    public static void loadInventory(Player player) {
        Inventory inventory = player.getInventory();
        UUID uuid = player.getUniqueId();
        MissileWarsPlugin.getPlugin().getSQL().getInventory(uuid, result -> {
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
                    boolean isPotion = current != null && current.getType() == Material.POTION ? true : false;
                    if (!(i == 39 || i == 4 || (isPotion && empty))) {
                        inventory.setItem(i, invItem);
                    }
                }
                // Add elytra if ranked
                if (player.hasPermission("umw.elytra")) {
                    if (!inventory.contains(Material.ELYTRA)) {
                        ItemStack elytra = MissileWarsPlugin.getPlugin().getDeckManager().createItem("elytra", 0, false);
                        ItemMeta meta = elytra.getItemMeta();
                        meta.setUnbreakable(true);
                        elytra.setItemMeta(meta);
                        inventory.setItem(38, elytra);
                    }
                }
            } catch (final Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "Failed to read inventory string of " + player.getName());
            }
        });
    }
}
