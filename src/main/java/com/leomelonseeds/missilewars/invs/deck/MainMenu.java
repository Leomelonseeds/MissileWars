package com.leomelonseeds.missilewars.invs.deck;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.decks.DeckStorage;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class MainMenu extends DeckSubInventory {

    private String preset;
    private JSONObject presetJson;
    private Runnable presetMenu;
    private boolean isSelected;
    
    public MainMenu(MWInventory mwinv, String deck, FileConfiguration itemConfig, JSONObject playerJson, String preset, JSONObject presetJson, Runnable presetMenu) {
        super(mwinv, deck, itemConfig, playerJson);
        this.preset = preset;
        this.presetJson = presetJson;
        this.presetMenu = presetMenu;
        this.isSelected = playerJson.getString("Deck").equals(deck) && 
                playerJson.getString("Preset").equals(preset);
    }

    @Override
    public void fillItems() {
        // Create deck item
        DeckStorage storedDeck = DeckStorage.fromString(deck);
        ConfigurationSection deckItemConfig = itemConfig.getConfigurationSection("preset.deck");
        ItemStack deckItem = storedDeck.getWeapon();
        ItemMeta deckItemMeta = deckItem.getItemMeta();
        
        // Set name
        String deckItemName = deckItemConfig.getString("name");
        deckItemName = deckItemName
            .replace("%color%", storedDeck.getColor())
            .replace("%deck%", deck)
            .replace("%preset%", preset)
            .replace("%select%", deckItemConfig.getString(isSelected ? "selected" : "notselected"));
        deckItemMeta.customName(ConfigUtils.toComponent(deckItemName));
        
        // Set lore (includes passive stuff)
        List<String> deckItemLore = new ArrayList<>();
        deckItemConfig.getStringList("lore").forEach(s -> deckItemLore.add(replaceAbilityNames(s, presetJson)));
        deckItemLore.addAll(deckItemConfig.getStringList("description." + deck));
        deckItemMeta.lore(ConfigUtils.toComponent(deckItemLore));
        
        // Add hide flags and glow if selected
        deckItemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (isSelected) {
            InventoryUtils.addGlow(deckItemMeta);
        }
        
        deckItem.setItemMeta(deckItemMeta);
        inv.setItem(21, deckItem);
        
        Material surrounderMaterial = isSelected ? 
                Material.LIME_STAINED_GLASS_PANE : 
                Material.GRAY_STAINED_GLASS_PANE;
        for (int i : new int[] {11, 12, 13, 20, 22, 29, 30, 31}) {
            inv.setItem(i, InventoryUtils.createBlankItem(surrounderMaterial));
        }
        
        // Preset selection item
        inv.setItem(24, InventoryUtils.createItem("preset.selector.indicator"));
    }

    @Override
    public boolean registerClick(ItemStack item, int slot, ClickType type, Player player) {
        if (slot == 21) {
            if (isSelected) {
                return false;
            }
            
            isSelected = true;
            selectPreset(preset, playerJson, player);
            return true;
        }
        
        if (slot == 24) {
            presetMenu.run();
            return false;
        }
        
        return false;
    }

    @Override
    public String getSubTitle() {
        return "Main Menu";
    }

}
