package com.leomelonseeds.missilewars.invs;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.text.Component;

public class PresetSelector implements MWInventory, Listener {
    
    private Inventory inv;
    private String deck;
    private Player player;
    private FileConfiguration deckConfig;
    private JSONObject playerJson;
    
    public PresetSelector(Player player, String deck) {
        this.player = player;
        this.deck = deck;
        
        deckConfig = ConfigUtils.getConfigFile("decks.yml");
        playerJson = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
        
        String title = deckConfig.getString("title.preset").replace("%deck%", deck);
        inv = Bukkit.createInventory(null, 36, ConfigUtils.toComponent(title));
        manager.registerInventory(player, this);
    }
    
    public PresetSelector() {}

    @Override
    public void updateInventory() {
        List<String> presets = MissileWarsPlugin.getPlugin().getDeckManager().getPresets();
        // Add preset items
        for (int i = 0; i < presets.size(); i++) {
            String p = presets.get(i);
            JSONObject current = playerJson.getJSONObject(deck).getJSONObject(p);
            
            ItemStack item = new ItemStack(Material.getMaterial(deckConfig.getString("preset.item")));
            ItemMeta meta = item.getItemMeta();
            
            meta.displayName(ConfigUtils.toComponent(deckConfig.getString("preset.name").replace("%preset%", p)));
            
            List<String> lore = new ArrayList<>();
            for (String l : deckConfig.getStringList("preset.lore")) {
                lore.add(l.replaceAll("%gpassive%", current.getJSONObject("gpassive").getString("selected")));
            }
            
            // Make item glow if selected
            if (deck.equals(playerJson.getString("Deck")) && p.equals(playerJson.getString("Preset"))) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                lore.add(deckConfig.getString("preset.loreselect"));
            } else {
                lore.add(deckConfig.getString("preset.lorenotselect"));
            }
            
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
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        
        // Check for arena selection
        if (!(manager.getInventory(player) instanceof PresetSelector)) {
            return;
        }
        
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())){
            return; 
        }
        
        event.setCancelled(true);

        if (event.getSlot() == 31) {
            String command = "bossshop open decks " + player.getName();
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

}
