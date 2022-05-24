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
        inv = Bukkit.createInventory(null, 36, ConfigUtils.toComponent(title));
        manager.registerInventory(player, this);
    }

    @Override
    public void updateInventory() {
        List<String> presets = MissileWarsPlugin.getPlugin().getDeckManager().getPresets();
        // Add preset items
        for (int i = 0; i < presets.size(); i++) {
            String p = presets.get(i);
            JSONObject current = playerJson.getJSONObject(deck).getJSONObject(p);
            
            ItemStack item = new ItemStack(Material.getMaterial(itemConfig.getString("preset.item")));
            ItemMeta meta = item.getItemMeta();
            
            meta.displayName(ConfigUtils.toComponent(itemConfig.getString("preset.name").replace("%preset%", p)));
            
            List<String> lore = new ArrayList<>();
            for (String l : itemConfig.getStringList("preset.lore")) {
                lore.add(l.replaceAll("%gpassive%", current.getJSONObject("gpassive").getString("selected")));
            }
            
            // Make item glow if selected
            if (deck.equals(playerJson.getString("Deck")) && p.equals(playerJson.getString("Preset"))) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                lore.add(itemConfig.getString("preset.loreselect"));
            } else {
                lore.add(itemConfig.getString("preset.lorenotselect"));
            }
            
            // Add data for slot registration identification
            meta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "preset"),
                    PersistentDataType.STRING, p);
            
            meta.lore(ConfigUtils.toComponent(lore));
            item.setItemMeta(meta);
            // Slots 11, 13, 15
            inv.setItem(i * 2 + 11, item);
        }
        
        // Add bottom panes
        for (int i = 27; i < 36; i++) {
            ItemStack item;
            if (i != 31) {
                item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(""));
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
        if (slot == 31) {
            String command = "bossshop open decks " + player.getName();
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
            return;
        }
        
        // Clicking on a preset type
        if (item.getType() == Material.getMaterial(itemConfig.getString("preset.item"))) {
            String p = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(MissileWarsPlugin.getPlugin(), "preset"),
                    PersistentDataType.STRING);
            if (type == ClickType.RIGHT) {
                // Open deck customizer
                new DeckCustomizer(player, deck, p);
            } else if (type == ClickType.LEFT) {
                // Choose preset
                playerJson.put("Deck", deck);
                playerJson.put("Preset", p);
                MissileWarsPlugin.getPlugin().getJSON().setPlayer(player.getUniqueId(), playerJson);
                updateInventory();
            }
        }
    }

}
