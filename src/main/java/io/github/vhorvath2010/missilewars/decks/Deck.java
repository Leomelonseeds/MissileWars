package io.github.vhorvath2010.missilewars.decks;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** A class representing a generic Deck. */
public class Deck {

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

}
