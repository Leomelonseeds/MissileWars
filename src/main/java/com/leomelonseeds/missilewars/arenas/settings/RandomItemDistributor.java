package com.leomelonseeds.missilewars.arenas.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

public class RandomItemDistributor implements ConfigurationSerializable {
    
    private final int DEFAULT_TIMER = 12;
    
    private int timer;
    private boolean isBagDistribution;
    private boolean disableXpTimer;
    private List<RandomItem> items;
    private List<RandomItem> curItems;
    private int totalWeight;
    
    public RandomItemDistributor() {
        this.timer = DEFAULT_TIMER;
        this.items = new ArrayList<>();
        this.curItems = new ArrayList<>();
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> distributor = new HashMap<>();
        distributor.put("timer", timer);
        distributor.put("is-bag-distribution", isBagDistribution);
        distributor.put("disable-xp-timer", disableXpTimer);
        distributor.put("items", items);
        return distributor;
    }
    
    @SuppressWarnings("unchecked")
    public RandomItemDistributor(Map<String, Object> distributor) {
        this.timer = (int) distributor.get("timer");
        this.isBagDistribution = (boolean) distributor.get("is-bag-distribution");
        this.disableXpTimer = (boolean) distributor.get("disableXpTimer");
        this.items = (List<RandomItem>) distributor.get("items");
        this.curItems = new ArrayList<>();
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
        
        if (isBagDistribution) {
            RandomItem toGive = curItems.remove(i);
            totalWeight -= toGive.getWeight();
            return toGive;
        }
            
        return curItems.get(i);
    }

    public int getTimer() {
        return timer;
    }

    public void setTimer(int timer) {
        this.timer = timer;
    }

    public void setBagDistribution(boolean isBagDistribution) {
        this.isBagDistribution = isBagDistribution;
    }

    public boolean isDisableXpTimer() {
        return disableXpTimer;
    }

    public void setDisableXpTimer(boolean disableXpTimer) {
        this.disableXpTimer = disableXpTimer;
    }
    
    public void addItem(RandomItem item) {
        this.items.add(item);
    }
    
    public void removeItem(RandomItem item) {
        this.items.remove(item);
    }
    
    public void clearItems() {
        this.items.clear();
    }
}
