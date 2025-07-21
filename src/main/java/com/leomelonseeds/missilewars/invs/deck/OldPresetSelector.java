package com.leomelonseeds.missilewars.invs.deck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.TutorialArena;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

public class OldPresetSelector extends MWInventory {
    
    private String deck;
    private FileConfiguration itemConfig;
    private JSONObject playerJson;
    
    public OldPresetSelector(Player player, String deck) {
        super(player, 54, 
            ConfigUtils.getConfigFile("items.yml")
                .getString("title.preset")
                .replace("%deck%", deck)
        );
        this.deck = deck;
        itemConfig = ConfigUtils.getConfigFile("items.yml");
        playerJson = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
    }

    @Override
    public void updateInventory() {
        // Register stage completion if player selects non-sentinel kit
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena instanceof TutorialArena && playerJson.getString("Deck").equals("Berserker")) {
            ((TutorialArena) arena).registerStageCompletion(player, 5);
        }
        
        // Add preset items
        List<String> presets = MissileWarsPlugin.getPlugin().getDeckManager().getPresets();
        for (int i = 0; i < presets.size(); i++) {
            String p = presets.get(i);
            
            // Create deck edit item
            ItemStack item = new ItemStack(Material.getMaterial(itemConfig.getString("preset.edit.item")));
            ItemMeta meta = item.getItemMeta();
            String name = itemConfig.getString("preset.edit.name").replace("%preset%", p);
            if (p.equals("B")) {
                name += " &8(requires &e&lESQUIRE&8)";
            } else if (p.equals("C")) {
                name += " &8(requires &b&lKNIGHT&8)";
            }
            meta.displayName(ConfigUtils.toComponent(name));
            
            // Fill item lore with passive info
            List<String> lore = new ArrayList<>();
            JSONObject current = null;
            if (playerJson.getJSONObject(deck).has(p)) {
                current = playerJson.getJSONObject(deck).getJSONObject(p);
            }

            // Use null if player doesn't have the json, otherwise do manual replacements
            for (String l : itemConfig.getStringList("preset.edit.lore")) {
                if (current == null) {
                    l = l
                        .replace("%ability%", "None")
                        .replace("%passive%", "None")
                        .replace("%gpassive%", "None");
                } else {
                    for (String type : new String[] {"ability", "passive", "gpassive"}) {
                        String placeholder = "%" + type + "%";
                        String passive = current.getJSONObject(type).getString("selected");
                        if (passive.equals("None")) {
                            l = l.replace(placeholder, "None");
                        } else {
                            String path = type.equals("gpassive") ? 
                                "gpassive." + passive + ".name" :
                                deck + "." + type + "." + passive + ".name"; 
                            l = l.replace(placeholder, itemConfig.getString(path));
                        }
                    }
                }
                lore.add(l);
            }
            
            // Add data for slot registration identification
            meta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "preset"),
                    PersistentDataType.STRING, p);
            meta.lore(ConfigUtils.toComponent(lore));
            item.setItemMeta(meta);
            // Slots 20, 22, 24
            // NO RANKED PRESET AT THE MOMENT, PUT 19 INSTEAD OF 20 IF ADDED BACK
            inv.setItem(i * 2 + 20, item);
            
            
            // Create deck selection item
            ItemStack sel = InventoryUtils.createItem("preset.normal");
            ItemMeta selMeta = sel.getItemMeta();
            
            // Different material and name if selected
            String select = "notselect";
            if (deck.equals(playerJson.getString("Deck")) && p.equals(playerJson.getString("Preset"))) {
                select = "select";
            }
            
            sel = sel.withType(Material.valueOf(itemConfig.getString("preset.normal.item" + select)));
            selMeta.displayName(ConfigUtils.toComponent(itemConfig.getString("preset.normal.name" + select)));
            selMeta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "preset"),
                    PersistentDataType.STRING, p);
            sel.setItemMeta(selMeta);
            inv.setItem(i * 2 + 29, sel);
        }
        
        // ItemStack ranked = MissileWarsPlugin.getPlugin().getDeckManager().createItem("ranked", 0, false);
        // inv.setItem(25, ranked);
        
        // Add top info
        ItemStack info = InventoryUtils.createItem("preset.info." + deck);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (playerJson.getString("Deck").equals(deck)) {
            List<Component> lore = infoMeta.lore();
            lore.set(lore.size() - 1, ConfigUtils.toComponent("&aSelected"));
            infoMeta.lore(lore);
            infoMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);
        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                continue;
            }
            Material color = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            switch (deck) {
            case "Architect":
                color = Material.GREEN_STAINED_GLASS_PANE;
                break;
            case "Berserker":
                color = Material.PINK_STAINED_GLASS_PANE;
                break;
            case "Vanguard":
                color = Material.ORANGE_STAINED_GLASS_PANE;
            }
            inv.setItem(i, InventoryUtils.createBlankItem(color));
        }
        
        // Add bottom panes
        for (int i = 45; i < 54; i++) {
            if (i != 49) {
                inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
                continue;
            } 
            
            ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(ConfigUtils.toComponent("&cBack"));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        ItemStack item = inv.getItem(slot);
        
        if (item == null) {
            return;
        }
        
        // Back button
        if (slot == 49) {
            String command = "bossshop open decks " + player.getName();
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
            return;
        }
        
        // Info button
        if (slot == 4) {
            String preset = playerJson.getJSONObject(deck).getString("last-preset");
            playerJson.put("Deck", deck);
            playerJson.put("Preset", preset);
            presetMessage(preset);
            updateInventory();
        }
        
        // Clicking on a preset type
        Material itemType = item.getType();
        Material selection = Material.getMaterial(itemConfig.getString("preset.normal.item"));
        Material edit = Material.getMaterial(itemConfig.getString("preset.edit.item"));
        if (itemType == selection || itemType == edit) {
            String p = InventoryUtils.getStringFromItem(item, "preset");
            if (!player.hasPermission("umw.preset." + p.toLowerCase())) {
                ConfigUtils.sendConfigMessage("messages.preset-locked", player);
                ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                return;
            }
            
            // Check item types
            if (itemType == edit) {
                // Open deck customizer
                new OldDeckCustomizer(player, deck, p);
                return;
            }
            
            // Check item types
            if (itemType == selection) {
                // Choose preset
                playerJson.put("Deck", deck);
                playerJson.put("Preset", p);
                playerJson.getJSONObject(deck).put("last-preset", p);
                presetMessage(p);
                updateInventory();
            }
        }
    }
    
    private void presetMessage(String preset) {
        ConfigUtils.sendConfigMessage("change-preset", player, Map.of("%deck%", deck, "%preset%", preset));
        ConfigUtils.sendConfigSound("change-preset", player);
    }
}
