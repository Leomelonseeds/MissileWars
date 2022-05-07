package io.github.vhorvath2010.missilewars.decks;

import java.util.List;
import java.util.Random;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;

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
        List<ItemStack> combined = ListUtils.union(missiles, utility);
        this.pool = combined;
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
    public void givePoolItem(Player player) {
        // Ensure Deck has pool items
        if (pool.isEmpty()) {
            return;
        }
        double chance = 0.375;
        List<ItemStack> toUse = rand.nextDouble() < chance ? utility : missiles;
        ItemStack poolItem = toUse.get(rand.nextInt(toUse.size()));
        if (hasInventorySpace(player)) {
            player.getInventory().addItem(poolItem);
        } else {
            String message = ConfigUtils.getConfigText("messages.inventory-limit", player, null, null);
            String name;
            if (poolItem.getItemMeta().hasDisplayName()) {
                name = poolItem.getItemMeta().getDisplayName();
            } else {
                name = poolItem.getAmount() + "x " + StringUtils.capitalize(poolItem.getType().toString().toLowerCase());
            }
            player.sendMessage(message.replaceAll("%umw_item%", name));
        }
    }

}
