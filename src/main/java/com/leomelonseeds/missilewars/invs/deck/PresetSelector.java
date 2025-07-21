package com.leomelonseeds.missilewars.invs.deck;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class PresetSelector extends DeckSubInventory {
    
    private static final NamespacedKey presetKey = new NamespacedKey(MissileWarsPlugin.getPlugin(), "preset");
    
    private JSONObject deckJson;
    private ConfigurationSection itemSection;

    public PresetSelector(MWInventory mwinv, String deck, FileConfiguration itemConfig, JSONObject playerJson, JSONObject deckJson) {
        super(mwinv, deck, itemConfig, playerJson);
        this.deckJson = deckJson;
        this.itemSection = itemConfig.getConfigurationSection("preset.selector.preset");
    }

    @Override
    public void fillItems() {
        // Add preset items
        List<String> presets = MissileWarsPlugin.getPlugin().getDeckManager().getPresets();
        int firstSlot = getFirstSlot(presets.size());
        for (int i = 0; i < presets.size(); i++) {
            String preset = presets.get(i);
            boolean isEditing = preset.equals(deckJson.getString("last-preset"));
            boolean isSelected = playerJson.getString("Deck").equals(deck) && 
                    playerJson.getString("Preset").equals(preset);
            
            // Deck edit item name
            String material = isEditing ? itemSection.getString("item-editing") : itemSection.getString("item");
            ItemStack item = new ItemStack(Material.getMaterial(material));
            ItemMeta meta = item.getItemMeta();
            String name = itemSection.getString("name").replace("%preset%", preset);
            if (itemSection.contains("name-rank-requirement." + preset)) {
                name += itemSection.get("name-rank-requirement." + preset);
            }
            meta.displayName(ConfigUtils.toComponent(name));
            
            // Fill item lore with passive info
            List<String> lore = new ArrayList<>();
            JSONObject presetJson = null;
            if (playerJson.getJSONObject(deck).has(preset)) {
                presetJson = playerJson.getJSONObject(deck).getJSONObject(preset);
            }
            for (String line : itemSection.getStringList("lore")) {
                line = replaceAbilityNames(line, presetJson);
                line = line.replace("%select%", isSelected ? 
                        itemSection.getString("selected") : 
                        itemSection.getString("notselected"));
                line = line.replace("%edit%", isEditing ? 
                        itemSection.getString("editing") : 
                        itemSection.getString("notediting"));
                lore.add(line);
            }
            meta.lore(ConfigUtils.toComponent(lore));
            
            if (isSelected) {
                InventoryUtils.addGlow(meta);
            }
            
            // Add data for slot registration identification
            meta.getPersistentDataContainer().set(presetKey, PersistentDataType.STRING, preset);
            item.setItemMeta(meta);
            inv.setItem(firstSlot + i, item);
        }
    }

    @Override
    public boolean registerClick(ItemStack item, int slot, ClickType type, Player player) {
        String preset = InventoryUtils.getStringFromItem(item, "preset");
        if (preset == null) {
            return false;
        }
        
        if (!player.hasPermission("umw.preset." + preset.toLowerCase())) {
            ConfigUtils.sendConfigMessage("messages.preset-locked", player);
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            return false;
        }
        
        if (type == ClickType.RIGHT) {
            if (item.getType().toString().equals(itemSection.getString("item-editing"))) {
                return false;
            }
            
            deckJson.put("last-preset", preset);
            ConfigUtils.sendConfigSound("use-skillpoint", player);
            return true;
        } else if (type == ClickType.LEFT) {
            if (item.containsEnchantment(Enchantment.UNBREAKING)) {
                return false;
            }

            selectPreset(preset, playerJson, player);
            return true;
        }
        
        return false;
    }

    @Override
    public String getSubTitle() {
        return "Preset Selection";
    }

}
