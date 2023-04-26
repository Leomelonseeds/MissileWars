package com.leomelonseeds.missilewars.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.decks.DeckItem;
import com.leomelonseeds.missilewars.invs.MapVoting;
import com.leomelonseeds.missilewars.schematics.SchematicManager;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Class to handle events for structure items. */
public class CustomItemListener implements Listener {
    
    /**
     * Simply get level from name
     * 
     * @param name
     * @return
     */
    private double getItemStat(String name, String stat) {
        String[] args = name.split("-");
        return Double.valueOf(ConfigUtils.getItemValue(args[0], Integer.parseInt(args[1]), stat) + "");
    }

    /**
     * Check if a given player is on the red team.
     *
     * @param player the player
     * @return true if they are on the red team, false otherwise (including if they are not on any team at all)
     */
    private boolean isRedTeam(Player player) {
        // Find player's team (Default to blue)
        boolean redTeam = false;
        Arena arena = getPlayerArena(player);
        if (arena != null) {
            redTeam = arena.getTeam(player.getUniqueId()).equalsIgnoreCase("red");
        }
        return redTeam;
    }

    /**
     * Get the Arena the current player is in.
     *
     * @param player the player
     * @return the Arena the player is in
     */
    private Arena getPlayerArena(Player player) {
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        return arena;
    }
    
    /**
     * Tells the player deck that they consumed an item
     * 
     * @param player
     * @param arena
     * @param item
     * @param deplete should the item manually be depleted. If false, the item automatically depletes
     */
    public static void consumeItem(Player player, Arena arena, ItemStack item, boolean deplete) {
        MissileWarsPlayer mwp = arena.getPlayerInArena(player.getUniqueId());
        if (mwp == null) {
            return;
        }
        
        Deck deck = mwp.getDeck();
        if (deck == null) {
            return;
        }

        int amt = item.getAmount();
        DeckItem di = deck.getDeckItem(item);
        if (di == null) {
            if (deplete) {
                item.setAmount(amt - 1);
            }
            return;
        }
        
        boolean makeUnavailable = false;
        if (amt == 1) {
            if (!deplete) {
                item.setAmount(2);
            }
            makeUnavailable = true;
        } else if (deplete) {
            item.setAmount(amt - 1);
        }
        
        di.consume(makeUnavailable);
        player.updateInventory();
    }
    
    /** Give architect pickaxes the haste effect */
    @EventHandler
    public void giveHaste(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        
        if (getPlayerArena(player) == null) {
            return;
        }
        
        // Makes sure player is using architect
        JSONObject json = MissileWarsPlugin.getPlugin().getJSON().getPlayerPreset(player.getUniqueId());
        if (!json.has("haste")) {
            return;
        }
        
        // Clear haste if switching off from pickaxe
        ItemStack prev = player.getInventory().getItem(event.getPreviousSlot());
        if (prev != null && prev.getType() == Material.IRON_PICKAXE) {
            if (player.hasPotionEffect(PotionEffectType.FAST_DIGGING)) {
                player.removePotionEffect(PotionEffectType.FAST_DIGGING);
            }
            return;
        }
        
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null || item.getType() != Material.IRON_PICKAXE) {
            return;
        }
        
