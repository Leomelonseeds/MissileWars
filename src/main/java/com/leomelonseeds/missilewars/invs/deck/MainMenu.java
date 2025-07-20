package com.leomelonseeds.missilewars.invs.deck;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.decks.DeckStorage;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.db.DBCallback;

public class MainMenu extends DeckSubInventory {

    private String deck;
    private String preset;
    private JSONObject playerJson;
    private JSONObject presetJson;
    private FileConfiguration itemConfig;
    private DBCallback presetMenu;
    private boolean isSelected;
    
    public MainMenu(Inventory inv, String deck, String preset, JSONObject playerJson, JSONObject presetJson, FileConfiguration itemConfig, DBCallback presetMenu) {
        super(inv);
        this.deck = deck;
        this.preset = preset;
        this.playerJson = playerJson;
        this.presetJson = presetJson;
        this.itemConfig = itemConfig;
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
        for (String line : deckItemConfig.getStringList("lore")) {
            if (presetJson == null) {
                line = line
                    .replace("%ability%", "None")
                    .replace("%passive%", "None")
                    .replace("%gpassive%", "None");
            } else {
                for (String type : new String[] {"ability", "passive", "gpassive"}) {
                    String placeholder = "%" + type + "%";
                    String passive = presetJson.getJSONObject(type).getString("selected");
                    if (passive.equals("None")) {
                        line = line.replace(placeholder, "None");
                    } else {
                        String path = type.equals("gpassive") ? 
                            "gpassive." + passive + ".name" :
                            deck + "." + type + "." + passive + ".name"; 
                        line = line.replace(placeholder, itemConfig.getString(path));
                    }
                }
            }
            deckItemLore.add(line);
        }
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
        inv.setItem(24, InventoryUtils.createItem("preset.selector"));
    }

    @Override
    public void registerClick(ItemStack item, int slot, ClickType type) {
        if (slot == 21) {
            if (isSelected) {
                return;
            }
            
            isSelected = true;
            playerJson.put("Deck", deck);
            playerJson.put("Preset", preset);
            playerJson.getJSONObject(deck).put("last-preset", preset);
            fillItems();
            return;
        }
        
        if (slot == 24) {
            presetMenu.onQueryDone(null);
            return;
        }
    }

    @Override
    public String getSubTitle() {
        return "Main Menu";
    }

}
