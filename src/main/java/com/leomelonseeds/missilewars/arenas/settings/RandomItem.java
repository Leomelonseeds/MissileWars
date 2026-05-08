package com.leomelonseeds.missilewars.arenas.settings;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

/**
 * 
 */
public class RandomItem implements ConfigurationSerializable {
    
    private String id; // If null, save item directly, otherwise only save id
    private ItemStack item;
    private int weight; 
    private int max;
    private int amount;
    private UUID uuid; // Use for checking equality
    
    public RandomItem(String id) {
        this(id, null);
    }
    
    public RandomItem(ItemStack item) {
        this(null, item);
    }
    
    private RandomItem(String id, ItemStack item) {
        this.id = id;
        if (id != null) {
            this.item = getItemFromID(id);
        } else {
            this.item = item.clone();
        }
        this.weight = 1;
        this.max = 1;
        this.amount = 1;
        this.uuid = UUID.randomUUID();
        InventoryUtils.setMetaString(item, InventoryUtils.UUID_KEY, uuid.toString());
    }
    
    public RandomItem(RandomItem other) {
        this.id = other.id;
        this.item = other.item.clone();
        this.weight = other.weight;
        this.max = other.max;
        this.amount = other.amount;
        this.uuid = UUID.randomUUID();
        InventoryUtils.setMetaString(item, InventoryUtils.UUID_KEY, uuid.toString());
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> settings = new HashMap<>();
        if (id == null) {
            settings.put("item", item);
        } else {
            settings.put("id", id);
        }
        
        settings.put("weight", weight);
        settings.put("max", max);
        settings.put("amount", amount);
        
        String customOffset = InventoryUtils.getStringFromItemKey(item, InventoryUtils.CUSTOM_OFFSET_KEY);
        if (customOffset != null) {
            settings.put("custom-offset", customOffset);
        }
        
        return settings;
    }
    
    public RandomItem(Map<String, Object> settings) {
        if (settings.containsKey("item")) {
            this.item = (ItemStack) settings.get("item");
        } else {
            this.id = (String) settings.get("id");
            this.item = getItemFromID(id);
            
            if (settings.containsKey("custom-offset")) {
                InventoryUtils.setMetaString(item, InventoryUtils.CUSTOM_OFFSET_KEY, (String) settings.get("custom-offset"));
            }
        }

        this.weight = (int) settings.get("weight");
        this.max = (int) settings.get("max");
        this.amount = (int) settings.get("amount");
        this.uuid = UUID.randomUUID();
        InventoryUtils.setMetaString(item, InventoryUtils.UUID_KEY, uuid.toString());
    }
    
    private ItemStack getItemFromID(String id) {
        String[] args = id.split("-");
        int level = Integer.parseInt(args[1]);
        return MissileWarsPlugin.getPlugin().getDeckManager().createItem(args[0], level, false);
    }

    /**
     * @return a clone of the item for the game
     */
    public ItemStack getItem() {
        return item.clone();
    }
    
    /**
     * @return an item that can be changed
     */
    public ItemStack getModifiableItem() {
        return item;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
    
    public UUID getID() {
        return uuid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RandomItem other = (RandomItem) obj;
        return Objects.equals(uuid, other.uuid);
    }
}
