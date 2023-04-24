package com.leomelonseeds.missilewars.decks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A class representing a generic Deck. */
public class Deck {

    /** The name of the Deck. */
    private String name;
    /** Items given at the start of the game. */
    private List<ItemStack> gear;
    /** Missiles given by this deck during gameplay. */
    private List<DeckItem> missiles;
    /** Utility items given by this deck during gameplay. */
    private List<DeckItem> utility;
    /** Combined for ease of use */
    private List<DeckItem> pool;

    /**
     * Generate a deck from a given set of gear, utils, and missiles.
     *
     * @param name the name of the Deck
     * @param gear the gear for the Deck
     * @param pool the utilities, missiles and items given throughout the game
     */
    public Deck(String name, List<ItemStack> gear, List<DeckItem> missiles, List<DeckItem> utility) {
        this.name = name;
        this.gear = gear;
        this.missiles = missiles;
        this.utility = utility;
        List<DeckItem> combined = new ArrayList<>(missiles);
        combined.addAll(utility);
        this.pool = combined;
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
    
    public List<ItemStack> getMissiles() {
        List<ItemStack> result = new ArrayList<>();
        for (DeckItem di : missiles) {
            result.add(di.getItem());
        }
        return result;
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
     * Get the corresponding deck item from the given item
     * 
     * @param item
     * @return deck item otherwise null if not found
     */
    public DeckItem getDeckItem(ItemStack item) {
        for (DeckItem m : pool) {
            if (m.matches(item)) {
                return m;
            }
        }
        return null;
    }
    
    /**
     * @return unmodifiable list of all deck items
     */
    public List<DeckItem> getItems() {
        return Collections.unmodifiableList(pool);
    }
}
