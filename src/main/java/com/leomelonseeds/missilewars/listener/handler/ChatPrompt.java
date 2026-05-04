package com.leomelonseeds.missilewars.listener.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

@SuppressWarnings("deprecation")
public class ChatPrompt implements Listener {
    
    private static Map<Player, ChatPrompt> instances = new HashMap<>();
    private Consumer<String> callback;
    private BukkitTask timeout;
    private Player player;
    private MissileWarsPlugin plugin;
    
    /**
     * @param player
     * @param req
     * @param deny set to NULL or EMPTY to make any message deny
     * @param time
     * @param cancelmsg
     * @param callback
     */
    public ChatPrompt(Player player, int time, Consumer<String> callback) {
        this.callback = callback;
        this.player = player;
        
        // Cancel the other chat window if player is in one
        if (instances.containsKey(player)) {
            instances.get(player).stop();
        }
        
        instances.put(player, this);
        this.plugin = MissileWarsPlugin.getPlugin();
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        this.timeout = ConfigUtils.schedule(time * 20, () -> {
            stop();
            callback("");
        });
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    private void onChat(AsyncPlayerChatEvent e) {
        Player sender = e.getPlayer();
        if (!sender.equals(player)) {
            return;
        }

        e.setCancelled(true);
        stop();
        Bukkit.getScheduler().runTask(plugin, () -> callback(e.getMessage()));
    }
    
    private void callback(String name) {
        callback.accept(name);
    }
    
    private void stop() {
        this.timeout.cancel();
        HandlerList.unregisterAll(this);
        instances.remove(player);
    }
}
