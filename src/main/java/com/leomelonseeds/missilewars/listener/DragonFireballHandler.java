package com.leomelonseeds.missilewars.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import com.destroystokyo.paper.event.entity.EnderDragonFireballHitEvent;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class DragonFireballHandler implements Listener {

    private static final double VELMULT = 3;
    private BukkitTask updateTask;
    private Slime slime;
    private DragonFireball fireball;
    private boolean hitDetected;
    
    public DragonFireballHandler(Fireball fireball) {
        this.fireball = (DragonFireball) fireball;
        this.hitDetected = false;
        
        // Spawn armorstand following the fireball so it can be deflected
        slime = (Slime) fireball.getWorld().spawnEntity(fireball.getLocation(), EntityType.SLIME);
        slime.setSize(2);
        // slime.setInvisible(true);
        slime.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20 * 30 * 60, 4, true, true));
        slime.setSilent(true);
        slime.setCollidable(false);
        slime.getCollidableExemptions().add(fireball.getUniqueId());
        updateTask = (Bukkit.getScheduler().runTaskTimer(MissileWarsPlugin.getPlugin(), () -> update(), 0, 1));
    }
    
    // Teleports the slime to in front of the fireball
    private void update() {
        if (hitDetected) {
            return;
        }
        
        slime.teleport(fireball.getLocation().clone().add(fireball.getVelocity().multiply(VELMULT)));
    }
    
    @EventHandler
    private void onHit(EntityDamageByEntityEvent e) {
        // Check if the slime got hit
        if (!e.getEntity().getUniqueId().equals(slime.getUniqueId())) {
            return;
        }

        hitDetected = true;
        e.setDamage(0.0001);
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            fireball.setDirection(slime.getVelocity());
            hitDetected = false;
        }, 1);
    }
    
    @EventHandler
    private void dragonFireball(EnderDragonFireballHitEvent event) {
        if (!event.getEntity().getUniqueId().equals(fireball.getUniqueId())) {
            return;
        }
        
        String[] args = ConfigUtils.toPlain(fireball.customName()).split(":");
        if (!args[0].equals("vdf")) {
            return;
        }
        
        int amplifier = Integer.parseInt(args[1]);
        int duration = Integer.parseInt(args[2]);
        AreaEffectCloud cloud = event.getAreaEffectCloud();
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.HARM, duration, amplifier), true);
        cloud.setDuration(duration * 20);
        cloud.setDurationOnUse(0);
        cloud.setRadiusPerTick(0);
        
        // Mark slime for removal
        updateTask.cancel();
        slime.remove();
        HandlerList.unregisterAll(this);
    }
}
