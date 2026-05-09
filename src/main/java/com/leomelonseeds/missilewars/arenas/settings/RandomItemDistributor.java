package com.leomelonseeds.missilewars.arenas.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class RandomItemDistributor implements ConfigurationSerializable {
    
    // Unfortunately we do need to reference these settings
    private ArenaSettings settings;
    private Map<UUID, RandomItem> itemMap;
    private List<RandomItem> items;
    private List<RandomItem> curItems;
    private int totalWeight;
    private int timerTicks; // IN TICKS!!! 0 means disabled
    
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
     * Starts the random item distribution according to the given timer.
     * The first item is given immediately.
     * 
     * @param redTeam A live view of the red team players
     * @param blueTeam A live view of the blue team players
     */
    public void startDistribution(Set<MissileWarsPlayer> redTeam, Set<MissileWarsPlayer> blueTeam) {
        timerTicks = ((int) settings.get(ArenaSetting.RANDOM_ITEM_DISTRIBUTION_TIMER)) * 20;
        giveNextItem(redTeam, blueTeam);
    }
    
    /**
     * Give the next item to both teams. If team balancing is enabled, and
     * the ratio of one team to another is greater than or equal to 3:2, the
     * smaller team will receive floor(g / l) items per player, and the
     * remaining g % l items will be randomly distributed to g % l players.
     * 
     * 
     * @param redTeam
     * @param blueTeam
     */
    private void giveNextItem(Set<MissileWarsPlayer> redTeam, Set<MissileWarsPlayer> blueTeam) {
        // If timer is 0, stop the distributor
        if (timerTicks == 0) {
            return;
        }
        
        // Make sure we can actually give the next item
        RandomItem nextItem = getNextItem();
        if (nextItem == null) {
            Bukkit.getLogger().warning("Stopping random item distributor because "
                    + "couldn't find next item for arena owned by " + settings.get(ArenaSetting.OWNER_NAME));
            return;
        }
        
        // Figure out team balancing
        int globalLimit = (int) settings.get(ArenaSetting.RANDOM_ITEM_INVENTORY_LIMIT);
        boolean uneven = false;
        do {
            if (!((boolean) settings.get(ArenaSetting.ENABLE_TEAM_BALANCING))) {
                break;
            }

            // Figure out if larger team size / smaller team size >= 3/2
            boolean redLarger = redTeam.size() > blueTeam.size();
            Set<MissileWarsPlayer> less = redLarger ? blueTeam : redTeam;
            Set<MissileWarsPlayer> more = redLarger ? redTeam : blueTeam;
            if ((double) less.size() * 3 / 2 > more.size()) {
                break;
            }

            // A shuffled list of players in the lesser team
            // Give extra items to the first `remainder` players
            uneven = true;
            List<MissileWarsPlayer> giveExtra = new ArrayList<>(less);
            int lessAmount = 1 + (more.size() / less.size());
            int remainder = more.size() % less.size();
            Collections.shuffle(giveExtra);
            for (int i = 0; i < giveExtra.size(); i++) {
                int amount = lessAmount;
                if (i < remainder) {
                    amount++;
                }
                giveItemToPlayer(giveExtra.get(i).getMCPlayer(), nextItem, amount, globalLimit);
            }
            
            // Give the team with more players their share too
            more.forEach(mwp -> giveItemToPlayer(mwp.getMCPlayer(), nextItem, 1, globalLimit));
        } while (false);
        
        if (!uneven) {
            redTeam.forEach(mwp -> giveItemToPlayer(mwp.getMCPlayer(), nextItem, 1, globalLimit));
            blueTeam.forEach(mwp -> giveItemToPlayer(mwp.getMCPlayer(), nextItem, 1, globalLimit));
        }
        
        ConfigUtils.schedule(timerTicks, () -> giveNextItem(redTeam, blueTeam));
    }
    
    // TODO
    private void giveItemToPlayer(Player player, RandomItem randomItem, int amount, int globalLimit) {
        
    }
    
    /**
     * Gets next item in the random item distributor
     * 
     * @return
     */
    private RandomItem getNextItem() {
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
    
    /**
     * If random item distribution is currently in progress, multiplies
     * the default timer set by the settings by the given multiplier.
     * The resulting timer is rounded to the nearest tick.
     * 
     * Genuinely does nothing if distributor isn't running
     * 
     * @param multiplier
     */
    public void setTimerMultiplier(double multiplier) {
        timerTicks = (int) Math.round(multiplier * timerTicks);
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
    
    /**
     * Sets the settings to use for the random item distributor.
     * Do not use other than for copying/deserializing the distributor
     * 
     * @param settings
     */
    public void setArenaSettings(ArenaSettings settings) {
        this.settings = settings;
    }
}