        int level = json.getInt("haste");
        if (level <= 0) {
            return;
        }
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 30 * 60 * 20, level * 2 - 1));
    }
    
    public static ArrayList<Player> cooldown = new ArrayList<>();

    /** Handle right clicking missiles and utility items */
    @EventHandler
    public void useItem(PlayerInteractEvent event) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        // Stop if not right-click
        if (!event.getAction().toString().contains("RIGHT")) {
            return;
        }
        
        // Check if player is trying to place a structure item
        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack hand = inv.getItem(event.getHand());
        if (player.hasCooldown(hand.getType())) {
            event.setCancelled(true);
            return;
        }
        
        // Check arena
        Arena playerArena = getPlayerArena(player);
        String held = ConfigUtils.getStringFromItem(hand, "held");
        if (playerArena == null) {
            // Player is using a held item
            if (held == null) {
                return;
            }
            
            // Must be holding main menu item
            if (!held.equals("main-menu")) {
                return;
            }
            
            event.setCancelled(true);
            String command = "bossshop open menu " + player.getName();
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
            return;
        }
        
        // Player is using a held item inside an arena
        if (held != null) {
            event.setCancelled(true);
            if (held.equals("leave")) {
                playerArena.removePlayer(player.getUniqueId(), true);
            } else if (held.equals("votemap")) {
                new MapVoting(player);
            }
            return;
        }
        
        // Check if gamemode survival
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        
        // Check if player is frozen by a canopy
        if (canopy_freeze.contains(player)) {
            event.setCancelled(true);
            return;
        }
        
        // Check if player used a structure or utility
        Block clicked = event.getClickedBlock();
        String structureName = ConfigUtils.getStringFromItem(hand, "item-structure");
        String utility = ConfigUtils.getStringFromItem(hand, "item-utility");
        if (structureName == null && utility == null) {
            return;
        }
        
        // Check if game is waiting for tie
        if (playerArena.isWaitingForTie()) {
            event.setCancelled(true);
            return;
        }
        
        // Disable if player just died
        int sinceDeath = player.getStatistic(Statistic.TIME_SINCE_DEATH);
        int respawnDisable = plugin.getConfig().getInt("respawn-disable");
        if (sinceDeath <= respawnDisable) {
            event.setCancelled(true);
            return;
        }
        
        // Spawn a structure item
        if (structureName != null) {
            // Switch to throwing logic if using a throwable
            if (structureName.contains("shield-") || structureName.contains("platform-") || structureName.contains("torpedo-")) {
                consumeItem(player, playerArena, hand, false);
                return;
            }
            
            // We can handle canopies now!
            if (structureName.contains("canopy")) {
                event.setCancelled(true);
                if (canopy_cooldown.contains(player.getUniqueId())) {
                    return;
                }
                if (!player.isOnGround()) {
                    ConfigUtils.sendConfigMessage("messages.canopy-fail", player, null, null);
                    return;
                }
                ConfigUtils.sendConfigMessage("messages.canopy-activate", player, null, null);
                canopy_cooldown.add(player.getUniqueId());
                Bukkit.getScheduler().runTaskLater(plugin, () -> spawnCanopy(player, playerArena, structureName, hand), 20L); 
                return;
            }
            
            
            // Check if a block was clicked, including a moving block
            if (event.getAction() == Action.RIGHT_CLICK_AIR) {
                for (int i = 1; i <= 4; i++) {
                    Block temp = player.getTargetBlock(i);
                    if (temp.getType() == Material.MOVING_PISTON) {
                        clicked = temp;
                        break;
                    }
                }
            }
            if (clicked == null) {
                return;
            }
            
            event.setCancelled(true);

            // Place structure
            String mapName = "default-map";
            if (playerArena.getMapName() != null) {
                mapName = playerArena.getMapName();
            }
            
            // 0.5s cooldown
            if (cooldown.contains(player)) {
                ConfigUtils.sendConfigMessage("messages.missile-cooldown", player, null, null);
                return;
            }
            
            if (SchematicManager.spawnNBTStructure(player, structureName, clicked.getLocation(), isRedTeam(player), mapName, true, true)) {
                consumeItem(player, playerArena, hand, true);
                playerArena.getPlayerInArena(player.getUniqueId()).incrementMissiles();
                ConfigUtils.sendConfigSound("spawn-missile", player);
                // 0.5s cooldown
                cooldown.add(player);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        cooldown.remove(player);
                    }
                }.runTaskLater(plugin, plugin.getConfig().getInt("experimental.missile-cooldown"));
            } else {
                ConfigUtils.sendConfigMessage("messages.cannot-place-structure", player, null, null);
            }
            return;
        }
        
        // Spawn a utility item. At this point we know the item MUST have a utility tag
        else {
            // Make sure we allow gear items to be used
            if (utility.contains("bow") || utility.contains("sword") || utility.contains("pickaxe")) {
                return;
            }

            if (event.getAction().toString().contains("RIGHT_CLICK_BLOCK")) {
                if (utility.contains("creeper")) {
                    event.setCancelled(true);

                    // Can't place creepers on obsidian, otherwise broken game
                    List<String> cancel = plugin.getConfig().getStringList("cancel-schematic");
                    for (String s : cancel) {
                        if (event.getClickedBlock().getType() == Material.getMaterial(s)) {
                            ConfigUtils.sendConfigMessage("messages.cannot-place-structure", player, null, null);
                            return;
                        }
                    }
                    
                    Location spawnLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
                    Creeper creeper = (Creeper) spawnLoc.getWorld().spawnEntity(spawnLoc.toCenterLocation().add(0, -0.5, 0), EntityType.CREEPER);
                    if (utility.contains("2")) {
                        creeper.setPowered(true);
                    }
                    creeper.customName(ConfigUtils.toComponent(ConfigUtils.getFocusName(player) + "'s &7Creeper"));
                    creeper.setCustomNameVisible(true);
                    consumeItem(player, playerArena, hand, true);
                    return;
                }
            }

            // Spawn a fireball
            if (utility.contains("fireball")) {
                event.setCancelled(true);
                Fireball fireball = (Fireball) player.getWorld().spawnEntity(player.getEyeLocation().clone().add(player
                        .getEyeLocation().getDirection()), EntityType.FIREBALL);
                // Check for boosterball passive. Store passive by setting incendiary value
                int boosterball = plugin.getJSON().getAbility(player.getUniqueId(), "boosterball");
                if (boosterball > 0) {
                    double multiplier = ConfigUtils.getAbilityStat("Berserker.passive.boosterball", boosterball, "multiplier");
                    fireball.setIsIncendiary(false);
                    fireball.customName(ConfigUtils.toComponent(multiplier + ""));
                } else {
                    fireball.setIsIncendiary(true);
                }
                float yield = (float) getItemStat(utility, "power");
                fireball.setYield(yield);
                fireball.setDirection(player.getEyeLocation().getDirection());
                fireball.setShooter(player);
                consumeItem(player, playerArena, hand, true);
                for (Player players : player.getWorld().getPlayers()) {
                     ConfigUtils.sendConfigSound("spawn-fireball", players, player.getLocation());
                }
                playerArena.getPlayerInArena(player.getUniqueId()).incrementUtility();
                return;
            }
            
            // Otherwise another utility was used, tell deck item to consume as well
            consumeItem(player, playerArena, hand, false);
        }
    }

    public static Set<UUID> canopy_cooldown = new HashSet<>();
    public static Map<Location, Integer> leaves = new HashMap<>();

    // Check for architect leaves to despawn them after a while
    @EventHandler
    public void architectLeaves(BlockPlaceEvent event) {
        
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();

        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        if (canopy_freeze.contains(player)) {
            event.setCancelled(true);
            return;
        }

        Arena playerArena = getPlayerArena(player);
        ItemStack item = event.getItemInHand();
        if ((playerArena == null) || !item.getType().toString().contains("LEAVES")) {
            return;
        }
        
        Location loc = block.getLocation();
        
        // no max height
        if (loc.getBlockY() > plugin.getConfig().getInt("max-height")) {
            ConfigUtils.sendConfigMessage("messages.cannot-place-structure", event.getPlayer(), null, null);
            event.setCancelled(true);
            return;
        }
        
        Location s1 = playerArena.getBlueTeam().getSpawn().getBlock().getLocation();
        Location s2 = playerArena.getRedTeam().getSpawn().getBlock().getLocation();
        
        // Stop spawngriefing
        if (loc.equals(s1) || loc.equals(s2)) {
            event.setCancelled(true);
            ConfigUtils.sendConfigMessage("messages.cannot-place-structure", player, null, null);
            return;
        }

        // Register block place
        playerArena.registerShieldBlockEdit(block.getLocation(), true);
        
        // Check passives
        int naturesblessing = plugin.getJSON().getAbility(player.getUniqueId(), "naturesblessing");
        int repairman = plugin.getJSON().getAbility(player.getUniqueId(), "repairman");
        int durationMultiplier = 1;
        String team = playerArena.getTeam(player.getUniqueId());
        if (naturesblessing > 0) {
            durationMultiplier = (int) ConfigUtils.getAbilityStat("Architect.passive.naturesblessing", naturesblessing, "multiplier");
        } else if (repairman > 0) {
            if (ConfigUtils.inShield(playerArena, loc, team, 6)) {
                double percentage = ConfigUtils.getAbilityStat("Architect.passive.repairman", repairman, "percentage") / 100;
                Random random = new Random();
                if (random.nextDouble() < percentage) {
                    item.setAmount(item.getAmount() + 1);
                }
            }
        }

        consumeItem(player, playerArena, item, false);
        
        // Remove leaves after 30 sec
        new BukkitRunnable() {
            @Override
            public void run() {
                // Must still be leaves
                if (!loc.getBlock().getType().toString().contains("LEAVES")) {
                    return;
                }
                
                // No repairman/outside base = remove leaves
                if (repairman == 0 || !ConfigUtils.inShield(playerArena, loc, team, 6)) {
                    loc.getBlock().setType(Material.AIR);
                    return;
                }
                
                // Replace blocks with endstone if repairman
                loc.getBlock().setType(Material.END_STONE);
                
            }
        }.runTaskLater(plugin, 30 * 20 * durationMultiplier);
    }

    private void spawnCanopy(Player player, Arena playerArena, String utility, ItemStack hand) {
        // Make sure the canopy spawn hasn't already been cancelled
        if (!canopy_cooldown.contains(player.getUniqueId())) {
            return;
        }
    
        canopy_cooldown.remove(player.getUniqueId());
    
        // Ignore offline players. Obviously
        if (!player.isOnline()) {
            return;
        }
    
        String mapName = "default-map";
        if (playerArena.getMapName() != null) {
            mapName = playerArena.getMapName();
        }
    
        int canopy_distance = (int) getItemStat(utility, "distance");
    
        // Ignore if player would be going through wall
        if (player.getTargetBlock(null, canopy_distance + 3).getType() != Material.AIR) {
            ConfigUtils.sendConfigMessage("messages.canopy-blocked", player, null, null);
            return;
        }
    
        // Finally spawn canopy
        Vector distance = player.getEyeLocation().getDirection().multiply(canopy_distance);
        Location spawnLoc = player.getEyeLocation().clone().add(distance);
        if (!SchematicManager.spawnNBTStructure(player, "canopy-1", spawnLoc, isRedTeam(player), mapName, false, true)) {
            ConfigUtils.sendConfigMessage("messages.cannot-place-structure", player, null, null);
            return;
        }
            
        // Teleport and give slowness
        Location loc = spawnLoc.toCenterLocation().add(0, -0.5, 0);
        loc.setPitch(90);
        player.teleport(loc);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, 5));
        
        // Freeze player for a bit
        canopy_freeze.add(player);
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            canopy_freeze.remove(player);
        }, 30);
        
        // Check if can give poison
        double toohigh = ConfigUtils.getMapNumber(playerArena.getGamemode(), playerArena.getMapName(), "too-high");
        if (loc.getBlockY() >= toohigh) {
            ConfigUtils.sendConfigMessage("messages.poison", player, null, null);
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 60 * 30, 1, false));
        }
        
        consumeItem(player, playerArena, hand, true);
        for (Player players : player.getWorld().getPlayers()) {
            ConfigUtils.sendConfigSound("spawn-canopy", players, spawnLoc);
        }
        playerArena.getPlayerInArena(player.getUniqueId()).incrementUtility();
        despawnCanopy(spawnLoc, 5);
    }

    public static Set<Player> canopy_freeze = new HashSet<>();
    Map<Location, Integer> canopy_extensions = new HashMap<>();

    private void despawnCanopy(Location loc, int duration) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Location wood = loc.clone().add(0, -1, 0).getBlock().getLocation();
                if (wood.getBlock().getType() == Material.OAK_WOOD) {
                    if (canopy_extensions.containsKey(wood)) {
                        despawnCanopy(loc, canopy_extensions.get(wood));
                        canopy_extensions.remove(wood);
                        return;
                    }
                    wood.getBlock().setType(Material.AIR);
                }
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), duration * 20L);
    }

    /** Cancel canopy activation if player jumps falls or something */
    @EventHandler
    public void canopyCancel(PlayerMoveEvent e) {

        Player player = e.getPlayer();
        
        // Stop players from moving a second after canopy spawn
        if (canopy_freeze.contains(player)) {
            e.setCancelled(true);
            return;
        }

        if (!canopy_cooldown.contains(player.getUniqueId())) {
            return;
        }

        if (!player.isOnGround()) {
            canopy_cooldown.remove(player.getUniqueId());
            ConfigUtils.sendConfigMessage("messages.canopy-cancel", player, null, null);
        }
    }

    /** Handle projectile items structure creation */
    @EventHandler
    public void useProjectile(ProjectileLaunchEvent event) {
        // Ensure we are tracking a utility thrown by a player
        if (!(event.getEntity().getType() == EntityType.SNOWBALL ||
              event.getEntity().getType() == EntityType.EGG ||
              event.getEntity().getType() == EntityType.ENDER_PEARL)) {
            return;
        }
        ThrowableProjectile thrown = (ThrowableProjectile) event.getEntity();
        
        if (!(thrown.getShooter() instanceof Player)) {
            return;
        }
        
        Player thrower = (Player) thrown.getShooter();
        
        if (canopy_freeze.contains(thrower)) {
            event.setCancelled(true);
            return;
        }
        
        Arena playerArena = getPlayerArena(thrower);
        if (playerArena == null) {
            return;
        }

        // Check if player is holding a structure item
        ItemStack hand = thrown.getItem();
        String structureName = ConfigUtils.getStringFromItem(hand, "item-structure");
        if (structureName == null) {
            return;
        }

        // Add meta for structure identification
        thrown.customName(Component.text(structureName));

        // Schedule structure spawn after 1 second if snowball is still alive
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!thrown.isDead() && thrower.isOnline()) {
                    spawnUtility(structureName, thrower, thrown, playerArena);
                }
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), 20);
    }

    /** Utility should go through players */
    @EventHandler
    public void handleUtilityCollisions(ProjectileHitEvent event) {
        Projectile thrown = event.getEntity();
        // Make sure a player threw this projectile
        // If it has a custom name, it definitely is a missile wars item
        if (!(thrown.getShooter() instanceof Player)) {
            return;
        }
        
        // Make sure projectile hit an entity
        if (event.getHitEntity() == null) {
            return;
        }

        // Make sure it's in an arena
        Player thrower = (Player) thrown.getShooter();
        Arena playerArena = getPlayerArena(thrower);
        if (playerArena == null) {
            return;
        }
        
        // We're only handling utility collisions against players
        if (event.getHitEntity().getType() != EntityType.PLAYER) {
            return;
        }
        
        // Always disallow projectiles to collide with players of same team
        Player hit = (Player) event.getHitEntity();
        String team1 = playerArena.getTeam(thrower.getUniqueId());
        String team2 = playerArena.getTeam(hit.getUniqueId());
        if (!team1.equals("no team") && team1.equals(team2)) {
            event.setCancelled(true);
            return;
        }
        
        // Here, players must be on different teams
        // Allow collisions if arrow or fireball
        if (event.getEntityType() == EntityType.ARROW || event.getEntityType() == EntityType.FIREBALL) {
            return;
        }
        
        // Allow collisions if prickly projectiles
        if (MissileWarsPlugin.getPlugin().getJSON().getAbility(thrower.getUniqueId(), "prickly") > 0) {
            return;
        }

        // Otherwise, also cancel all collisions
        event.setCancelled(true);
    }

    /** Handle spawning of utility structures */
    public void spawnUtility(String structureName, Player thrower, ThrowableProjectile thrown, Arena playerArena) {
        Location spawnLoc = thrown.getLocation();
        String mapName = "default-map";
        ItemStack item = thrown.getItem();
        if (playerArena.getTeam(thrower.getUniqueId()) == "no team") {
            return;
        }
        
        if (playerArena.getMapName() != null) {
            mapName = playerArena.getMapName();
        }
        if (SchematicManager.spawnNBTStructure(thrower, structureName, spawnLoc, isRedTeam(thrower), mapName, false, true)) {
            playerArena.getPlayerInArena(thrower.getUniqueId()).incrementUtility();
            String sound = "none";
            if (structureName.contains("obsidianshield")) {
                sound = "spawn-obsidian-shield";
                // Detect obsidian shield duration in seconds here!
                int duration = (int) getItemStat(structureName, "duration");
                clearObsidianShield(duration, spawnLoc, isRedTeam(thrower), mapName, playerArena);
            } else if (structureName.contains("shield-") || structureName.contains("platform")) {
                sound = "spawn-shield";
            } else if (structureName.contains("torpedo")) {
                sound = "spawn-torpedo";
                // Register all spawned TNT minecarts into the tracker
                for (Entity e : spawnLoc.getNearbyEntities(2, 2, 2)) {
                    if (e.getType() == EntityType.MINECART_TNT) {
                        playerArena.getTracker().registerTNTMinecart((ExplosiveMinecart) e, thrower);
                    }
                }
            } 
            for (Player players : thrower.getWorld().getPlayers()) {
                ConfigUtils.sendConfigSound(sound, players, spawnLoc);
            }
            // Repairman
            int repairman = MissileWarsPlugin.getPlugin().getJSON().getAbility(thrower.getUniqueId(), "repairman");
            if (repairman > 0 && ConfigUtils.inShield(playerArena, spawnLoc, playerArena.getTeam(thrower.getUniqueId()), 5)) {
                double percentage = ConfigUtils.getAbilityStat("Architect.passive.repairman", repairman, "percentage") / 100;
                Random random = new Random();
                if (random.nextDouble() < percentage) {
                    Item newitem = thrower.getWorld().dropItem(thrower.getLocation(), item);
                    newitem.setPickupDelay(0);
                }
            }
        } else {
            ConfigUtils.sendConfigMessage("messages.cannot-place-structure", thrower, null, null);
            Item newitem = thrower.getWorld().dropItem(thrower.getLocation(), item);
            newitem.setPickupDelay(0);
        }
        if (!thrown.isDead()) {
            thrown.remove();
        }
    }

    /** Handle projectile item utility creation */
    @EventHandler
    public void throwSplash(ProjectileLaunchEvent event) {

        if (event.getEntity().getType() != EntityType.SPLASH_POTION) {
            return;
        }

        ThrownPotion thrown = (ThrownPotion) event.getEntity();
        if (!(thrown.getShooter() instanceof Player)) {
            return;
        }
        Player thrower = (Player) thrown.getShooter();
        Arena playerArena = getPlayerArena(thrower);
        if (playerArena == null) {
            return;
        }

        // Check if player is holding a utility item
        ItemStack hand = thrown.getItem();
        String utility = ConfigUtils.getStringFromItem(hand, "item-utility");

        // Make sure it's splash potion of water
        if (utility == null || !thrown.getEffects().isEmpty()) {
            return;
        }

        // Check the duration here
        double duration = getItemStat(utility, "duration");
        int extend = (int) getItemStat(utility, "extend");
        thrown.customName(Component.text("splash:" + duration + ":" + extend));
        playerArena.getPlayerInArena(thrower.getUniqueId()).incrementUtility();
    }

    /** Handle spawning of splash waters */
    @EventHandler
    public void handleSplash(ProjectileHitEvent event) {

        // Make sure we're getting the right potion here
        if ((event.getEntityType() != EntityType.SPLASH_POTION) || (event.getEntity().customName() == null)) {
            return;
        }
        
        String customName = PlainTextComponentSerializer.plainText().serialize(event.getEntity().customName());
        if (!customName.contains("splash:")) {
            return;
        }

        // Spawn some water
        Block hitBlock = event.getHitBlock();
        if (hitBlock == null) {
            return;
        }

        // Get data from item
        String[] args = customName.split(":");
        Player thrower = (Player) event.getEntity().getShooter();

        // Handle hitting oak_wood to fully repair canopies
        if (hitBlock.getType() == Material.OAK_WOOD) {
            if (event.getEntity().getShooter() instanceof Player) {
                int extraduration = Integer.parseInt(args[2]);
                Location key = hitBlock.getLocation();
                if (canopy_extensions.containsKey(key)) {
                    canopy_extensions.put(key, canopy_extensions.get(key) + extraduration);
                } else {
                    canopy_extensions.put(key, extraduration);
                }
                Location newSpawn = hitBlock.getLocation().add(0, 1, 0);
                // map name doesn't matter here because the canopy has already been spawned,
                // we therefore know that the structure was placed successfully and do not need
                // to perform validity checks based on the map
                SchematicManager.spawnNBTStructure(null, "canopy-1", newSpawn, isRedTeam(thrower), "default-map", false, false);
                thrower.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7This canopy will now last &a" +
                            extraduration + " &7seconds longer."));
            }
            return;
        }

        BlockFace face = event.getHitBlockFace();
        Location location = event.getHitBlock().getRelative(face).getLocation();

        double duration = Double.parseDouble(args[1]);
        int lavasplash = MissileWarsPlugin.getPlugin().getJSON().getAbility(thrower.getUniqueId(), "lavasplash");
        if (lavasplash <= 0) {
            if (location.getBlock().getType() != Material.AIR) {
                return;
            }
            location.getBlock().setType(Material.WATER);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (location.getBlock().getType() == Material.WATER) {
                        location.getBlock().setType(Material.AIR);
                    }
                }
            }.runTaskLater(MissileWarsPlugin.getPlugin(), (long) (duration * 20));
            return;
        }
        
        // Lava splash manager
        double durationmultiplier = ConfigUtils.getAbilityStat("Vanguard.passive.lavasplash", lavasplash, "multiplier");
        int radius = (int) ConfigUtils.getAbilityStat("Vanguard.passive.lavasplash", lavasplash, "radius");
        List<Location> locations = new ArrayList<>();
        locations.add(location);
        if (radius == 1) {
            locations.add(location.clone().add(1, 0, 0));
            locations.add(location.clone().add(-1, 0, 0));
            locations.add(location.clone().add(0, 0, 1));
            locations.add(location.clone().add(0, 0, -1));
        }
        
        // Spawn and then remove lavasplash after a while
        for (Location l : locations) {
            if (l.getBlock().getType() != Material.AIR) {
                continue;
            }
            l.getBlock().setType(Material.LAVA);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (l.getBlock().getType() == Material.LAVA) {
                        l.getBlock().setType(Material.AIR);
                    }
                }
            }.runTaskLater(MissileWarsPlugin.getPlugin(), (long) (duration * 20 * durationmultiplier));
        }
    }

    /** Clears obsidian shield after a certain amount of time */
    private void clearObsidianShield(int t, Location location, Boolean red, String mapName, Arena playerArena) {

        int duration = t * 2;
        for (int i = duration; i > duration - 10; i--) {
            int finalDuration = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (playerArena.isRunning()) {
                        if (finalDuration == duration) {
                            SchematicManager.spawnNBTStructure(null, "obsidianshieldclear-1", location, red, mapName, false, false);
                            for (Player player : location.getWorld().getPlayers()) {
                                ConfigUtils.sendConfigSound("break-obsidian-shield", player, location);
                            }
                        } else if (finalDuration % 2 == 0) {
                            SchematicManager.spawnNBTStructure(null, "obsidianshielddeplete-1", location, red, mapName, false, false);
                        } else {
                            SchematicManager.spawnNBTStructure(null, "obsidianshield-1", location, red, mapName, false, false);
                        }
                    }
                }
            }.runTaskLater(MissileWarsPlugin.getPlugin(), i * 10L);
        }
    }
}
