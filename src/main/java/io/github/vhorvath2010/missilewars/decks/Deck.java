package io.github.vhorvath2010.missilewars.decks;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

/** A class representing a generic Deck. */
public class Deck {

    /** The Random object to control random pool item distribution for all Decks. */
    private static Random rand = new Random();
    /** The name of the Deck. */
    private String name;
    /** Items given at the start of the game. */
    private List<ItemStack> gear;
    /** Utility items and Missiles given by this deck during gameplay. */
    private List<ItemStack> pool;

    /**
     * Generate a deck from a given set of gear, utils, and missiles.
     *
     * @param name the name of the Deck
     * @param gear the gear for the Deck
     * @param pool the utilities, missiles and items given throughout the game
     */
    public Deck(String name, List<ItemStack> gear, List<ItemStack> pool) {
        this.name = name;
        this.gear = gear;
        this.pool = pool;
    }

    /**
     * Give this Deck's gear to a given player.
     *
     * @param player the player to give the gear to
     */
    public void giveGear(Player player) {
        for (ItemStack gearItem : gear) {
            player.getInventory().addItem(gearItem);
        }
    }

    /**
     * Give a random item from the pool to a given player if they have space.
     *
     * @param player the player to give the pool item too
     */
    public void givePoolItem(Player player) {
        // Ensure Deck has pool items
        if (pool.isEmpty()) {
            return;
        }

        // Ensure they have inventory space
        int limit = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "default-settings.yml").getInt("inventory-limit");

        // Count multiples of item in inventory
        for (ItemStack poolItem : pool) {
            int numOfItem = 1;
            while (player.getInventory().containsAtLeast(poolItem, numOfItem * poolItem.getAmount())) {
                numOfItem++;
            }
            limit -= (numOfItem - 1);
        }

        // Give random item if under limit
        if (limit > 0) {
            player.getInventory().addItem(pool.get(rand.nextInt(pool.size())));
        }
    }

}
