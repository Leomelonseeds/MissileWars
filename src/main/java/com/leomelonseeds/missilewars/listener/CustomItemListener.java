package com.leomelonseeds.missilewars.listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ClassicArena;
import com.leomelonseeds.missilewars.arenas.TutorialArena;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.arenas.tracker.Tracked;
import com.leomelonseeds.missilewars.arenas.tracker.TrackedMissile;
import com.leomelonseeds.missilewars.decks.Ability;
import com.leomelonseeds.missilewars.decks.Ability.Stat;
import com.leomelonseeds.missilewars.invs.MapVoting;
import com.leomelonseeds.missilewars.listener.handler.AstralTurretManager;
import com.leomelonseeds.missilewars.listener.handler.CanopyManager;
import com.leomelonseeds.missilewars.listener.handler.DragonFireballHandler;
import com.leomelonseeds.missilewars.listener.handler.EnderSplashManager;
import com.leomelonseeds.missilewars.listener.handler.SmokeShieldHandler;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

/** Class to handle events for structure items. */
public class CustomItemListener implements Listener {
    
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
        Arena arena = ArenaUtils.getArena(player);
        if (arena != null) {
            return arena.getTeam(player.getUniqueId()) == TeamName.RED;
        }
        return false;
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
        
        if (ArenaUtils.getArena(player) == null) {
            return;
        }
        
        // Makes sure player is using architect
        int haste = MissileWarsPlugin.getPlugin().getJSON().getEnchantLevel(player.getUniqueId(), "haste");
        if (haste <= 0) {
            return;
        }
        
