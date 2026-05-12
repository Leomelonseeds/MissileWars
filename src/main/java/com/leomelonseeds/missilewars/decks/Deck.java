package com.leomelonseeds.missilewars.decks;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.utilities.InventoryUtils;

/** A class representing a generic Deck. */
public class Deck {

    private DeckStorage type;
    /** Items given at the start of the game. */
    private List<ItemStack> gear;
    /** Combined for ease of use */
    private List<DeckItem> pool;
    /** For O(1) fetching, optimizing the shit out of this */
    private Map<UUID, DeckItem> poolMap;
    
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
        this.poolMap = new HashMap<>();
        pool.forEach(di -> poolMap.put(di.getID(), di));
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
        return pool.stream()
                .map(di -> di.getInstanceItem())
                .filter(i -> i.getType().toString().contains("SPAWN_EGG"))
                .toList();
    }
    
    /**
     * Get the corresponding deck item from the given item
     * 
     * @param item
     * @return deck item otherwise null if not found
     */
    public DeckItem getDeckItem(ItemStack item) {
        String uuidString = InventoryUtils.getUUIDFromItem(item);
        if (uuidString == null) {
            return null;
        }
        
        return poolMap.get(UUID.fromString(uuidString));
    }
    
    public DeckItem getMatchingDeckItem(ItemStack item) {
        for (DeckItem di : pool) {
            if (di.matches(item)) {
                return di;
            }
        }
        
        return null;
    }
    
    /**
     * Disable all deck items that match the given predicate
     * 
     * @param p
     */
    public void disableItems(Predicate<ItemStack> p) {
        pool.stream().filter(di -> p.test(di.getInstanceItem())).forEach(di -> di.setDisabled(true));
    }
    
    /**
     * Sets isDisabled to false for every deck item
     */
    public void enableAllItems() {
        pool.forEach(di -> di.setDisabled(false));
    }
    
    /**
     * @return unmodifiable list of all deck items
     */
    public List<DeckItem> getItems() {
        return Collections.unmodifiableList(pool);
    }
}
