package com.leomelonseeds.missilewars.listener.handler;

import org.bukkit.Bukkit;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.event.entity.EnderDragonFireballHitEvent;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class DragonFireballHandler implements Listener {
    
    

    private static final double VELMULT = 3;
    private static final int LIFETIME = 30 * 20;
    
    private BukkitTask updateTask;
    private Slime slime;
    private DragonFireball fireball;
    private boolean hitDetected;
    private int timer;
    
    public DragonFireballHandler(Fireball fireball) {
        this.fireball = (DragonFireball) fireball;
        this.hitDetected = false;
        this.timer = 0;
        
        // Spawn slime following the fireball so it can be deflected
        this.slime = fireball.getWorld().spawn(fireball.getLocation(), Slime.class, slime -> {
            slime.setSize(2);
            slime.setInvisible(true); // Set to false to debug
            slime.setSilent(true);
            slime.setAI(false);
        });
        
        updateTask = Bukkit.getScheduler().runTaskTimer(MissileWarsPlugin.getPlugin(), () -> update(), 0, 1);
        Bukkit.getPluginManager().registerEvents(this, MissileWarsPlugin.getPlugin());
    }
    
    public Slime getSlime() {
        return slime;
    }
    
    // Teleports the slime to in front of the fireball
    private void update() {
        if (hitDetected) {
            return;
        }
        
        if (timer >= LIFETIME) {
            remove();
        }
        
        slime.teleport(fireball.getLocation().clone().add(fireball.getVelocity().multiply(VELMULT)));
        slime.setFireTicks(0);
        timer++;
    }
    
    @EventHandler
    private void onEntityHit(ProjectileHitEvent e) {
        if (!e.getEntity().getUniqueId().equals(fireball.getUniqueId())) {
            return;
        }
        
        if (e.getHitEntity() == null) {
            return;
        }
        
        if (e.getHitEntity().getUniqueId().equals(slime.getUniqueId())) {
            e.setCancelled(true);;
        }
    }
    
    @EventHandler
    private void onDeflect(EntityDamageByEntityEvent e) {
        // Check if the slime got hit
        if (!e.getEntity().getUniqueId().equals(slime.getUniqueId())) {
            return;
        }
        
        // Don't want anything to happen to slime
        e.setDamage(0.001);
        for (PotionEffect pe : slime.getActivePotionEffects()) {
            slime.removePotionEffect(pe.getType());
        }
        
        Entity damager = e.getDamager();
        Vector direction;
        if (damager instanceof Projectile) {
            direction = damager.getVelocity().normalize();
        } else if (damager instanceof Explosive) {
            direction = e.getEntity().getLocation().toVector().subtract(damager.getLocation().toVector());
        } else if (damager instanceof Player) {
            direction = ((Player) damager).getEyeLocation().getDirection();
            fireball.setShooter((ProjectileSource) damager);
            ConfigUtils.sendConfigSound("fireball-deflect", (Player) damager);
        } else {
            return;
        }

        hitDetected = true;
        fireball.teleport(fireball.getLocation());
        fireball.setDirection(direction);
        fireball.setVelocity(direction);
        fireball.setAcceleration(direction);
        hitDetected = false;
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
        double radius = Double.parseDouble(args[3]);
        AreaEffectCloud cloud = event.getAreaEffectCloud();
        PotionEffect effect = new PotionEffect(PotionEffectType.INSTANT_DAMAGE, duration, amplifier);
        new DamageSphere((Player) fireball.getShooter(), cloud.getLocation(), radius, effect);
        cloud.remove();
        remove();
    }
    
    private void remove() {
        updateTask.cancel();
        slime.remove();
        fireball.remove();
        HandlerList.unregisterAll(this);
    }
}
