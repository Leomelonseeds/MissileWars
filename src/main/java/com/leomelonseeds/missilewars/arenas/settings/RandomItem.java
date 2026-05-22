package com.leomelonseeds.missilewars.arenas.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

public class RandomItem implements ConfigurationSerializable {
    
    private String id;
    private ItemStack item;
    private int weight; 
    private int max;
    private int amount;
    private UUID uuid; // Use for checking equality
    
    public RandomItem(String id) {
        this.id = id;
        this.uuid = UUID.randomUUID();
        this.item = getItemFromID(id);
        this.weight = 1;
        this.max = 1;
        this.amount = 1;
        addInfoLore();
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
        settings.put("id", id);
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
        this.uuid = UUID.randomUUID();
        this.id = (String) settings.get("id");
        this.item = getItemFromID(id);
        
        if (settings.containsKey("custom-offset")) {
            InventoryUtils.setMetaString(item, InventoryUtils.CUSTOM_OFFSET_KEY, (String) settings.get("custom-offset"));
        }

        this.weight = (int) settings.get("weight");
        this.max = (int) settings.get("max");
        this.amount = (int) settings.get("amount");
        addInfoLore();
    }
    
    private ItemStack getItemFromID(String id) {
        String[] args = id.split("-");
        int level = Integer.parseInt(args[1]);
        return MissileWarsPlugin.getPlugin().getDeckManager().createRandomItem(args[0], level);
    }
    
    /**
     * Call when max or amount is updated
     */
    private void updateInfoLore() {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        lore.remove(lore.size() - 1);
        lore.remove(lore.size() - 1);
        lore.addAll(getLoreLines());
        meta.lore(lore);
        item.setItemMeta(meta);
    }
    
    /**
     * Adds lore to item, setting max, amount, and UUID
     */
    private void addInfoLore() {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        lore.addAll(getLoreLines());
        meta.lore(lore);
        InventoryUtils.setMetaString(meta, InventoryUtils.UUID_KEY, uuid.toString());
        item.setItemMeta(meta);
    }
    
    private List<Component> getLoreLines() {
        List<Component> res = new ArrayList<>();
        FileConfiguration itemConfig = ConfigUtils.getConfigFile("items.yml");
        List<String> toAdd = itemConfig.getStringList("text.itemstats-random");
        for (String s : toAdd) {
            s = s.replace("%amount%", getWithItemUnits(amount)).replace("%max%", getWithItemUnits(max * amount));
            res.add(ConfigUtils.toComponent(s));
        }
        return res;
    }
    
    private String getWithItemUnits(int amount) {
        return amount == 0 ? "None" : amount + " item" + (amount == 1 ? "" : "s");
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
        updateInfoLore();
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
        updateInfoLore();
    }
    
    public UUID getUUID() {
        return uuid;
    }
    
    public String getId() {
        return id;
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
