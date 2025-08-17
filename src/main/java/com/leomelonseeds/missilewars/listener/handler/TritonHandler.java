package com.leomelonseeds.missilewars.listener.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.decks.Ability;
import com.leomelonseeds.missilewars.decks.Ability.Stat;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.text.Component;

public class TritonHandler implements Listener {
    
    private Map<Player, ItemStack> swordCache;
    private ItemStack trident;
    
    public TritonHandler() {
        this.swordCache = new HashMap<>();
        Bukkit.getPluginManager().registerEvents(this, MissileWarsPlugin.getPlugin());
    }
    
    /**
     * Adds a player to the triton handler, returning
     * the trident item that the gold sword should be replaced with
     * 
     * @param player
     * @param itemStack
     * @return
     */
    public ItemStack addPlayer(Player player, ItemStack sword) {
        // We never auto-remove players from the cache. It should be very rare
        // that the player leaves or quits the game while a trident is in their
        // inventory, and if it happens, the next time they do it the old sword
        // will simply be overriden.
        swordCache.put(player, sword);
        ItemStack res = getTrident().clone();
        res.addUnsafeEnchantments(sword.getEnchantments());
        return res;
    }
    
    private ItemStack getTrident() {
        if (trident == null) {
            initTrident();
        }
        
        return trident;
    }
    
    private void initTrident() {
        ItemStack trident = MissileWarsPlugin.getPlugin().getDeckManager().createItem("trident", 0, false);
        ItemMeta meta = trident.getItemMeta();
        
        // Set modified attack damage
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
            new NamespacedKey(MissileWarsPlugin.getPlugin(), "trident-attack-damage"),
            3.0,
            Operation.ADD_NUMBER
        ));
        
        // Same attack speed but we removed it just so it's custom now
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
            new NamespacedKey(MissileWarsPlugin.getPlugin(), "trident-attack-speed"),
            -2.9,
            Operation.ADD_NUMBER
        ));
        
        // Hide attributes and add text to make it custom
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        List<String> lore = new ArrayList<>();
        for (Component c : meta.lore()) {
            lore.add(ConfigUtils.toPlain(c));
        }
        lore.addAll(List.of(
            "",
            "&7When in Main Hand:",
            " &24 Attack Damage",
            " &21.1 Attack Speed"
        ));
        meta.lore(ConfigUtils.toComponent(lore));
        
        // Set meta and save
        trident.setItemMeta(meta);
        this.trident = trident;
    }
    
    @EventHandler
    public void onTridentThrow(ProjectileLaunchEvent event) {
        if (event.getEntityType() != EntityType.TRIDENT) {
            return;
        }
        
        ProjectileSource shooter = event.getEntity().getShooter();
        if (shooter == null || !(shooter instanceof Player)) {
            return;
        }
        
        Player player = (Player) shooter;
        if (ArenaUtils.getArena(player) == null) {
            return;
        }
        
        ItemStack sword = swordCache.remove(player);
        if (sword == null) {
            return;
        }
        
        int level = MissileWarsPlugin.getPlugin().getJSON().getLevel(player.getUniqueId(), Ability.TRITON);
        if (level <= 0) {
            return;
        }
        
        // Set damage of trident based on triton level
        Trident trident = (Trident) event.getEntity();
        trident.setDamage(ConfigUtils.getAbilityStat(Ability.TRITON, level, Stat.DAMAGE));
        trident.setPickupStatus(PickupStatus.DISALLOWED);
        if (sword.containsEnchantment(Enchantment.FIRE_ASPECT)) {
            trident.setFireTicks(1000);
        }

        // Replace gold sword
        PlayerInventory inv = player.getInventory();
        if (inv.getItemInMainHand().getType() == Material.TRIDENT) {
            inv.setItem(EquipmentSlot.HAND, sword);
        } else {
            inv.setItem(EquipmentSlot.OFF_HAND, sword);
        }
        
        ConfigUtils.sendConfigSound("triton-deactivate", player.getLocation());
    }
    
    // Remove tridents upon hit
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTridentHit(ProjectileHitEvent event) {
        if (event.getEntityType() != EntityType.TRIDENT) {
            return;
        }

        Trident trident = (Trident) event.getEntity();
        Entity hitEntity = event.getHitEntity();
        if (trident.getFireTicks() > 0 && hitEntity != null) {
            if (hitEntity instanceof LivingEntity) {
                hitEntity.setFireTicks(80);
            }
            ConfigUtils.sendConfigSound("trident-hit", hitEntity.getLocation());
        }
        
        ConfigUtils.schedule(20, () -> {
            if (trident.isDead()) {
                return;
            }
            
            Location loc = trident.getLocation();
            ConfigUtils.sendConfigSound("trident-despawn", loc);
            loc.getWorld().spawnParticle(Particle.BUBBLE_POP, loc, 20, 0.2, 0.2, 0.2, 0.05);
            trident.remove();
        });
    }

}