        // Clear haste if switching off from pickaxe
        ItemStack prev = player.getInventory().getItem(event.getPreviousSlot());
        if (prev != null && prev.getType() == Material.IRON_PICKAXE) {
            if (player.hasPotionEffect(PotionEffectType.HASTE)) {
                player.removePotionEffect(PotionEffectType.HASTE);
            }
            return;
        }
        
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null || item.getType() != Material.IRON_PICKAXE) {
            return;
        }
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 30 * 60 * 20, haste * 2 - 1));
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
        Arena playerArena = ArenaUtils.getArena(player);
        String held = InventoryUtils.getStringFromItem(hand, "held");
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
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "genesis open menu " + player.getName());
            return;
        }
        
        // Player is using a held item inside an arena
        UUID uuid = player.getUniqueId();
        if (held != null) {
            event.setCancelled(true);
            switch(held) {
            case "votemap":
                new MapVoting(player, playerArena);
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
        
        // Make sure this action can't deflect a fireball
        MiscListener.notLeftClick.add(uuid);
        ConfigUtils.schedule(1, () -> MiscListener.notLeftClick.remove(uuid));
        
        // Check if player is frozen by a canopy
        CanopyManager canopies = CanopyManager.getInstance();
        if (canopies.isFrozen(player)) {
            event.setCancelled(true);
            return;
        }
        
        // Check if player used a structure or utility
        Block clicked = event.getClickedBlock();
        String structureName = InventoryUtils.getStringFromItem(hand, "item-structure");
        String utility = InventoryUtils.getStringFromItem(hand, "item-utility");
        if (structureName == null && utility == null) {
            return;
        }
        
        // Disable if player just died
        MissileWarsPlayer mwp = playerArena.getPlayerInArena(uuid);
        if (mwp.justSpawned() && !hand.getType().toString().contains("BOW")) {
            event.setCancelled(true);
            return;
        }
        
        // Spawn a structure item
        if (structureName != null) {
            // Switch to throwing logic if using a throwable
            if (InventoryUtils.isThrowable(structureName)) {
                return;
            }
            
            event.setCancelled(true);
            
            // We can handle canopies now!
            if (structureName.contains("canopy")) {
                canopies.initPlayer(player, hand, playerArena, (int) getItemStat(structureName, "distance"));
                return;
            }
            
            // Check if a block was clicked, including a moving block
            if (event.getAction() == Action.RIGHT_CLICK_AIR) {
                Block temp = player.getTargetBlock(null, 4);
                if (temp.getType() == Material.MOVING_PISTON) {
                    clicked = temp;
                }
            }
            
            if (clicked == null) {
                return;
            }

            // Place structure
            if (SchematicManager.spawnNBTStructure(player, structureName, clicked.getLocation(), isRedTeam(player), true, true)) {
                ConfigUtils.sendConfigSound("spawn-missile", player);
                // Missile cooldown
                if (player.getGameMode() != GameMode.CREATIVE) {
                    for (ItemStack i : player.getInventory().getContents()) {
                        if (i == null) continue;
                        Material material = i.getType();
                        if (!player.hasCooldown(material) && material.toString().contains("SPAWN_EGG")) {
                            int cooldown = plugin.getConfig().getInt("experimental.missile-cooldown");
                            player.setCooldown(material, cooldown);
                        }
                    }
                }
                InventoryUtils.consumeItem(player, playerArena, hand, -1);
                mwp.incrementStat(MissileWarsPlayer.Stat.MISSILES);
                
                // Training arena things
                if (playerArena instanceof TutorialArena tutorialArena) {
                    tutorialArena.registerStageCompletion(player, 1);
                    if (structureName.equals("warhead-2")) {
                        tutorialArena.registerStageCompletion(player, 7);
                    }
                    ConfigUtils.schedule(100, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        
                        for (Tracked t : tutorialArena.getTracker().getMissiles()) {
                            if (!(t instanceof TrackedMissile tm)) {
                                continue;
                            }
                            
                            if (tm.contains(player.getLocation(), 2) && tm.isInMotion()) {
                                tutorialArena.registerStageCompletion(player, 2);
                                return;
                            }
                        }
                    });
                }
            }
            return;
        }
        
        // Spawn a utility item. At this point we know the item MUST have a utility tag
        if (utility.contains("creeper") && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
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
            mwp.incrementStat(MissileWarsPlayer.Stat.UTILITY);
            return;
        }

        // Spawn a fireball/dragon fireball
        if (utility.contains("fireball") || utility.contains("lingering")) {
            event.setCancelled(true);
            Fireball fireball;
            Location spawnLoc = player.getEyeLocation().clone().add(player.getEyeLocation().getDirection());
            if (utility.contains("lingering")) {
                fireball = (DragonFireball) player.getWorld().spawnEntity(spawnLoc, EntityType.DRAGON_FIREBALL);
                int amplifier = (int) getItemStat(utility, "amplifier");
                int duration = (int) getItemStat(utility, "duration");
                double radius = getItemStat(utility, "radius");
                fireball.customName(ConfigUtils.toComponent("vdf:" + amplifier + ":" + duration + ":" + radius));
                fireball.setCustomNameVisible(false);
                Bukkit.getPluginManager().registerEvents(new DragonFireballHandler(fireball), plugin);
            } else {
                fireball = (Fireball) player.getWorld().spawnEntity(spawnLoc, EntityType.FIREBALL);
                fireball.setIsIncendiary(true);
                float yield = (float) getItemStat(utility, "power");
                fireball.setYield(yield);
            }

            fireball.setDirection(player.getEyeLocation().getDirection());
            fireball.setShooter(player);
            InventoryUtils.consumeItem(player, playerArena, hand, -1);
            ConfigUtils.sendConfigSound("spawn-fireball", player.getLocation());
            mwp.incrementStat(MissileWarsPlayer.Stat.UTILITY);
            Bukkit.getPluginManager().callEvent(new ProjectileLaunchEvent(fireball));
            return;
        }
    }

    // Check for architect leaves to despawn them after a while
    @EventHandler
    public void architectLeaves(BlockPlaceEvent event) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Player player = event.getPlayer();
        Arena playerArena = ArenaUtils.getArena(player);
        ItemStack item = event.getItemInHand();
        if ((playerArena == null) || !item.getType().toString().contains("LEAVES")) {
            return;
        }
        
        // no max height
        Location loc = event.getBlock().getLocation();
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

        // Register block place.
        InventoryUtils.consumeItem(player, playerArena, item, event.getHand() == EquipmentSlot.HAND ? 
                    player.getInventory().getHeldItemSlot() : 40);
        
        // Remove leaves after 30 sec
        ConfigUtils.schedule(30 * 20, () -> {
            // Must still be leaves
            if (!loc.getBlock().getType().toString().contains("LEAVES")) {
                return;
            }
            
            loc.getBlock().setType(Material.AIR);
        });
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
        if (CanopyManager.getInstance().isFrozen(thrower)) {
            event.setCancelled(true);
            return;
        }
        
        Arena playerArena = ArenaUtils.getArena(thrower);
        if (playerArena == null) {
            return;
        }

        // Check if the thrown entity is a structure item
        ItemStack hand = thrown.getItem();
        String structureName = InventoryUtils.getStringFromItem(hand, "item-structure");
        if (structureName == null) {
            return;
        }

        // Add meta for structure identification + pokemissiles
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        ItemStack offhand = thrower.getInventory().getItemInOffHand();
        boolean hasOffhandCooldown = thrower.hasCooldown(offhand.getType());
        UUID uuid = thrower.getUniqueId();
        boolean poke = plugin.getJSON().getLevel(uuid, Ability.POKEMISSILES) > 0;
        if (poke) {
            String offName = InventoryUtils.getStringFromItem(offhand, "item-structure");
            if (offName != null && !hasOffhandCooldown && offhand.getType().toString().contains("SPAWN_EGG")) {
                thrown.setItem(offhand);
                structureName = offName + "-p"; // Add extra dash to represent a pokemissile
                InventoryUtils.consumeItem(thrower, playerArena, offhand, -1);
            }
        }
        
        // Check for astral turret
        if (structureName.contains("obsidianshield") && offhand.getType() == Material.ARROW && !hasOffhandCooldown &&
                plugin.getJSON().getLevel(uuid, Ability.ASTRAL_TURRET) > 0) {
            thrown.customName(ConfigUtils.toComponent("astral"));
            InventoryUtils.consumeItem(thrower, playerArena, offhand, -1);
            AstralTurretManager.getInstance();
        }
        
        projectileConsume(hand, thrower, playerArena);
        
        // Add particle effects for prickly
        if (plugin.getJSON().getLevel(uuid, Ability.PRICKLY_PROJECTILES) > 0) {
            ArenaUtils.spiralTrail(thrown, Particle.INSTANT_EFFECT, null);
        }

        // More delay + particles for impact trigger
        if (plugin.getJSON().getLevel(uuid, Ability.IMPACT_TRIGGER) > 0) {
            ArenaUtils.spiralTrail(thrown, Particle.SMOKE, null);
        }

        // Schedule structure spawn after 1 second (or more, if impact trigger), if snowball is still alive
        String structure = structureName;
        ConfigUtils.schedule(20, () -> {
            if (spawnUtility(thrower, thrown, structure, playerArena, thrown.getLocation())) {
                return;
            }
            
            if (poke) {
                InventoryUtils.regiveItem(thrower, offhand);
            }
        });
    }
    
    // Handle impact trigger passive (allow utilities to spawn when hitting a block)
    @EventHandler(priority = EventPriority.HIGH)
    public void impactTrigger(ProjectileHitEvent event) {
        EntityType type = event.getEntityType();
        if (!(type == EntityType.SNOWBALL || type == EntityType.EGG || type == EntityType.ENDER_PEARL)) {
            return;
        }
        
        ThrowableProjectile thrown = (ThrowableProjectile) event.getEntity();
        if (!(thrown.getShooter() instanceof Player thrower)) {
            return;
        }
        
        Arena playerArena = ArenaUtils.getArena(thrower);
        if (playerArena == null) {
            return;
        }
        
        // The all important line for this event
        int level = MissileWarsPlugin.getPlugin().getJSON().getLevel(thrower.getUniqueId(), Ability.IMPACT_TRIGGER);
        if (level <= 0) {
            return;
        }
        
        ItemStack hand = thrown.getItem();
        String structureName = InventoryUtils.getStringFromItem(hand, "item-structure");
        if (structureName == null) {
            return;
        }
        
        Location spawnLoc;
        if (event.getHitEntity() != null && level >= 2) {
            Vector back = thrown.getVelocity().normalize().multiply(-0.5);
            spawnLoc = thrown.getLocation().add(back);
        } else if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            Block block = event.getHitBlock();
            spawnLoc = block.getRelative(event.getHitBlockFace()).getLocation();
        } else {
          return;
        }
        
        spawnUtility(thrower, thrown, structureName, playerArena, spawnLoc);
    }
    
    // Returns false ONLY IF the utility spawn failed due to the schematic manager returned false
    // If it returns false, the item should already be given back to the player. False is only for,
    // say if pokemissiles was active and that needs to be given back
    private boolean spawnUtility(Player thrower, ThrowableProjectile thrown, String structure, Arena playerArena, Location spawnLoc) {
        if (thrown.isDead() || !thrower.isOnline()) {
            return true;
        }

        ItemStack item = thrown.getItem();
        UUID uuid = thrower.getUniqueId();
        if (playerArena.getTeam(uuid) == TeamName.NONE) {
            return true;
        }
        
        if (!thrown.isDead()) {
            thrown.remove();
        }
        
        // Do not allow structures to spawn too close to players
        if (spawnLoc.distance(thrower.getLocation()) < 2) {
            ConfigUtils.sendConfigMessage("structure-too-close", thrower);
            InventoryUtils.regiveItem(thrower, item);
            return false;
        }
        
        boolean red = isRedTeam(thrower);
        if (!SchematicManager.spawnNBTStructure(thrower, structure, spawnLoc, red, false, true)) {
            InventoryUtils.regiveItem(thrower, item);
            return false;
        }
        
        playerArena.getPlayerInArena(uuid).incrementStat(MissileWarsPlayer.Stat.UTILITY);
        String sound;
        if (structure.contains("obsidianshield")) {
            sound = "spawn-obsidian-shield";
            
            // Check for smokeshield
            int duration = (int) getItemStat(structure, "duration");
            int smokeshield = MissileWarsPlugin.getPlugin().getJSON().getLevel(uuid, Ability.SMOKE_SHIELD);
            if (smokeshield > 0) {
                double radius = ConfigUtils.getAbilityStat(Ability.SMOKE_SHIELD, smokeshield, Stat.RADIUS);
                new SmokeShieldHandler(spawnLoc, duration * 20, radius);
            }
            
            // Check for astral turret
            // Don't need to check for arrow cooldown because arrow was consumed on throw
            if (thrown.customName() != null && ConfigUtils.toPlain(thrown.customName()).equals("astral") &&
                    thrower.getInventory().getItemInOffHand().getType() == Material.ARROW) {
                AstralTurretManager.getInstance().registerPlayer(thrower, spawnLoc, red);
            }

            // Clear obsidian shield after a while
            int doubleDuration = duration * 2;
            for (int i = doubleDuration; i > doubleDuration - 10; i--) {
                int finalDuration = i;
                ConfigUtils.schedule(i * 10, () -> {
                    if (!playerArena.isRunning()) {
                        return;
                    }
                    
                    if (finalDuration == doubleDuration) {
                        SchematicManager.spawnNBTStructure(null, "obsidianshieldclear-1", spawnLoc, red, false, false);
                        ConfigUtils.sendConfigSound("break-obsidian-shield", spawnLoc);
                        AstralTurretManager.getInstance().explode(thrower, spawnLoc);
                        AstralTurretManager.getInstance().unregisterPlayer(thrower, spawnLoc);
                    } else if (finalDuration % 2 == 0) {
                        SchematicManager.spawnNBTStructure(null, "obsidianshielddeplete1-1", spawnLoc, red, false, false);
                    } else {
                        SchematicManager.spawnNBTStructure(null, "obsidianshielddeplete2-1", spawnLoc, red, false, false);
                    }
                });
            }
        } else if (structure.contains("shield-") || structure.contains("platform")) {
            sound = "spawn-shield";
        } else if (structure.contains("torpedo")) {
            sound = "spawn-torpedo";
            
            // Ignite level 2 torpedos if a player gets trapped inside
            boolean lvl2 = structure.contains("2");
            Location tntLoc = lvl2 ? spawnLoc.clone() : spawnLoc.clone().add(0, -1, 0);
            double yAdd = lvl2 ? 0.5 : 0;
            double yCheck = lvl2 ? 2 : 1.5;
            for (Entity e : spawnLoc.toCenterLocation().add(0, yAdd, 0).getNearbyEntities(0.5, yCheck, 0.5)) {
                if (e.getType() != EntityType.PLAYER) {
                    continue;
                }
                
                for (Location tnt : List.of(
                        tntLoc.clone().add(1, 0, 0),
                        tntLoc.clone().add(-1, 0, 0),
                        tntLoc.clone().add(0, 0, 1),
                        tntLoc.clone().add(0, 0, -1)
                    )) {
                    tnt.getBlock().setType(Material.AIR);
                    TNTPrimed entTnt = (TNTPrimed) tnt.getWorld().spawnEntity(tnt.toCenterLocation().add(0, -0.5, 0), EntityType.TNT);
                    entTnt.setSource(thrower);
                    entTnt.setFuseTicks(80);
                }
            }
            
            // Register all spawned TNT minecarts into the tracker
            for (Entity e : spawnLoc.getNearbyEntities(2, 3, 2)) {
                if (e.getType() == EntityType.TNT_MINECART) {
                    playerArena.getTracker().registerTNTMinecart((ExplosiveMinecart) e, thrower);
                }
            }
        } else {
            // This is a missile from pokemissiles then
            sound = "spawn-missile";
        }

        ConfigUtils.sendConfigSound(sound, spawnLoc);
        return true;
    }
    

    // This method allows utilities to go through players
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
        Arena playerArena = ArenaUtils.getArena(thrower);
        if (playerArena == null) {
            return;
        }
        
        // We're only handling utility collisions against players
        if (event.getHitEntity().getType() != EntityType.PLAYER) {
            return;
        }
        
        // Allow projectiles to hit the player himself
        Player hit = (Player) event.getHitEntity();
        if (hit.equals(thrower)) {
            return;
        }
        
        // Otherwise always disallow projectiles to collide with players of same team
        TeamName team1 = playerArena.getTeam(thrower.getUniqueId());
        TeamName team2 = playerArena.getTeam(hit.getUniqueId());
        if (team1 != TeamName.NONE && team1 == team2) {
            event.setCancelled(true);
            return;
        }
        
        // Here, players must be on different teams
        // Allow collisions if arrow, fireball, or trident
        String typeString = event.getEntityType().toString();
        if (typeString.contains("ARROW") || typeString.contains("FIREBALL") || 
                typeString.equals("TRIDENT") || typeString.equals("SPLASH_POTION")) {
            return;
        }
        
        // Allow collisions if prickly projectiles
        if (MissileWarsPlugin.getPlugin().getJSON().getLevel(thrower.getUniqueId(), Ability.PRICKLY_PROJECTILES) > 0) {
            return;
        }

        // Otherwise, also cancel all collisions
        event.setCancelled(true);
    }
    
    // The following section handles splash-specific mechanics

    // Handle splash throwing
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
        Arena playerArena = ArenaUtils.getArena(thrower);
        if (playerArena == null) {
            return;
        }

        // Check if player is holding a utility item
        ItemStack hand = thrown.getItem();
        String utility = InventoryUtils.getStringFromItem(hand, "item-utility");

        // Make sure it's splash potion of water
        if (utility == null || !thrown.getEffects().isEmpty()) {
            return;
        }

        // Check the duration here
        double duration = getItemStat(utility, "duration");
        int extend = (int) getItemStat(utility, "extend");
        boolean ender = ConfigUtils.toPlain(hand.getItemMeta().displayName()).contains("Ender");
        String name = (ender ? "ender" : "") + "splash:" + duration + ":" + extend;
        thrown.customName(ConfigUtils.toComponent(name));
        playerArena.getPlayerInArena(thrower.getUniqueId()).incrementStat(MissileWarsPlayer.Stat.UTILITY);
        projectileConsume(hand, thrower, playerArena);
        
        // Add particles if ender splash
        if (!ender) {
            return;
        }
        
        EnderSplashManager esm = EnderSplashManager.getInstance();
        esm.addPlayer(thrower, thrown);
        BukkitTask trail = ArenaUtils.spiralTrail(thrown, Particle.DRAGON_BREATH, null);
        BukkitTask deathChecker = Bukkit.getScheduler().runTaskTimer(MissileWarsPlugin.getPlugin(), () -> {
            if (thrown.isDead()) {
                esm.removeSplash(thrower, thrown);
            }  
        }, 1, 1);
        
        // Despawn ender splash after effectiveness has run out
        int level = MissileWarsPlugin.getPlugin().getJSON().getLevel(thrower.getUniqueId(), Ability.ENDER_SPLASH);
        if (level <= 0) {
            level = 1;
        }
        
        double expiry = ConfigUtils.getAbilityStat(Ability.ENDER_SPLASH, level, Stat.DURATION) * 20;
        ConfigUtils.schedule((int) expiry, () -> {
           if (thrown.isDead()) {
               return;
           }

           if (!esm.removeSplash(thrower, thrown)) {
               return;
           }
           
           // Replace custom name so it doesn't teleport player in the next event
           String curName = ConfigUtils.toPlain(thrown.customName());
           thrown.customName(ConfigUtils.toComponent(curName.replace("ender", "")));
           
           // Remove trail and death check
           trail.cancel();
           deathChecker.cancel();
           
           // Set color back to blue and do some sfx
           PotionMeta meta = thrown.getPotionMeta();
           meta.setColor(Color.BLUE);
           thrown.setPotionMeta(meta);
           playerArena.getWorld().spawnParticle(Particle.DRAGON_BREATH, thrown.getLocation(), 10, 0, 0, 0, 0.05, null, true);
           ConfigUtils.sendConfigSound("ender-splash-expire", thrown.getLocation());
        });
    }

    // Handle splash hit block mechanics
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
            if (hitEntity.getType() != EntityType.TNT) {
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
            CanopyManager.getInstance().registerExtension(key, extraduration);
            Location newSpawn = hitBlock.getLocation().add(0, 1, 0);
            // map name doesn't matter here because the canopy has already been spawned,
            // we therefore know that the structure was placed successfully and do not need
            // to perform validity checks based on the map
            SchematicManager.spawnNBTStructure(null, "canopy-1", newSpawn, isRedTeam(thrower), false, false);
            thrower.sendMessage(ConfigUtils.toComponent("&7This canopy will now last &a" + extraduration + " &7seconds longer."));
            return;
        }
        
        // Check for portal breaks / moving pistons
        Location location = hitBlock.getRelative(event.getHitBlockFace()).getLocation();
        Block spawnBlock = location.getBlock();// Check for splashes going through moving pistons
        boolean blockUpdates = true;
        if (spawnBlock.getType() != Material.AIR) {
            if (spawnBlock.getType() == Material.NETHER_PORTAL) {
                blockUpdates = false;
                Arena arena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(thrower.getWorld());
                if (arena instanceof ClassicArena carena) {
                    carena.registerPortalBreak(location.clone(), thrower);
                }
            } else if (spawnBlock.getType().toString().contains("PISTON")) {
                Block upper = hitBlock.getRelative(BlockFace.UP);
                if (upper.getType() != Material.AIR) {
                    return;
                }
                location = upper.getLocation();
            } else if (spawnBlock.getType() != Material.WATER) {
                return;
            }
        }

        // Normal splash manager
        double duration = Double.parseDouble(args[1]);
        Block actualBlock = location.getBlock();
        actualBlock.setType(Material.WATER, blockUpdates);
        if (!blockUpdates) {
            // Since portals are broken with FAWE, wait a bit before
            // sending out block update telling water to flow
            ConfigUtils.schedule(5, () -> {
                actualBlock.setType(Material.AIR);
                actualBlock.setType(Material.WATER);
            });
        }
        
        // Replace back with air after the set splash duration
        ConfigUtils.schedule((int) (duration * 20), () -> {
            if (actualBlock.getType() == Material.WATER) {
                actualBlock.setType(Material.AIR);
            }
        });
        
        // Ender splash
        if (customName.contains("ender") && thrower.isOnline() && 
                EnderSplashManager.getInstance().removeSplash(thrower, event.getEntity())) {
            Location tpLoc = location.toCenterLocation().add(0, -0.5, 0);
            tpLoc.setYaw(thrower.getYaw());
            tpLoc.setPitch(thrower.getPitch());
            thrower.teleport(tpLoc);
            ConfigUtils.sendConfigSound("ender-splash", location);
        }
    }
}
