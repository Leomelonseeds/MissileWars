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
public class ChatConfirm implements Listener {
    
    private static Map<Player, ChatConfirm> instances = new HashMap<>();
    private Consumer<Boolean> callback;
    private BukkitTask timeout;
    private Player player;
    private String req;
    private String deny;
    private MissileWarsPlugin plugin;
    private String cancelmsg;
    
    public ChatConfirm(Player player, String req, int time, String cancelmsg, Consumer<Boolean> callback) {
        this(player, req, null, time, cancelmsg, callback);
    }
    
    /**
     * @param player
     * @param req
     * @param deny set to NULL or EMPTY to make any message deny
     * @param time
     * @param cancelmsg
     * @param callback
     */
    public ChatConfirm(Player player, String req, String deny, int time, String cancelmsg, Consumer<Boolean> callback) {
        this.callback = callback;
        this.req = req;
        this.player = player;
        this.cancelmsg = cancelmsg;
        
        if (deny != null && !deny.isEmpty()) {
            this.deny = deny;
        }
        
        // Return if player already is in a chat window
        if (instances.containsKey(player)) {
            player.sendMessage(ConfigUtils.toComponent("&cYou already have an existing chat dialogue!"));
            callback.accept(false);
            return;
        }
        
        instances.put(player, this);
        this.plugin = MissileWarsPlugin.getPlugin();
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        this.timeout = ConfigUtils.schedule(time * 20, () -> {
            stop();
            callback(false);
        });
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    private void onChat(AsyncPlayerChatEvent e) {
        Player sender = e.getPlayer();
        if (!sender.equals(player)) {
            return;
        }

        e.setCancelled(true);
        String msg = e.getMessage();
        boolean success = isCorrect(msg, req);
        if (!success && deny != null && !isCorrect(msg, deny)) {
            return;
        }
        
        stop();
        Bukkit.getScheduler().runTask(plugin, () -> callback(success));
    }
    
    private void callback(boolean success) {
        if (!success && cancelmsg != null && player.isOnline()) {
            player.sendMessage(ConfigUtils.toComponent("&c" + cancelmsg));
        }
        
        callback.accept(success);
    }
    
    private boolean isCorrect(String supplied, String answer) {
        String plural = answer + "s";
        return supplied.equalsIgnoreCase(answer) || supplied.equalsIgnoreCase(plural);
    }
    
    private void stop() {
        this.timeout.cancel();
        HandlerList.unregisterAll(this);
        instances.remove(player);
    }
}
