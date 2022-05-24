package com.leomelonseeds.missilewars.invs;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.decks.DeckManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.text.Component;

public class DeckCustomizer implements MWInventory {
    
    private Inventory inv;
    private JSONObject json;
    private Player player;
    private String deck;
    private FileConfiguration itemConfig;
    private DeckManager deckManager;
    
    public DeckCustomizer(Player player, String deck, String preset) {
        JSONObject init = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
        json = init.getJSONObject(deck).getJSONObject(preset);
        itemConfig = ConfigUtils.getConfigFile("items.yml");
        deckManager = MissileWarsPlugin.getPlugin().getDeckManager();
        this.player = player;
        this.deck = deck;
        
        String title = itemConfig.getString("title.deck").replace("%deck%", deck).replace("%preset%", preset);
        inv = Bukkit.createInventory(null, 54, ConfigUtils.toComponent(title));
        manager.registerInventory(player, this);
    }

    @Override
    public void updateInventory() {
        // Add indicators
        for (String key : itemConfig.getConfigurationSection("indicators").getKeys(false)) {
            ItemStack item = deckManager.createItem("indicators." + key, 0, false, true);
            if (key.equals("skillpoints")) {
                ItemMeta meta = item.getItemMeta();
                String name = ConfigUtils.toPlain(item.getItemMeta().displayName());
                meta.displayName(ConfigUtils.toComponent(name.replace("%sp%", json.getInt("skillpoints") + "")));
                item.setItemMeta(meta);
            }
            inv.setItem(itemConfig.getInt("indicators." + key + ".slot"), item);
        }
        
        // Add panes and misc items
        for (int i = 0; i < 6; i++) {
            inv.setItem(i * 9 + 1, blankName(new ItemStack(Material.IRON_BARS)));
        }
        
        // Too lazy to come up with formula
        for (int i : new int[]{35, 44}) {
            inv.setItem(i, blankName(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
        }
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        // Back button
        if (slot == 53) {
            new PresetSelector(player, deck);
            return;
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
    
    // Return an item with a blank name
    private ItemStack blankName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(""));
        item.setItemMeta(meta);
        return item;
    }
}
