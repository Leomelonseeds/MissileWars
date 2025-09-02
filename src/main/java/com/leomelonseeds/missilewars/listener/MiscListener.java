package com.leomelonseeds.missilewars.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.ObjectUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.mineacademy.chatcontrol.api.ChannelPreChatEvent;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;

public class MiscListener implements Listener {
    
    // PER-WORLD CHAT HANDLER
    @EventHandler
    public void onChat(ChannelPreChatEvent e) {
        if (!(e.getSender() instanceof Player)) {
            return;
        }
        
        // Messages sent from tutorial or lobby are broadcast everywhere
        Player sender = (Player) e.getSender();
        World world = sender.getWorld();
        if (world.getName().equals("world") || world.getName().equals("mwarena_tutorial")) {
            return;
        }
        
        // Messages sent from arenas are sent to the arena and to hub/tutorial
        Set<Player> recipients = e.getRecipients();
        recipients.clear();
        recipients.addAll(world.getPlayers());
        recipients.addAll(Bukkit.getWorld("world").getPlayers());
        recipients.addAll(Bukkit.getWorld("mwarena_tutorial").getPlayers());
    }
    
    // TRACKER LISTENER
    
    @EventHandler
    public void tntPrimed(TNTPrimeEvent e) {
        // Get arena
        World world = e.getBlock().getWorld();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(world);
        if (arena == null) {
            return;
        }
        
        Bukkit.getScheduler().runTaskLaterAsynchronously(MissileWarsPlugin.getPlugin(), 
                () -> arena.getTracker().assignPrimeSource(e), 1);
    }
    
    @EventHandler
    public void pistonExtend(BlockPistonExtendEvent e) {
        // Get arena
        World world = e.getBlock().getWorld();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(world);
        if (arena == null) {
            return;
        }
        
        arena.getTracker().registerPistonEvent(e);
    }
    
    /** Remove player from Arena if they DC. */
    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {

        // Save player deck configuration
        Player player = event.getPlayer();
        MissileWarsPlugin.getPlugin().getJSON().savePlayer(player.getUniqueId());
        
        // Get Arena player is in and remove them
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            InventoryUtils.saveInventory(player, true);
            return;
        }

        playerArena.removePlayer(player.getUniqueId(), false);
        player.teleport(ConfigUtils.getSpawnLocation());
    }

    /** Handle inventory loading on join */
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.teleport(ConfigUtils.getSpawnLocation());
        if (player.isInvulnerable()) {
            player.setInvulnerable(false);
        }
        
        // Make player undrunk
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "brew " + player.getName() + " 0 10");
        
        // Remove potion effects
        for (PotionEffect effect : player.getActivePotionEffects()){
            if (effect.getType() == PotionEffectType.RESISTANCE) {
                continue;
            }
            player.removePotionEffect(effect.getType());
        }

        // Load player data, making sure for new players that it happens after an entry for them is created.
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        plugin.getSQL().createPlayer(player.getUniqueId(), result -> {
            MissileWarsPlugin.getPlugin().getJSON().loadPlayer(player.getUniqueId());
            InventoryUtils.loadInventory(player);
            RankUtils.setPlayerExpBar(player);
        });
        
        // Make people who are new but didn't do the tutorial yet do the tutorial
        // Bedrock players shouldn't get the cinematic as its fucked
        if (player.hasPermission("umw.new") || ConfigUtils.isBedrockPlayer(player)) {
            ConfigUtils.schedule(20, () -> {
                Arena arena = plugin.getArenaManager().getArena("tutorial");
                if (arena.isResetting()) {
                    ConfigUtils.schedule(20, () -> arena.joinPlayer(player));
                } else {
                    arena.joinPlayer(player);
                }
            });
            
            return;
        }

        // Let new players watch the cinematic
        // Give players permission and then play the cinematic once they move
        if (!player.hasPlayedBefore()) {
            Location initLoc = player.getLocation();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                    "lp user " + player.getName() + " permission set umw.new");
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        this.cancel();
                        return;
                    }
                    
                    if (player.getLocation().distanceSquared(initLoc) > 1) {
                        plugin.getCinematicManager().play(player);
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 20, 20);
        }
    }
    
    // ------------------------------------------------
    // This section handles jump pads (code stolen from HubBasics)
    // ------------------------------------------------
    
    @EventHandler
    public void onPressurePlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }
        
        if (event.getClickedBlock().getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals("world")) {
            return;
        }

        double radians = Math.toRadians(player.getLocation().getYaw());
        double x = -Math.sin(radians) * 1.5;
        double y = 1.0;
        double z = Math.cos(radians) * 1.5;
        player.setVelocity(new Vector(x, y, z));
        ConfigUtils.sendConfigSound("lobby-plate", player);
        event.setCancelled(true);
    }
    
    // --------------------------------------------------
    // This section helps high-ping users with fireball deflections
    // event is high priority to execute after player drop/interact
    // --------------------------------------------------
    
    public static Set<UUID> notLeftClick = new HashSet<>();
    public static Map<Fireball, Slime> fireballs = new HashMap<>();
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        // Cancel if a left click was recently detected
        Player player = event.getPlayer();
        if (notLeftClick.contains(player.getUniqueId())) {
            return;
        }
        
        // Raytraces backwards so players with high ping can still deflect fireballs on their screen
        // Add 2 to delay; idk why fireball locations are so delayed on people's screens...
        Location eyeLoc = player.getEyeLocation();
        Vector eyeDir = eyeLoc.getDirection();
        double delay = player.getPing() / 50.0 + 2;
        Entity target = null;
        for (Fireball fb : fireballs.keySet()) {
            if (fb.isDead() || !fb.getWorld().equals(player.getWorld())) {
                continue;
            }
            
            Location fbLoc = fb.getLocation();
            if (fbLoc.distanceSquared(eyeLoc) > 3 * 3) {
                continue;
            }

            Vector fbDir = fb.getVelocity();
            double maxDist = fbDir.length() * delay + 3;
            Vector screenFbCenter = fb.getBoundingBox().getCenter().subtract(fbDir.multiply(delay));
            BoundingBox screenFbBox = BoundingBox.of(screenFbCenter, 0.5, 0.5, 0.5);
            boolean success = false;
            for (int i = 1; i <= (int) Math.ceil(maxDist); i++) {
                Vector toCheck = eyeLoc.clone().add(eyeDir.clone().multiply(i)).toVector();
                if (!screenFbBox.contains(toCheck)) {
                    continue;
                }
                
                target = ObjectUtils.defaultIfNull(fireballs.get(fb), fb);
                success = true;
                break;
            }
            
            if (success) {
                break;
            }
        }
        
        if (target == null) {
            return;
        }
        
        // Handle dragon fireballs by registering an EDBEE for the handler
        if (target instanceof Slime) {
            @SuppressWarnings("removal")
            EntityDamageByEntityEvent extraEvent = new EntityDamageByEntityEvent(
                    player, 
                    target, 
                    DamageCause.ENTITY_ATTACK, 
                    0.001);
            Bukkit.getPluginManager().callEvent(extraEvent);
            return;
        }
        
        // Thank you so much mister CAG2 for suggesting Player#attack, absolute genius if you ask me
        if (target instanceof Fireball) {
            player.attack(target);
        }
    }
}
