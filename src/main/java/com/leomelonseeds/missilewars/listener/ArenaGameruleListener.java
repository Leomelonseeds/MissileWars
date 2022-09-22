package com.leomelonseeds.missilewars.listener;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.json.JSONObject;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.ess3.api.events.AfkStatusChangeEvent;
import net.kyori.adventure.text.Component;

/** Class to listen for events relating to Arena game rules. */
public class ArenaGameruleListener implements Listener {

    /** Event to ignore hunger. */
    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity().getWorld().getName().contains("mwarena_")) {
            event.setCancelled(true);
        }
    }

    /** Handle void death. Works outside arenas too. */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
    	//Check if entity is player
    	if (!(event.getEntity() instanceof Player)) {
            return;
        }

        // Cause instant death so player can respawn faster
        if (event.getCause() == DamageCause.VOID) {
            event.setDamage(40.0);
        }
    }
    
    // Handle player regen in boomlust
    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        
        if (event.getRegainReason() != RegainReason.SATIATED) { 
            return; 
        }
        
        //Check if entity is player
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        // Check if player was killed in an Arena
        Player player = (Player) event.getEntity();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            return;
        }
        
        // True means boomlust is active and player cannot regen
        if (playerArena.getPlayerInArena(player.getUniqueId()).getBoomLustRegen()) {
            event.setCancelled(true);
        }
    }

    /** Handle player deaths. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Check if player was killed in an Arena
        Player player = event.getEntity();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
        	player.setBedSpawnLocation(ConfigUtils.getSpawnLocation(), true);
            return;
        }

        // Find killer and increment kills
        if (player.getKiller() != null) {
            MissileWarsPlayer killer = playerArena.getPlayerInArena(player.getKiller().getUniqueId());
            if (!player.getKiller().equals(player)) {
                killer.incrementKills();
                ConfigUtils.sendConfigSound("player-kill", killer.getMCPlayer());
                
                // Berserker boomlust
                if (MissileWarsPlugin.getPlugin().getJSON().getAbility(killer.getMCPlayerId(), "boomlust") > 0) {
                    playerArena.getPlayerInArena(killer.getMCPlayerId()).setBoomLust(true);
                    playerArena.getPlayerInArena(killer.getMCPlayerId()).setBoomLustRegen(true);
                }
                
                // Sentinel retaliate
                if (MissileWarsPlugin.getPlugin().getJSON().getAbility(player.getUniqueId(), "retaliate") > 0) {
                    playerArena.getPlayerInArena(player.getUniqueId()).setRetaliate(true);
                }
            }
        }

        Component deathMessage = event.deathMessage();
        event.deathMessage(Component.text(""));

        // Count death if player is on a team
        if (!playerArena.getTeam(player.getUniqueId()).equals("no team")) {
            MissileWarsPlayer missileWarsPlayer = playerArena.getPlayerInArena(player.getUniqueId());
            missileWarsPlayer.incrementDeaths();
            for (Player p : player.getWorld().getPlayers()) {
                p.sendMessage(deathMessage);
            }
        }

        // Un-obstruct spawns
        Location spawn1 = playerArena.getPlayerSpawn(player);
        Location spawn2 = spawn1.clone().add(0, 1, 0);
        for (Location l : new Location[] {spawn1, spawn2}) {
            if (l.getBlock().getType() != Material.AIR) {
                l.getBlock().setType(Material.AIR);
            }
        }
        
        player.setBedSpawnLocation(playerArena.getPlayerSpawn(player), true);
    }

    /** Handles haste giving on death */
    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Player player = event.getPlayer();
        ArenaManager manager = plugin.getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            return;
        }
        
        if (playerArena.getTeam(player.getUniqueId()).equals("no team")) {
            return;
        }
        
        // Re-give haste if player using architect with haste
        JSONObject json = plugin.getJSON().getPlayerPreset(player.getUniqueId());
        if (json.has("haste")) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() != Material.IRON_PICKAXE) {
                return;
            }
            
            int level = json.getInt("haste");
            if (level <= 0) {
                return;
            }
            
            player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 30 * 60 * 20, level - 1));
        }
        
        int bunny = plugin.getJSON().getAbility(player.getUniqueId(), "bunny");
        if (bunny > 0) {
            int level = (int) ConfigUtils.getAbilityStat("Vanguard.passive.bunny", bunny, "amplifier");
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 30 * 60 * 20, level));
        }
        
        // Reset boomlust
        playerArena.getPlayerInArena(player.getUniqueId()).setBoomLustRegen(false);
    }
    
    public static Map<Player, Location> bowShots = new HashMap<>();
    
    // Handle sentinel longshot bow shoot event
    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        // Ensure we are handling a player in an arena
        if (event.getEntityType() != EntityType.PLAYER) {
            return;
        }
        Player player = (Player) event.getEntity();
        ArenaManager arenaManager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = arenaManager.getArena(player.getUniqueId());
        if ((arena == null) || !arena.isRunning()) {
            return;
        }
        
        if (MissileWarsPlugin.getPlugin().getJSON().getAbility(player.getUniqueId(), "longshot") > 0) {
            bowShots.put(player, player.getLocation());
        }
    }
    

    /** Handle friendly fire + other things */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        // Ensure we are handling a player in an arena
        if (event.getEntityType() != EntityType.PLAYER) {
            return;
        }
        Player player = (Player) event.getEntity();
        ArenaManager arenaManager = plugin.getArenaManager();
        Arena arena = arenaManager.getArena(player.getUniqueId());
        if ((arena == null) || !arena.isRunning()) {
            return;
        }

        // Check if player is damaged by a player
        Player damager = null;
        Boolean isProjectile = false;;
        if (event.getDamager().getType() == EntityType.PLAYER) {
            damager = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            // Do sentinel longshot checks
            Projectile projectile = (Projectile) event.getDamager();
            // Allow players to damage anyone with fireballs
            if (projectile.getType() == EntityType.FIREBALL) {
                // Check for boosterball and multiply knockback
                Fireball fb = (Fireball) projectile;
                if (!fb.isIncendiary()) {
                    double multiplier = Double.parseDouble(ConfigUtils.toPlain(fb.customName()));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Vector velocity = player.getVelocity();
                        double velx = velocity.getX() * multiplier;
                        double velz = velocity.getZ() * multiplier;
                        double vely = velocity.getY();
                        player.setVelocity(new Vector(velx, vely, velz));
                    }, 1);
                } 
                // Allow fb friendly fire
                return;
            }
            if (projectile.getShooter() instanceof Player) {
                damager = (Player) projectile.getShooter();
                isProjectile = true;
            }
        }
        if (damager == null) {
            return;
        }
        
        if (CustomItemListener.canopy_freeze.contains(damager)) {
            event.setCancelled(true);
            return;
        }

        // Stop event if damager and damaged are on same team
        if (arena.getTeam(player.getUniqueId()).equalsIgnoreCase(arena.getTeam(damager.getUniqueId())) &&
           !arena.getTeam(player.getUniqueId()).equalsIgnoreCase("no team")) {
            event.setCancelled(true);
            return;
        }
        
        // Do arrowhealth/longshot calculations
        if (isProjectile) {
            // Do sentinel longshot checks
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                // Longshot calculations
                if (projectile.getType() == EntityType.ARROW) {
                    if (bowShots.containsKey(damager)) {
                        int longshot = plugin.getJSON().getAbility(damager.getUniqueId(), "longshot");
                        double plus = ConfigUtils.getAbilityStat("Sentinel.passive.longshot", longshot, "plus");
                        double minus = ConfigUtils.getAbilityStat("Sentinel.passive.longshot", longshot, "minus");
                        double max = ConfigUtils.getAbilityStat("Sentinel.passive.longshot", longshot, "max");
                        double dmg = event.getDamage();
                        double distance = bowShots.get(damager).distance(player.getLocation());
                        double cutoff = 25;
                        
                        // Calculate damage
                        if (distance >= cutoff) {
                            double extradistance = distance - cutoff;
                            double extradmg = Math.min(extradistance * plus, max);
                            event.setDamage(dmg + extradmg);
                        } else {
                            double shortdistance = cutoff - distance;
                            double shortdmg = shortdistance * minus;
                            event.setDamage(Math.max(dmg - shortdmg, 1));
                        }

                        plugin.log("Longshot: " + plus + " " + minus + " " + max + " " + dmg + " " + distance + " " + event.getDamage());
                    }

                    // Arrowhealth message
                    if (player.getHealth() - event.getFinalDamage() > 0 && !player.equals(damager)) {
                        DecimalFormat df = new DecimalFormat("0.0");
                        df.setRoundingMode(RoundingMode.UP);
                        String health = df.format(player.getHealth() - event.getFinalDamage());
                        String msg = ConfigUtils.getConfigText("messages.arrow-damage", damager, arena, player);
                        msg = msg.replace("%health%", health);
                        damager.sendMessage(msg);
                    }
                } 
                // Check for prickly projectiles
                else if (projectile.getType() == EntityType.EGG || 
                         projectile.getType() == EntityType.SNOWBALL || 
                         projectile.getType() == EntityType.ENDER_PEARL) {
                    
                    // Cancel ender pearl damage (or something, idk oldcombatmechanics did this so)
                    if (event.getDamage() != 0.0) {
                        return;
                    }
                    
                    // Check for the passive
                    int prickly = plugin.getJSON().getAbility(damager.getUniqueId(), "prickly");
                    if (prickly == 0) {
                        return;
                    }

                    ThrowableProjectile proj = (ThrowableProjectile) projectile;
                    ItemStack item = proj.getItem();
                    
                    // Make sure its custom item
                    if ((item.getItemMeta() == null) || !item.getItemMeta().getPersistentDataContainer().has(
                            new NamespacedKey(plugin, "item-structure"), PersistentDataType.STRING)) {
                        return;
                    }
                    
                    // Get level of the item
                    String itemString = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,
                            "item-structure"), PersistentDataType.STRING);
                    String[] args = itemString.split("-");
                    int multiplier = Integer.parseInt(args[1]);
                    
                    // Set custom damage and knockback
                    event.setDamage(0.0001);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Vector velocity = player.getVelocity();
                        double velx = velocity.getX() * multiplier;
                        double velz = velocity.getZ() * multiplier;
                        double vely = velocity.getY();
                        player.setVelocity(new Vector(velx, vely, velz));
                    }, 1);
                    
                    // Give item back if level is 1
                    if (prickly > 1) {
                        damager.getInventory().addItem(item);
                    }
                }
            }
        }
    }

    /** Make sure players can't create portals */
    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {

        // Ensure it was in an arena world
        String possibleArenaName = event.getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }

        event.setCancelled(true);
    }

    /** Make sure players can't teleport with ender pearls */
    @EventHandler
    public void onPearl(PlayerTeleportEvent event) {

        // Ensure it's an ender pearl
        if (event.getCause() != TeleportCause.ENDER_PEARL) {
            return;
        }

        // Ensure it was in an arena world
        String possibleArenaName = event.getPlayer().getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }

        event.setCancelled(true);
    }

    /** Handle fireball and TNT explosions. */
    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        // Ensure it was in an arena world
        String possibleArenaName = event.getEntity().getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }

        // Check for shield breaks
        event.blockList().forEach(block -> {
            // Register shield breaking
            possibleArena.registerShieldBlockEdit(block.getLocation(), false);
        });

        EntityType entity = event.getEntityType();

        // Ensure its actually a fireball
        if (entity == EntityType.FIREBALL) {
            // Check for boosterball. If so, prevent block damage.
            Fireball fb = (Fireball) event.getEntity();
            if (!fb.isIncendiary()) {
                event.blockList().clear();
            }
            // Otherwise, don't allow portals to be broken
            else {
                event.blockList().removeIf(block -> block.getType() == Material.NETHER_PORTAL);
            }
        }
        // Check for TNT explosions of portals
        else if (entity == EntityType.PRIMED_TNT || entity == EntityType.MINECART_TNT ||
                 entity == EntityType.CREEPER) {
            event.blockList().forEach(block -> {
                // Register portal brake if block was broken
                if (block.getType() == Material.NETHER_PORTAL) {
                    possibleArena.registerPortalBreak(block.getLocation());
                }
            });
        }
    }

    /** Handle shield block breaks breaks. */
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        // Ensure it was in an arena world
        String possibleArenaName = block.getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }
        
        // Fix dumb bug. No break obsidian
        if (block.getType().toString().contains("OBSIDIAN")) {
            event.setCancelled(true);
            return;
        }

        // Register block break
        possibleArena.registerShieldBlockEdit(block.getLocation(), false);
        
        // Check for deconstructor
        if (player.getInventory().getItemInMainHand().getType() != Material.IRON_PICKAXE) {
            return;
        }
        
        String type = block.getType().toString();
        if (type.contains("END_STONE") || type.contains("LEAVES") || type.contains("GLASS")) {
            return;
        }
        
        int deconstructor = MissileWarsPlugin.getPlugin().getJSON().getAbility(player.getUniqueId(), "deconstructor");
        if (deconstructor > 0) {
            Random random = new Random();
            double percentage = ConfigUtils.getAbilityStat("Architect.passive.deconstructor", deconstructor, "percentage") / 100;
            if (random.nextDouble() < percentage) {
                ItemStack item = new ItemStack(block.getType());
                possibleArena.getWorld().dropItemNaturally(block.getLocation(), item);
            }
        }
    }

    /** Stop chickens spawning from eggs */
    @EventHandler
    public void onEgg(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == SpawnReason.EGG) {
            event.setCancelled(true);
        }
    }
    
    /** Experimental setting to give players poison if they go too high.
     *  Also handle priest passive
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Player player = event.getPlayer();
        
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        
        ArenaManager arenaManager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = arenaManager.getArena(player.getUniqueId());
        if (arena == null) {
            return;
        }
        
        if (arena.getTeam(player.getUniqueId()).equals("no team")) {
            return;
        }
        
        // Straight up kill people
        double tooLobby = plugin.getConfig().getDouble("barrier.center.x");
        double wayTooHigh = plugin.getConfig().getDouble("max-height");
        if (event.getTo().getBlockY() >= wayTooHigh || event.getTo().getBlockX() >= tooLobby) {
            ConfigUtils.sendConfigMessage("messages.out-of-bounds-death", player, null, null);
            player.setHealth(0);
            return;
        }
        
        // Experimental poison 
        if (plugin.getConfig().getBoolean("experimental.poison")) {

            double toohigh = ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), "too-high");
            
            if (event.getFrom().getBlockY() <= toohigh - 1 && event.getTo().getBlockY() >= toohigh) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.getLocation().getBlockY() >= toohigh) {
                        ConfigUtils.sendConfigMessage("messages.poison", player, null, null);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 60 * 30, 1, false));
                    }
                }, 10);
                return;
            }
            
            if (event.getTo().getBlockY() <= toohigh - 1 && event.getFrom().getBlockY() >= toohigh) {
                player.removePotionEffect(PotionEffectType.POISON);
                return;
            }
        }
    }
    
    // Kick afk players from arena
    @EventHandler
    public void onAFK(AfkStatusChangeEvent event) {
        if (!event.getValue()) {
            return;
        }
        
        Player player = event.getAffected().getBase();
        
        ArenaManager arenaManager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = arenaManager.getArena(player.getUniqueId());
        if (arena == null) {
            return;
        }
        
        if (arena.getTeam(player.getUniqueId()).equals("no team")) {
            return;
        }
        
        MissileWarsPlayer mwPlayer = arena.getPlayerInArena(player.getUniqueId());
        if (arena.isSpectating(mwPlayer)) {
            return;
        }
        
        arena.announceMessage("messages.afk-removal", mwPlayer);
        arena.removePlayer(player.getUniqueId(), true);
    }
}
