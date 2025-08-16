package com.leomelonseeds.missilewars.listener.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer.Stat;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.decks.Ability;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

public class CanopyManager {
    
    private static CanopyManager instance;
    
    /** Time after throwing a canopy that it spawns. Must be >= 20 */
    private final int CANOPY_DELAY = 40;
    
    private Set<Player> canopy_freeze;
    private Set<Player> canopy_explosion;
    private Map<Player, ItemStack> canopy_cooldown;
    private Map<Location, Integer> canopy_extensions;
    
    public static CanopyManager getInstance() {
        if (instance == null) {
            instance = new CanopyManager();
        }
        
        return instance;
    }
    
    private CanopyManager() {
        canopy_freeze = new HashSet<>();
        canopy_explosion = new HashSet<>();
        canopy_cooldown = new HashMap<>();
        canopy_extensions = new HashMap<>();
    }
    
    /**
     * Register canopy extension (from splash most likely)
     * 
     * @param key
     * @param extraduration
     */
    public void registerExtension(Location key, int extraduration) {
        if (canopy_extensions.containsKey(key)) {
            canopy_extensions.put(key, canopy_extensions.get(key) + extraduration);
        } else {
            canopy_extensions.put(key, extraduration);
        }
    }

    
    /**
     * Unregister player from canopy manager, and return
     * the player's itemstack
     * 
     * @param player
     * @return
     */
    public ItemStack removePlayer(Player player) {
        canopy_freeze.remove(player);
        return canopy_cooldown.remove(player);
    }
    
    /**
     * Check if player frozen
     * 
     * @param player
     * @return
     */
    public boolean isFrozen(Player player) {
        return canopy_freeze.contains(player);
    }
    
    /**
     * Check if player recently caused a canopy explosion.
     * Use to disable portal breaking with canopy
     * 
     * @param player
     * @return
     */
    public boolean justExploded(Entity player) {
        return canopy_explosion.contains(player);
    }
    
    /**
     * Set after player initially throws canopy
     * 
     * @param player
     */
    public void initPlayer(Player player, ItemStack hand, Arena playerArena, int canopy_distance) {
        if (canopy_cooldown.containsKey(player)) {
            return;
        }
        
        // Spawn ender eye
        Location eyeLoc = player.getEyeLocation();
        EnderSignal signal = (EnderSignal) playerArena.getWorld().spawnEntity(eyeLoc, EntityType.EYE_OF_ENDER);
        signal.setDropItem(false);
        
        // Check for explosive canopy
        boolean explosive = false;
        do {
            // Replace with ability check later
            int level = MissileWarsPlugin.getPlugin().getJSON().getLevel(player.getUniqueId(), Ability.EXPLOSIVE_CANOPY);
            if (level <= 0) {
                break;
            }
            
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand.getType() != Material.DRAGON_HEAD || player.hasCooldown(offhand.getType())) {
                break;
            }
            
            // Store explosion power and other stats in signal's custom name
            double power = ConfigUtils.getAbilityStat(Ability.EXPLOSIVE_CANOPY, level, Ability.Stat.POWER);
            String dragonFireball = InventoryUtils.getStringFromItem(offhand, "item-utility");
            int amplifier = 0;
            int duration = 0;
            double radius = 0;
            if (dragonFireball != null) {
                String args[] = dragonFireball.split("-");
                int fireballLevel = Integer.parseInt(args[1]);
                amplifier = (int) ConfigUtils.getItemValue(args[0], fireballLevel, "amplifier");
                duration = (int) ConfigUtils.getItemValue(args[0], fireballLevel, "duration");
                radius = Double.parseDouble(ConfigUtils.getItemValue(args[0], fireballLevel, "radius") + "");
            }
            
            // Store these stats in the ender signals' custom name for later access
            signal.customName(ConfigUtils.toComponent(power + ":" + amplifier + ":" + duration + ":" + radius));
            InventoryUtils.consumeItem(player, playerArena, offhand, -1);
            explosive = true;
        } while (false);
        
        // Set target location
        Vector distance = eyeLoc.getDirection().multiply(canopy_distance);
        Location target = eyeLoc.clone().add(distance).toCenterLocation();
        ConfigUtils.sendConfigSound("launch-canopy", player.getLocation());

