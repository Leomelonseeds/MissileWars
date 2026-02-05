package com.leomelonseeds.missilewars.arenas.settings;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
    
    /**
     * @param id nullable
     * @param item must be provided cannot be null
     */
    public RandomItem(String id, ItemStack item) {
        this.id = id;
        this.item = item.clone();
        this.weight = 1;
        this.max = 1;
        this.amount = 1;
        this.uuid = UUID.randomUUID();
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
            String[] args = id.split("-");
            int level = Integer.parseInt(args[1]);
            this.item = MissileWarsPlugin.getPlugin().getDeckManager().createItem(args[0], level, false);
            
            if (settings.containsKey("custom-offset")) {
                ItemMeta meta = item.getItemMeta();
                meta.getPersistentDataContainer().set(
                    InventoryUtils.CUSTOM_OFFSET_KEY, 
                    PersistentDataType.STRING, 
                    (String) settings.get("custom-offset")
                );
                item.setItemMeta(meta);
            }
        }

        this.weight = (int) settings.get("weight");
        this.max = (int) settings.get("max");
        this.amount = (int) settings.get("amount");
        this.uuid = UUID.randomUUID();
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
