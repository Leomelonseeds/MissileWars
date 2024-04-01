package com.leomelonseeds.missilewars.invs;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.TutorialArena;
import com.leomelonseeds.missilewars.decks.DeckManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.text.Component;

public class PresetSelector implements MWInventory {
    
    private Inventory inv;
    private String deck;
    private Player player;
    private FileConfiguration itemConfig;
    private JSONObject playerJson;
    
    public PresetSelector(Player player, String deck) {
        this.player = player;
        this.deck = deck;
        itemConfig = ConfigUtils.getConfigFile("items.yml");
        playerJson = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
        
        String title = itemConfig.getString("title.preset").replace("%deck%", deck);
        inv = Bukkit.createInventory(null, 54, ConfigUtils.toComponent(title));
        manager.registerInventory(player, this);
    }

    @Override
    public void updateInventory() {
        // Register stage completion if player selects non-sentinel kit
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena instanceof TutorialArena && !playerJson.getString("Deck").equals("Sentinel")) {
            ((TutorialArena) arena).registerStageCompletion(player, 5);
        }
        
        // Add preset items
        DeckManager dm = MissileWarsPlugin.getPlugin().getDeckManager();
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
                    l = l.replaceAll("%gpassive%", "None");
                    l = l.replaceAll("%passive%", "None");
                } else {
                    for (String type : new String[] {"passive", "gpassive"}) {
                        String passive = current.getJSONObject(type).getString("selected");
                        String placeholder = "%" + type + "%";
                        String path = type.equals("gpassive") ? "gpassive." + passive + ".name" :
                            deck + "." + type + "." + passive + ".name";
                        if (!passive.equals("None")) {
                            l = l.replaceAll(placeholder, itemConfig.getString(path));
                        } else {
                            l = l.replaceAll(placeholder, "None");
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
            ItemStack sel = dm.createItem("preset.normal", 0, false);
            ItemMeta selMeta = sel.getItemMeta();
            
            // Different material and name if selected
            String select = "notselect";
            if (deck.equals(playerJson.getString("Deck")) && p.equals(playerJson.getString("Preset"))) {
                select = "select";
            }
            
            sel.setType(Material.valueOf(itemConfig.getString("preset.normal.item" + select)));
            selMeta.displayName(ConfigUtils.toComponent(itemConfig.getString("preset.normal.name" + select)));
            selMeta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "preset"),
                    PersistentDataType.STRING, p);
            sel.setItemMeta(selMeta);
            inv.setItem(i * 2 + 29, sel);
        }
        
        // ItemStack ranked = MissileWarsPlugin.getPlugin().getDeckManager().createItem("ranked", 0, false);
        // inv.setItem(25, ranked);
        
        // Add top info
        ItemStack info = dm.createItem("preset.info." + deck, 0, false);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (playerJson.getString("Deck").equals(deck)) {
            List<Component> lore = infoMeta.lore();
            lore.set(lore.size() - 1, ConfigUtils.toComponent("&aSelected"));
            infoMeta.lore(lore);
            infoMeta.addEnchant(Enchantment.DURABILITY, 1, true);
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
            ItemStack item = new ItemStack(color);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(ConfigUtils.toComponent(""));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        
        // Add bottom panes
        for (int i = 45; i < 54; i++) {
            ItemStack item;
            if (i != 49) {
                item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(ConfigUtils.toComponent(""));
                item.setItemMeta(meta);
            } else {
                item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(ConfigUtils.toComponent("&cBack"));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
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
            playerJson.put("Deck", deck);
            playerJson.put("Preset", "A");
            presetMessage("A");
            updateInventory();
        }
        
        // Clicking on a preset type
        Material itemType = item.getType();
        Material selection = Material.getMaterial(itemConfig.getString("preset.normal.item"));
        Material edit = Material.getMaterial(itemConfig.getString("preset.edit.item"));
        if (itemType == selection || itemType == edit) {
            String p = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(MissileWarsPlugin.getPlugin(), "preset"),
                    PersistentDataType.STRING);
            
            // Check permission for B
            if (p.equals("B") && !player.hasPermission("umw.preset.b")) {
                ConfigUtils.sendConfigMessage("messages.preset-b-locked", player, null, null);
                ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                return;
            }
            
            // Check permission for C
            if (p.equals("C") && !player.hasPermission("umw.preset.c")) {
                ConfigUtils.sendConfigMessage("messages.preset-c-locked", player, null, null);
                ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                return;
            }
            
            // Check item types
            if (itemType == edit) {
                // Open deck customizer
                new DeckCustomizer(player, deck, p);
                return;
            }
            
            // Check item types
            if (itemType == selection) {
                // Choose preset
                playerJson.put("Deck", deck);
                playerJson.put("Preset", p);
                presetMessage(p);
                updateInventory();
            }
        }
    }
    
    private void presetMessage(String preset) {
        String msg = ConfigUtils.getConfigText("messages.change-preset", player, null, null);
        msg = msg.replace("%deck%", deck);
        msg = msg.replace("%preset%", preset);
        player.sendMessage(ConfigUtils.toComponent(msg));
        ConfigUtils.sendConfigSound("change-preset", player);
    }
}
