package com.leomelonseeds.missilewars.arenas;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.listener.handler.ChatConfirm;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class CustomArenaCreationSession {
    
    private Player player;
    private ConfigurationSection promptSection;
    private String denyMessage;
    
    public CustomArenaCreationSession(Player player) {
        this.player = player;
        this.promptSection = ConfigUtils.getConfigFile("messages.yml").getConfigurationSection("messages.custom-arena-confirmation");
        this.denyMessage = "Custom arena creation was cancelled because you either didn't type 'yes' or took too long.";
        prompt(1);
    }
    
    private void prompt(int iteration) {
        if (!player.isOnline()) {
            return;
        }
        
        String sec = "c" + iteration;
        if (!promptSection.contains(sec)) {
            success();
            return;
        }
        
        player.sendMessage(ConfigUtils.toComponent(promptSection.getString(sec)));
        new ChatConfirm(player, "yes", 60, denyMessage, result -> {
            if (result) {
                prompt(iteration + 1);
            }
        });
    }
    
    private void success() {
        ConfigUtils.sendConfigMessage("custom-arena-creating", player);
        ConfigUtils.sendConfigSound("lobby-countdown-start", player);
        MissileWarsPlugin.getPlugin().getArenaManager().createCustomArena(player);
    }
}
