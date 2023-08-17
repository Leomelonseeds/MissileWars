package com.leomelonseeds.missilewars.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.DragonFireball;
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
import org.bukkit.inventory.EquipmentSlot;
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
import com.leomelonseeds.missilewars.arenas.ClassicArena;
import com.leomelonseeds.missilewars.invs.MapVoting;
import com.leomelonseeds.missilewars.schematics.SchematicManager;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

/** Class to handle events for structure items. */
public class CustomItemListener implements Listener {
    
    public static Set<UUID> canopy_cooldown = new HashSet<>();
    public static Set<Player> canopy_freeze = new HashSet<>();
    Map<Location, Integer> canopy_extensions = new HashMap<>();
    public static Map<Location, Integer> leaves = new HashMap<>();

    
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
     * Consume a projectile by finding its appropriate item in player inventory
     * 
     * @param item
     * @param player
     * @param arena
     */
    private void projectileConsume(ItemStack item, Player player, Arena arena) {
        PlayerInventory inv = player.getInventory();
        int slot = inv.getHeldItemSlot();
        ItemStack main = inv.getItemInMainHand();
        if (main.isSimilar(item)) {
            InventoryUtils.consumeItem(player, arena, main, slot);
        } else {
            InventoryUtils.consumeItem(player, arena, inv.getItemInOffHand(), 40);
        }
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
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "bossshop open menu " + player.getName());
            return;
        }
        
        // Player is using a held item inside an arena
        if (held != null) {
            event.setCancelled(true);
            UUID uuid = player.getUniqueId();
            switch(held) {
            case "votemap":
                new MapVoting(player);
                break;
            case "to-lobby":
                playerArena.removePlayer(uuid, true);
                break;
            case "red":
            case "blue":
                playerArena.enqueue(uuid, held);
                break;
            case "spectate":
                MissileWarsPlayer mwp = playerArena.getPlayerInArena(uuid);
                if (playerArena.isSpectating(mwp)) {
                    playerArena.removeSpectator(mwp);
                } else {
                    playerArena.addSpectator(uuid); 
                }
                break;
            case "deck":
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "bossshop open decks " + player.getName());
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
        
        // Disable if player just died
        MissileWarsPlayer mwp = playerArena.getPlayerInArena(player.getUniqueId());
        if (mwp.justSpawned() && !hand.getType().toString().contains("BOW")) {
            event.setCancelled(true);
            return;
        }
        
        // Spawn a structure item
        if (structureName != null) {
            // Switch to throwing logic if using a throwable
            if (structureName.contains("shield-") || structureName.contains("platform-") || structureName.contains("torpedo-")) {
                return;
            }
            
            event.setCancelled(true);
            
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
                    Block temp = player.getTargetBlock(null, i);
                    if (temp != null && temp.getType() == Material.MOVING_PISTON) {
                        clicked = temp;
                        break;
                    }
                }
            }
            if (clicked == null) {
                return;
            }

            // Place structure
            String mapName = "default-map";
            if (playerArena.getMapName() != null) {
                mapName = playerArena.getMapName();
            }
            
            if (SchematicManager.spawnNBTStructure(player, structureName, clicked.getLocation(), isRedTeam(player), mapName, true, true)) {
                ConfigUtils.sendConfigSound("spawn-missile", player);
                // Missile cooldown
                if (player.getGameMode() != GameMode.CREATIVE) {
                    int cooldown = plugin.getConfig().getInt("experimental.missile-cooldown");
                    for (ItemStack i : player.getInventory().getContents()) {
                        if (i == null) continue;
                        Material material = i.getType();
                        if (!player.hasCooldown(material) && material.toString().contains("SPAWN_EGG")) {
                            player.setCooldown(material, cooldown);
                        }
                    }
                }
                InventoryUtils.consumeItem(player, playerArena, hand, -1);
                mwp.incrementMissiles();
            }
            return;
        }
        
        // Spawn a utility item. At this point we know the item MUST have a utility tag
        else {
            if (utility.contains("creeper")) {
                if (event.getAction().toString().contains("RIGHT_CLICK_BLOCK")) {
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
                    InventoryUtils.consumeItem(player, playerArena, hand, -1);
                    mwp.incrementUtility();
                }
                return;
            }

            // Spawn a fireball/dragon fireball
            if (utility.contains("fireball") || utility.contains("lingering")) {
                event.setCancelled(true);
                Fireball fireball;
                if (utility.contains("lingering")) {
                    Location spawnLoc = player.getEyeLocation().clone().add(player.getEyeLocation().getDirection());
                    fireball = (DragonFireball) player.getWorld().spawnEntity(spawnLoc, EntityType.DRAGON_FIREBALL);
                    int amplifier = (int) getItemStat(utility, "amplifier");
                    int duration = (int) getItemStat(utility, "duration");
                    fireball.customName(ConfigUtils.toComponent("vdf:" + amplifier + ":" + duration));
                    fireball.setCustomNameVisible(false);
                    Bukkit.getPluginManager().registerEvents(new DragonFireballHandler(fireball), plugin);
                } else {
                    fireball = (Fireball) player.getWorld().spawnEntity(player.getEyeLocation().clone().add(
                            player.getEyeLocation().getDirection()), EntityType.FIREBALL);
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
                }

                fireball.setDirection(player.getEyeLocation().getDirection());
                fireball.setShooter(player);
                InventoryUtils.consumeItem(player, playerArena, hand, -1);
                ConfigUtils.sendConfigSound("spawn-fireball", player.getLocation());
                mwp.incrementUtility();
                return;
            }
        }
    }

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
        
        // no max height
        Location loc = block.getLocation();
        if (loc.getBlockY() > plugin.getConfig().getInt("max-height")) {
            ConfigUtils.sendConfigMessage("messages.cannot-place-structure", event.getPlayer(), null, null);
            event.setCancelled(true);
            return;
        }
        
        // Stop spawngriefing
        Location s1 = playerArena.getBlueTeam().getSpawn().getBlock().getLocation();
        Location s2 = playerArena.getRedTeam().getSpawn().getBlock().getLocation();
        if (loc.equals(s1) || loc.equals(s2)) {
            event.setCancelled(true);
            ConfigUtils.sendConfigMessage("messages.cannot-place-structure", player, null, null);
            return;
        }

        // Register block place
        playerArena.registerShieldBlockEdit(block.getLocation(), true);
        InventoryUtils.consumeItem(player, playerArena, item, event.getHand() == EquipmentSlot.HAND ? 
                    player.getInventory().getHeldItemSlot() : 40);
        
        // Remove leaves after 30 sec
        new BukkitRunnable() {
            @Override
            public void run() {
                // Must still be leaves
                if (!loc.getBlock().getType().toString().contains("LEAVES")) {
                    return;
                }
                
                loc.getBlock().setType(Material.AIR);
            }
        }.runTaskLater(plugin, 30 * 20);
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
        
        InventoryUtils.consumeItem(player, playerArena, hand, -1);
        ConfigUtils.sendConfigSound("spawn-canopy", spawnLoc);
        playerArena.getPlayerInArena(player.getUniqueId()).incrementUtility();
        despawnCanopy(spawnLoc, 5);
    }
    
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
        EntityType type = event.getEntityType();
        if (!(type == EntityType.SNOWBALL || type == EntityType.EGG || type == EntityType.ENDER_PEARL)) {
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

        // Add meta for structure identification + pokemissiles
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        ItemStack offhand = thrower.getInventory().getItemInOffHand();
        UUID uuid = thrower.getUniqueId();
        int poke = plugin.getJSON().getAbility(uuid, "poke");
        if (poke > 0) {
            String offName = ConfigUtils.getStringFromItem(offhand, "item-structure");
            if (offName != null && !thrower.hasCooldown(offhand.getType()) && offhand.getType().toString().contains("SPAWN_EGG")) {
                structureName = offName + "-p"; // Add extra dash to represent a pokemissile
                InventoryUtils.consumeItem(thrower, playerArena, offhand, -1);
            } else {
                // Set poke to 0 so the particles won't activate
                poke = 0;
            }
        }
        projectileConsume(hand, thrower, playerArena);

        // Schedule structure spawn after 1 second if snowball is still alive
        boolean pokeActive = poke > 0;
        String structure = structureName;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (thrown.isDead() || !thrower.isOnline()) {
                return;
            }

            Location spawnLoc = thrown.getLocation();
            String mapName = playerArena.getMapName();
            ItemStack item = thrown.getItem();
            if (playerArena.getTeam(uuid) == "no team") {
                return;
            }
            
            boolean red = isRedTeam(thrower);
            if (SchematicManager.spawnNBTStructure(thrower, structure, spawnLoc, red, mapName, false, true)) {
                playerArena.getPlayerInArena(uuid).incrementUtility();
                String sound;
                if (structure.contains("obsidianshield")) {
                    sound = "spawn-obsidian-shield";
                    // Detect obsidian shield duration in seconds here
                    // Also clear obsidian shield after a while
                    int duration = (int) getItemStat(structure, "duration") * 2;
                    for (int i = duration; i > duration - 10; i--) {
                        int finalDuration = i;
                        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
                            if (!playerArena.isRunning()) {
                                return;
                            }
                            
                            if (finalDuration == duration) {
                                SchematicManager.spawnNBTStructure(null, "obsidianshieldclear-1", spawnLoc, red, mapName, false, false);
                                ConfigUtils.sendConfigSound("break-obsidian-shield", spawnLoc);
                            } else if (finalDuration % 2 == 0) {
                                SchematicManager.spawnNBTStructure(null, "obsidianshielddeplete-1", spawnLoc, red, mapName, false, false);
                            } else {
                                SchematicManager.spawnNBTStructure(null, "obsidianshield-1", spawnLoc, red, mapName, false, false);
                            }
                        }, i * 10L);
                    }
                } else if (structure.contains("shield-") || structure.contains("platform")) {
                    sound = "spawn-shield";
                } else if (structure.contains("torpedo")) {
                    sound = "spawn-torpedo";
                    // Register all spawned TNT minecarts into the tracker
                    for (Entity e : spawnLoc.getNearbyEntities(2, 3, 2)) {
                        if (e.getType() == EntityType.MINECART_TNT) {
                            playerArena.getTracker().registerTNTMinecart((ExplosiveMinecart) e, thrower);
                        }
                    }
                } else {
                    sound = "spawn-missile";
                }

                ConfigUtils.sendConfigSound(sound, spawnLoc);
            } else {
                Item newitem = thrower.getWorld().dropItem(thrower.getLocation(), item);
                newitem.setPickupDelay(0);
                
                if (pokeActive) {
                    Item mitem = thrower.getWorld().dropItem(thrower.getLocation(), offhand);
                    mitem.setPickupDelay(0);
                }
            }
            
            if (!thrown.isDead()) {
                thrown.remove();
            }
        }, 20);
        
        // Add particle effects for prickly/poke
        int prickly = plugin.getJSON().getAbility(uuid, "prickly");
        if (prickly <= 0 && poke <= 0) {
            return;
        }
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (thrown.isDead()) {
                    this.cancel();
                }
                DustOptions dustOptions = new DustOptions(prickly > 0 ? Color.MAROON : Color.LIME, 1.0F);
                playerArena.getWorld().spawnParticle(Particle.REDSTONE, thrown.getLocation(), 1, dustOptions);
            }
        }.runTaskTimer(plugin, 1, 1);
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
        if (event.getEntityType().toString().contains("ARROW") || event.getEntityType() == EntityType.FIREBALL) {
            return;
        }
        
        // Allow collisions if prickly projectiles
        if (MissileWarsPlugin.getPlugin().getJSON().getAbility(thrower.getUniqueId(), "prickly") > 0) {
            return;
        }

        // Otherwise, also cancel all collisions
        event.setCancelled(true);
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
        thrown.customName(ConfigUtils.toComponent("splash:" + duration + ":" + extend));
        playerArena.getPlayerInArena(thrower.getUniqueId()).incrementUtility();
        projectileConsume(hand, thrower, playerArena);
    }

    /** Handle spawning of splash waters */
    @EventHandler
    public void handleSplash(ProjectileHitEvent event) {
        // Make sure we're getting the right potion here
        if ((event.getEntityType() != EntityType.SPLASH_POTION) || (event.getEntity().customName() == null)) {
            return;
        }
        
        String customName = ConfigUtils.toPlain(event.getEntity().customName());
        if (!customName.contains("splash:")) {
            return;
        }

        // Spawn some water
        Block hitBlock = event.getHitBlock();
        Entity hitEntity = event.getHitEntity();
        if (hitBlock == null && hitEntity == null) {
            return;
        }

        // Get data from item
        String[] args = customName.split(":");
        Player thrower = (Player) event.getEntity().getShooter();
        
        // Defuse primed TNT
        if (hitEntity != null) {
            if (hitEntity.getType() != EntityType.PRIMED_TNT) {
                return;
            }
            Location loc = hitEntity.getLocation();
            if (loc.getBlock().getType() != Material.NETHER_PORTAL) {
                hitEntity.remove();
                loc.getBlock().setType(Material.TNT, false);
                return; 
            }
        }

        // Handle hitting oak_wood to fully repair canopies
        if (hitBlock.getType() == Material.OAK_WOOD) {
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
            thrower.sendMessage(ConfigUtils.toComponent("&7This canopy will now last &a" + extraduration + " &7seconds longer."));
            return;
        }
        
        // Check for portal breaks / moving pistons
        Location location = hitBlock.getRelative(event.getHitBlockFace()).getLocation();
        Block spawnBlock = location.getBlock();// Check for splashes going through moving pistons
        if (spawnBlock.getType() != Material.AIR) {
            if (spawnBlock.getType() == Material.NETHER_PORTAL) {
                Arena arena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(thrower.getWorld());
                if (arena instanceof ClassicArena) {
                    ((ClassicArena) arena).registerPortalBreak(location.clone(), thrower);
                }
            } else if (spawnBlock.getType() != Material.MOVING_PISTON) {
                return;
            } else {
                Block upper = hitBlock.getRelative(BlockFace.UP);
                if (upper.getType() != Material.AIR) {
                    return;
                }
                location = upper.getLocation();
            }
        }

        // Normal splash manager
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        double duration = Double.parseDouble(args[1]);
        int lavasplash = plugin.getJSON().getAbility(thrower.getUniqueId(), "lavasplash");
        if (lavasplash <= 0) {
            Block actualBlock = location.getBlock();
            actualBlock.setType(Material.WATER);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (actualBlock.getType() == Material.WATER) {
                    actualBlock.setType(Material.AIR);
                }
            }, (long) (duration * 20));
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
        for (int i = 0; i < locations.size(); i++) {
            Location l = locations.get(i);
            if (i > 0 && l.getBlock().getType() != Material.AIR) {
                continue;
            }
            l.getBlock().setType(Material.LAVA);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (l.getBlock().getType() == Material.LAVA) {
                    l.getBlock().setType(Material.AIR);
                }
            }, (long) (duration * 20 * durationmultiplier));
        }
    }
}
