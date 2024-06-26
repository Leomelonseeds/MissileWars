package com.leomelonseeds.missilewars.decks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.inventory.ItemStack;

/** A class representing a generic Deck. */
public class Deck {

    private DeckStorage type;
    /** Items given at the start of the game. */
    private List<ItemStack> gear;
    /** Combined for ease of use */
    private List<DeckItem> pool;
    /**
     * Generate a deck from a given set of gear, utils, and missiles.
     *
     * @param name the name of the Deck
     * @param gear the gear for the Deck
     * @param pool the utilities, missiles and items given throughout the game
     */
    public Deck(DeckStorage type, List<ItemStack> gear, List<DeckItem> pool) {
        this.type = type;
        this.gear = gear;
        this.pool = pool;
    }
    
    public DeckStorage getType() {
        return type;
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
        for (DeckItem di : pool) {
            ItemStack i = di.getInstanceItem();
            if (i.getType().toString().contains("SPAWN_EGG")) {
                result.add(i);
            }
        }
        return result;
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
