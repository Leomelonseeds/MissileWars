package com.leomelonseeds.missilewars.arenas.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;

public class RandomItemDistributor implements ConfigurationSerializable {
    
    // Unfortunately we do need to reference these settings
    private ArenaSettings settings;
    private Map<UUID, RandomItem> itemMap;
    private List<RandomItem> items;
    private List<RandomItem> curItems;
    private int totalWeight;
    
    public RandomItemDistributor(ArenaSettings settings) {
        this.settings = settings;
        this.items = new ArrayList<>();
        this.curItems = new ArrayList<>();
        this.itemMap = new HashMap<>();
    }
    
    public RandomItemDistributor(RandomItemDistributor other, ArenaSettings settings) {
        this.settings = settings;
        this.curItems = new ArrayList<>();
        this.items = new ArrayList<>();
        other.items.forEach(ri -> this.items.add(new RandomItem(ri)));
    }
 
    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> distributor = new HashMap<>();
        distributor.put("items", items);
        return distributor;
    }
    
    @SuppressWarnings("unchecked")
    public RandomItemDistributor(Map<String, Object> distributor) {
        this.items = (List<RandomItem>) distributor.get("items");
        this.curItems = new ArrayList<>();
        items.forEach(ri -> itemMap.put(ri.getID(), ri));
    }
    
    /**
     * Gets next item in the random item distributor
     * 
     * @return
     */
    public RandomItem getNextItem() {
        // Just in case
        if (items.isEmpty()) {
            return null;
        }
        
        if (curItems.isEmpty()) {
            curItems.addAll(items);
            totalWeight = curItems.stream().mapToInt(ri -> ri.getWeight()).sum();
        }
        
        // Thanks https://stackoverflow.com/questions/6737283/weighted-randomness-in-java
        int i = 0;
        for (double r = Math.random() * totalWeight; i < curItems.size() - 1; ++i) {
            r -= curItems.get(i).getWeight();
            if (r <= 0.0) {
                break;
            }
        }
        
        if ((boolean) settings.get(ArenaSetting.RANDOM_ITEM_BAG_DISTRIBUTION)) {
            RandomItem toGive = curItems.remove(i);
            totalWeight -= toGive.getWeight();
            return toGive;
        }
            
        return curItems.get(i);
    }
    
    public void giveNextItem(List<MissileWarsPlayer> players) {
        RandomItem nextItem = getNextItem();
        ItemStack toGive = nextItem.getItem();
        ItemMeta meta = toGive.getItemMeta();
        for (MissileWarsPlayer mwp : players) {
            PlayerInventory inv = mwp.getMCPlayer().getInventory();
        }
    }
    
    public void addItem(RandomItem item) {
        this.items.add(item);
        this.itemMap.put(item.getID(), item);
    }
    
    public void removeItem(RandomItem item) {
        this.items.remove(item);
        this.itemMap.remove(item.getID());
    }
    
    public void clearItems() {
        this.items.clear();
        this.itemMap.clear();
    }
    
    public void setArenaSettings(ArenaSettings settings) {
        this.settings = settings;
    }
}