        // Consume item and update canopy so it travels to correct location
        // No clue why I need to do this but oh well
        InventoryUtils.consumeItem(player, playerArena, hand, -1);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (signal.isDead()) {
                    this.cancel();
                    return;
                }
                signal.setTargetLocation(target, false);
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), 0, 5);
        
        // Switch to explosion code if explosive
        if (explosive) {
            prepareCanopyExplosion(player, signal);
            return;
        }

        // Add player to canopy cooldown to give item back on death
        canopy_cooldown.put(player, hand);
        
        // Send sound 1 second before the tp
        ConfigUtils.schedule(CANOPY_DELAY - 20, () -> {
            if (canopy_cooldown.containsKey(player)) {
                ConfigUtils.sendConfigSound("canopy-activate", player);
            }
        });

        ConfigUtils.schedule(CANOPY_DELAY, () -> spawnCanopy(player, playerArena, signal));
    }
    
    private void prepareCanopyExplosion(Player player, EnderSignal signal) {
        World world = signal.getWorld();
        ArenaUtils.playSoundFollowingEntity(Sound.ENTITY_WARDEN_ANGRY, signal, 1F, 1.2F);
        ArenaUtils.doUntilDead(signal, t -> world.spawnParticle(Particle.DRAGON_BREATH, signal.getLocation(), 1, 0, 0, 0, 0.2, null, true));
        
        // Flashes to indicate about to explode. Same code as astral turret flashes
        ItemStack signalItem = new ItemStack(Material.ENDER_EYE);
        ItemStack signalExplodeItem = new ItemStack(Material.ENDER_PEARL);
        ConfigUtils.schedule(40, () -> {
            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    if (signal.isDead()) {
                        this.cancel();
                        return;
                    }
                    
                    if (t < 20) {
                        if (t % 10 == 0) {
                            signal.setItem(signalExplodeItem);
                        } else if ((t - 5) % 10 == 0) {
                            signal.setItem(signalItem);
                        }
                    } else if (t < 40) {
                        if (t % 5 == 0) {
                            signal.setItem(signalExplodeItem);
                        } else if ((t - 3) % 5 == 0) {
                            signal.setItem(signalItem);
                        }
                    }
                    
                    t++;
                }
            }.runTaskTimer(MissileWarsPlugin.getPlugin(), 0, 1);
        });
        
        // SFX and explosion
        ConfigUtils.schedule(60, () -> ConfigUtils.sendConfigSound("canopy-activate", signal.getLocation()));
        ConfigUtils.schedule(80, () -> {
            canopy_explosion.add(player);
            Location loc = signal.getLocation();
            String args[] = ConfigUtils.toPlain(signal.customName()).split(":");
            float power = Float.parseFloat(args[0]);
            world.createExplosion(player, loc, power, false, true, false);
            world.spawnParticle(Particle.DRAGON_BREATH, loc, 200, 0, 0, 0, 0.8, null, true);
            ConfigUtils.sendConfigSound("canopy-explode", loc);
            signal.setDespawnTimer(80);
            ConfigUtils.schedule(1, () -> canopy_explosion.remove(player));
            
            // Create area effect sphere from dragon fireball
            int amplifier = Integer.parseInt(args[1]);
            int duration = Integer.parseInt(args[2]);
            double radius = Double.parseDouble(args[3]);
            PotionEffect effect = new PotionEffect(PotionEffectType.INSTANT_DAMAGE, duration, amplifier);
            new DamageSphere(player, loc, radius, effect);
        });
    }
    
    private void spawnCanopy(Player player, Arena playerArena, EnderSignal signal) {
        // Ignore offline players, or if signal is dead
        if (!player.isOnline() || signal.isDead()) {
            return;
        }
        
        ItemStack hand = canopy_cooldown.remove(player);
        if (hand == null) {
            return;
        }
        
        // Check if canopy destination is blocked
        Location spawnLoc = signal.getLocation().toCenterLocation();
        if (spawnLoc.getBlock().getType() != Material.AIR ||
            spawnLoc.clone().add(0, 1, 0).getBlock().getType() != Material.AIR) {
            ConfigUtils.sendConfigMessage("canopy-blocked", player);
            InventoryUtils.regiveItem(player, hand);
            return;
        }
        
        // Check if player would take too much fall damage.
        // Raw fall damage is ceil of player fall distance - 3
        // Each level of jump boost further reduces fall damage by 1
        // Each feather falling level reduces fall damage by 12%
        double rawDmg = Math.ceil(player.getFallDistance()) - 3;
        PotionEffect jump = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jump != null) {
            rawDmg -= jump.getAmplifier() + 1;
        }
        
        int ff = player.getInventory().getItem(EquipmentSlot.FEET).getEnchantmentLevel(Enchantment.FEATHER_FALLING);
        if (ff > 0) {
            rawDmg *= 1 - 0.12 * ff;
        }
        
        if (rawDmg > player.getHealth()) {
            ConfigUtils.sendConfigMessage("canopy-fall", player);
            InventoryUtils.regiveItem(player, hand);
            return;
        }
        
        // Try to teleport player finally (but regive if unbreakable blocks)
        boolean isRed = playerArena.getTeam(player.getUniqueId()) == TeamName.RED;
        if (!SchematicManager.spawnNBTStructure(player, "canopy-1", spawnLoc, isRed, false, true)) {
            InventoryUtils.regiveItem(player, hand);
            return;
        }
            
        // Teleport and remove ender eye
        Location loc = spawnLoc.add(0, -0.5, 0);
        loc.getBlock().setType(Material.COBWEB);
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(player.getLocation().getPitch());
        player.teleport(loc);
        player.damage(rawDmg);
        signal.remove();

        // Stop players from placing missiles or using utility for a second
        canopy_freeze.add(player);
        ConfigUtils.schedule(10, () -> canopy_freeze.remove(player));
        
        // Do final checks
        ConfigUtils.sendConfigSound("spawn-canopy", spawnLoc);
        playerArena.getPlayerInArena(player.getUniqueId()).incrementStat(Stat.UTILITY);
        despawnCanopy(spawnLoc, 5);
    }
    
    private void despawnCanopy(Location loc, int duration) {
        ConfigUtils.schedule(duration * 20, () -> {
            Location wood = loc.clone().add(0, -1, 0).getBlock().getLocation();
            if (wood.getBlock().getType() != Material.OAK_WOOD) {
                return;
            }
            
            if (canopy_extensions.containsKey(wood)) {
                despawnCanopy(loc, canopy_extensions.get(wood));
                canopy_extensions.remove(wood);
                return;
            }
            
            wood.getBlock().setType(Material.AIR);
            
            // There may be a cobweb - set that to air too
            Location cobweb = wood.clone().add(0, 1, 0);
            if (cobweb.getBlock().getType() == Material.COBWEB) {
                cobweb.getBlock().setType(Material.AIR);
            }
        });
    }
}
