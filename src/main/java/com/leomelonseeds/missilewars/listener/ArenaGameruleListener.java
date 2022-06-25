package com.leomelonseeds.missilewars.listener;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.json.JSONObject;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

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
        Player player = event.getPlayer();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            return;
        }
        
        // Re-give haste if player using architect with haste
        JSONObject json = MissileWarsPlugin.getPlugin().getJSON().getPlayerPreset(player.getUniqueId());
        if (!json.has("haste")) {
            return;
        }
        
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

    /** Handle friendly fire. */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
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

        // Check if player is damaged by a player
        Player damager = null;
        if (event.getDamager().getType() == EntityType.PLAYER) {
            damager = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                damager = (Player) projectile.getShooter();
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
            // Remove all portals from block list
            event.blockList().removeIf(block -> block.getType() == Material.NETHER_PORTAL);
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
    }

    /** Handle shield block breaks places. */
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        // Ensure it was in an arena world
        String possibleArenaName = block.getWorld().getName().replace("mwarena_", "");
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(possibleArenaName);
        if (possibleArena == null) {
            return;
        }
        
        // Ain't no way bruh
        if (block.getLocation().getBlockY() > MissileWarsPlugin.getPlugin().getConfig().getInt("max-height")) {
            ConfigUtils.sendConfigMessage("messages.cannot-place-structure", event.getPlayer(), null, null);
            event.setCancelled(true);
            return;
        }

        // Register block break
        possibleArena.registerShieldBlockEdit(block.getLocation(), true);
    }

    /** Stop chickens spawning from eggs */
    @EventHandler
    public void onEgg(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == SpawnReason.EGG) {
            event.setCancelled(true);
        }
    }
    
    /** Experimental setting to give players poison if they go too high */
    @EventHandler
    public void givePoison(PlayerMoveEvent event) {
        if (!MissileWarsPlugin.getPlugin().getConfig().getBoolean("experimental.poison")) {
            return;
        }
        
        Player player = event.getPlayer();
        
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        
        ArenaManager arenaManager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = arenaManager.getArena(player.getUniqueId());
        if (arena == null) {
            return;
        }
        
        double toohigh = ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), "too-high");
        
        if (event.getFrom().getBlockY() <= toohigh - 1 && event.getTo().getBlockY() >= toohigh) {
            Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
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
