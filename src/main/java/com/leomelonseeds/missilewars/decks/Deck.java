package com.leomelonseeds.missilewars.decks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.JSONManager;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** A class representing a generic Deck. */
public class Deck {

    /** The Random object to control random pool item distribution for all Decks. */
    private static Random rand = new Random();
    /** The name of the Deck. */
    private String name;
    /** Items given at the start of the game. */
    private List<ItemStack> gear;
    /** Missiles given by this deck during gameplay. */
    private List<ItemStack> missiles;
    /** Utility items given by this deck during gameplay. */
    private List<ItemStack> utility;
    /** Combined for ease of use */
    private List<ItemStack> pool;
    /** Make game more skill dependent */
    private Deque<ItemStack> lastTwo;
    
    private JSONManager jsonmanager;

    /**
     * Generate a deck from a given set of gear, utils, and missiles.
     *
     * @param name the name of the Deck
     * @param gear the gear for the Deck
     * @param pool the utilities, missiles and items given throughout the game
     */
    public Deck(String name, List<ItemStack> gear, List<ItemStack> missiles, List<ItemStack> utility) {
        this.name = name;
        this.gear = gear;
        this.missiles = missiles;
        this.utility = utility;
        List<ItemStack> combined = new ArrayList<>(missiles);
        combined.addAll(utility);
        this.pool = combined;
        lastTwo = new ArrayDeque<>();
        
        jsonmanager = MissileWarsPlugin.getPlugin().getJSON();
    }
    
    public String getName() {
        return name;
    }

    /**
     * Get the gear for this {@link Deck}.
     *
     * @return the gear for this {@link Deck}.
     */
    public List<ItemStack> getGear() {
        return gear;
    }

    /**
     * Give this Deck's gear to a given player.
     *
     * @param player the player to give the gear to
     */
    public void giveGear(Player player) {
        for (ItemStack gearItem : gear) {
            if (gearItem.getType().toString().contains("BOOTS")) {
                player.getInventory().setBoots(gearItem);
            } else {
                player.getInventory().addItem(gearItem);
            }
        }
    }

    /**
     * Check if a given player with this deck has inventory space for more items.
     *
     * @param player the player to check space for
     * @return true if player has inventory space
     */
    public boolean hasInventorySpace(Player player) {
        int limit = MissileWarsPlugin.getPlugin().getConfig().getInt("inventory-limit");
        int hoarder = jsonmanager.getAbility(player.getUniqueId(), "hoarder");
        limit += hoarder;

        // Count multiples of item in inventory
        for (ItemStack poolItem : pool) {
            int numOfItem = 1;
            while (player.getInventory().containsAtLeast(poolItem, 1 + (numOfItem - 1) * poolItem.getAmount())) {
                numOfItem++;
            }
            limit -= (numOfItem - 1);
        }

        // Give random item if under limit
        return limit > 0;
    }

    /**
     * Give a random item from the pool to a given player if they have space.
     *
     * @param player the player to give the pool item too
     */
    public void givePoolItem(Player player, Boolean first) {
        // Ensure Deck has pool items
        if (pool.isEmpty()) {
            return;
        }
        
        int missilespec = jsonmanager.getAbility(player.getUniqueId(), "missilespec");
        int utilityspec = jsonmanager.getAbility(player.getUniqueId(), "utilityspec");
        
        // Calculate chance based on ability
        double chance = 0.375;
        double add = 0;
        if (missilespec > 0) {
            add = -1 * (double) ConfigUtils.getItemValue("gpassive.missilespec", missilespec, "percentage");
        } else if (utilityspec > 0) {
            add = (double) ConfigUtils.getItemValue("gpassive.utilityspec", utilityspec, "percentage");
        }
        chance += add;
        List<ItemStack> toUse = rand.nextDouble() < chance ? utility : missiles;
        
        // Check on first item, global passive
        if (first) {
            if (jsonmanager.getAbility(player.getUniqueId(), "missilespec") == 3) {
                toUse = missiles;
            } else if (jsonmanager.getAbility(player.getUniqueId(), "utilityspec") == 3) {
                toUse = utility;
            }
        }
        
        // Don't give players the same item twice
        ItemStack poolItem;
        do {
            poolItem = toUse.get(rand.nextInt(toUse.size()));
        } while (lastTwo.contains(poolItem));
        // Add item to the list
        lastTwo.addFirst(poolItem);
        if (lastTwo.size() > 2) {
            lastTwo.removeLast();
        }
        
        Arena arena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(player.getUniqueId());
        double toohigh = ConfigUtils.getMapNumber(arena.getMapType(), arena.getMapName(), "too-high");
        double toofar = ConfigUtils.getMapNumber(arena.getMapType(), arena.getMapName(), "too-far");
        Location loc = player.getLocation();
        
        // Don't give item if they are out of bounds
        if (loc.getBlockY() > toohigh || loc.getBlockX() < toofar) {
            refuseItem(player, poolItem, "messages.out-of-bounds");
            return;
        }
        
        // Don't give item if their inventory space is full
        if (!hasInventorySpace(player)) {
            refuseItem(player, poolItem, "messages.inventory-limit");
            return;
        }
        
        player.getInventory().addItem(poolItem);
    }
    
    /**
     * Refuse to give player an item, sending a message.
     * 
     * @param player
     * @param poolItem
     * @param messagePath
     */
    private void refuseItem(Player player, ItemStack poolItem, String messagePath) {
        String message = ConfigUtils.getConfigText(messagePath, player, null, null);
        String name;
        if (poolItem.getItemMeta().hasDisplayName()) {
            name = PlainTextComponentSerializer.plainText().serialize(poolItem.getItemMeta().displayName());
        } else {
            name = poolItem.getAmount() + "x " + StringUtils.capitalize(poolItem.getType().toString().toLowerCase());
        }
        player.sendMessage(message.replaceAll("%umw_item%", name));
    }
}
