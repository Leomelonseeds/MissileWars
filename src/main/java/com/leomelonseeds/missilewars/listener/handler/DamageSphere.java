package com.leomelonseeds.missilewars.listener.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class DamageSphere {
    
    private static Map<Player, Player> mostRecentDamageSphereSource = new HashMap<>();
    
    private Set<Player> damageImmune;
    private Player cause;
    
    /**
     * Create a sphere which will apply damage to any players
     * inside the sphere. The effect is applied every second, and will
     * persist for the duration of the effect.
     * 
     * @param cause
     * @param center
     * @param radius
     * @param effect
     * @return
     */
    public DamageSphere(Player cause, Location center, double radius, PotionEffect effect) {
        this.cause = cause;
        this.damageImmune = new HashSet<>();
        init(center, radius, effect);
    }
    
    public Player getCauser() {
        return cause;
    }
    
    public boolean isRecentlyDamaged(Player player) {
        return damageImmune.contains(player);
    }
    
    private void init(Location center, double radius, PotionEffect effect) {
        ArenaUtils.createParticleSphere(center, Particle.DRAGON_BREATH, radius, 0.75);
        BukkitTask damageTask = Bukkit.getScheduler().runTaskTimer(MissileWarsPlugin.getPlugin(), () -> {
            for (Player player : center.getNearbyPlayers(radius)) {
                if (!ArenaUtils.isInSphere(player.getEyeLocation(), center, radius) &&
                        !ArenaUtils.isInSphere(player.getLocation(), center, radius)) {
                    continue;
                }
                
                if (damageImmune.contains(player)) {
                    continue;
                }
                
                player.addPotionEffect(effect);
                damageImmune.add(player);
                mostRecentDamageSphereSource.put(player, cause);
                ConfigUtils.schedule(19, () -> {
                    damageImmune.remove(player);
                    mostRecentDamageSphereSource.remove(player);
                });
            }
        }, 20, 5);
        
        BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(MissileWarsPlugin.getPlugin(), () ->
            ArenaUtils.createParticleSphere(center, Particle.DRAGON_BREATH, radius, 0.75), 20, 20);
        
        ConfigUtils.schedule(effect.getDuration() * 20, () -> {
            damageTask.cancel();
            particleTask.cancel();
        });
    }
    
    public static Player getLastDamager(Player player) {
        return mostRecentDamageSphereSource.get(player);
    }
}
