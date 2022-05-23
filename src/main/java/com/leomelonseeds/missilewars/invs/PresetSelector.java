package com.leomelonseeds.missilewars.invs;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
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
        
        String title = deckConfig.getString("title.preset").replace("%deck%", playerJson.getString("Deck"));
        inv = Bukkit.createInventory(null, 27, Component.text(title));
        manager.registerInventory(player, this);
    }
    
    public PresetSelector() {}

    @Override
    public void updateInventory() {
        // TODO Auto-generated method stub

    }

    @Override
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        // TODO Auto-generated method stub

    }

    @Override
    public Inventory getInventory() {
        // TODO Auto-generated method stub
        return null;
    }

}
