package com.leomelonseeds.missilewars.listener.handler;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class SmokeShieldHandler {
    
    private static final double smoke_size = 0.5;
    private static final double rhumb_angle = Math.PI / 16;
    private static final int rhumb_dots_per_tick = 2;
    private static final int rhumb_dots_min_t = -15;
    private static final DustOptions particleData = new DustOptions(Color.BLACK, 1.0F);
    private static final PotionEffect blindness = new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, true);
    
    private Set<Player> taggedPlayers;
    private Set<BukkitTask> tasks;
    private Location center;
    private int duration;
    private double radius;
    
    /**
     * @param center
     * @param duration in ticks
     * @param radius 
     */
    public SmokeShieldHandler(Location center, int duration, double radius) {
        this.tasks = new HashSet<>();
        this.taggedPlayers = new HashSet<>();
        this.center = center.toCenterLocation();
        this.duration = duration;
        this.radius = radius;
        spawnSmoke();
        checkForPlayers();
        ConfigUtils.schedule(duration, () -> removeShield());
    }
    
    private void spawnSmoke() {
        double d = radius / 3;
        center.getWorld().spawnParticle(Particle.SMOKE, center, 250, d, d, d, 0.1, null, true);
        ConfigUtils.sendConfigSound("smokeshield", center);
        ConfigUtils.schedule(9, () -> maintainSmoke());
    }
    
    private void maintainSmoke() {
        boolean isLong = duration >= 240;
        Particle smoke = isLong ? Particle.CAMPFIRE_SIGNAL_SMOKE : Particle.CAMPFIRE_COSY_SMOKE;
        int partDur = isLong ? 240 : 40;
        BukkitTask smokeMaintainer = Bukkit.getScheduler().runTaskTimer(
            MissileWarsPlugin.getPlugin(), () -> 
                ArenaUtils.createParticleSphere(center, smoke, radius, smoke_size), 0, partDur);
        if (duration % partDur != 0) {
            ConfigUtils.schedule(duration - partDur, () -> ArenaUtils.createParticleSphere(center, smoke, radius, smoke_size));
        }
        ConfigUtils.schedule(duration - partDur, () -> smokeMaintainer.cancel());
        
        // Adds spiral particle effect travelling upwards
        for (int i = rhumb_dots_min_t; i < -rhumb_dots_min_t; i += 3) {
            spiralParticles(i);
        }
    }
    
    // Spawn particles spiraling both upwards and downards
    private void spiralParticles(double t_start) {
        int delay = (int) t_start - rhumb_dots_min_t;
        tasks.add(new BukkitRunnable() {
            
            double t = t_start;
            
            @Override
            public void run() {
                for (int i = 0; i < rhumb_dots_per_tick; i++) {
                    double[] loc = ArenaUtils.rhumbLine(radius + 0.3, rhumb_angle, t);
                    center.getWorld().spawnParticle(
                            Particle.DUST, 
                            center.getX() + loc[0], 
                            center.getY() + loc[1], 
                            center.getZ() + loc[2], 
                            1, 0, 0, 0, 0, 
                            particleData, true);
                    center.getWorld().spawnParticle(
                            Particle.DUST, 
                            center.getX() - loc[0], 
                            center.getY() + loc[1], 
                            center.getZ() - loc[2], 
                            1, 0, 0, 0, 0, 
                            particleData, true);  
                    if (t >= rhumb_dots_min_t * -1) {
                        t = rhumb_dots_min_t;
                    } else {
                        t += 0.1;
                    }
                }
            }
        }.runTaskTimerAsynchronously(MissileWarsPlugin.getPlugin(), delay, 1));
    }
    
    private void checkForPlayers() {
        tasks.add(Bukkit.getScheduler().runTaskTimer(MissileWarsPlugin.getPlugin(), () -> {
            for (Player player : center.getNearbyPlayers(radius)) {
                if (player.getGameMode() != GameMode.SURVIVAL) {
                    continue;
                }
                
                if (taggedPlayers.contains(player)) {
                    continue;
                }
                
                if (!ArenaUtils.isInSphere(player.getEyeLocation(), center, radius)) {
                    continue;
                }
                
                player.addPotionEffect(blindness);
                tagPlayer(player);
            }
        }, 10, 5));
    }
    
    private void tagPlayer(Player player) {
        taggedPlayers.add(player);
        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                if (ArenaUtils.isInSphere(player.getEyeLocation(), center, radius)) {
                    return;
                }
                
                untagPlayer(player);
                this.cancel();
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), 0, 5));
    }
    
    private void untagPlayer(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        taggedPlayers.remove(player);
    }
    
    private void removeShield() {
        tasks.forEach(t -> t.cancel());
        tasks.clear();
        taggedPlayers.forEach(p -> untagPlayer(p));
        taggedPlayers.clear();
    }
}
