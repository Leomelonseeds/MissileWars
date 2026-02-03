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
import org.bukkit.block.data.Levelled;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.scheduler.BukkitRunnable;
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
import com.leomelonseeds.missilewars.listener.handler.LavaHandler;
import com.leomelonseeds.missilewars.listener.handler.MovingTNTHandler;
import com.leomelonseeds.missilewars.listener.handler.SmokeShieldHandler;
import com.leomelonseeds.missilewars.listener.handler.TritonHandler;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CooldownUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

/** Class to handle events for structure items. */
public class CustomItemListener implements Listener {
    
    private TritonHandler tritonHandler;
    private Map<Player, ThrowableProjectile> bludgerStore;
    
    public CustomItemListener() {
        this.tritonHandler = new TritonHandler();
        this.bludgerStore = new HashMap<>();
    }

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

    /** Handle right clicking missiles and utility items */
    @EventHandler
    public void useItem(PlayerInteractEvent event) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        // Stop if not right-click
        if (!event.getAction().isRightClick()) {
            return;
        }
        
        // Check if player is trying to place a structure item
        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack hand = inv.getItem(event.getHand());
        if (CooldownUtils.hasCooldown(player, hand)) {
            return;
        }
        
        // Check arena
        Arena playerArena = ArenaUtils.getArena(player);
        String held = InventoryUtils.getStringFromItemKey(hand, InventoryUtils.HELD_KEY);
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
        String structureName = InventoryUtils.getStructureFromItem(hand);
        String utility = InventoryUtils.getUtilityFromItem(hand);
        if (structureName == null && utility == null) {
            return;
        }
        
        // Spawn a structure item
        MissileWarsPlayer mwp = playerArena.getPlayerInArena(uuid);
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
                DragonFireballHandler dfbHandler = new DragonFireballHandler(fireball);
                MiscListener.fireballs.put(fireball, dfbHandler.getSlime());
            } else {
                fireball = (Fireball) player.getWorld().spawnEntity(spawnLoc, EntityType.FIREBALL);
                fireball.setIsIncendiary(true);
                float yield = (float) getItemStat(utility, "power");
                fireball.setYield(yield);
                MiscListener.fireballs.put(fireball, null);
            }

            fireball.setDirection(player.getEyeLocation().getDirection());
            fireball.setShooter(player);
            InventoryUtils.consumeItem(player, playerArena, hand, -1);
            ConfigUtils.sendConfigSound("spawn-fireball", player.getLocation());
            mwp.incrementStat(MissileWarsPlayer.Stat.UTILITY);
            Bukkit.getPluginManager().callEvent(new ProjectileLaunchEvent(fireball));
            
            // Remove fireballs from deflection manager when they die
            ArenaUtils.doUntilDead(fireball, null, true, () -> MiscListener.fireballs.remove(fireball));
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
        int decayAfter = 30;
        int natures = MissileWarsPlugin.getPlugin().getJSON().getLevel(player.getUniqueId(), Ability.NATURES_BLESSING);
        if (natures > 0) {
            decayAfter += (int) ConfigUtils.getAbilityStat(Ability.NATURES_BLESSING, natures, Stat.DURATION);
        }
        
        ConfigUtils.schedule(decayAfter * 20, () -> {
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
        String structureName = InventoryUtils.getStructureFromItem(hand);
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
            String offName = InventoryUtils.getStructureFromItem(offhand);
            if (offName != null && !hasOffhandCooldown && offhand.getType().toString().contains("SPAWN_EGG")) {
                thrown.setItem(offhand);
                structureName = offName + "-p"; // Add extra dash to represent a pokemissile
                InventoryUtils.consumeItem(thrower, playerArena, offhand, -1);
            }
        }
        
        // Check for astral turret
        boolean isObsidianShield = structureName.contains("obsidianshield");
        if (isObsidianShield && offhand.getType() == Material.ARROW && !hasOffhandCooldown &&
                plugin.getJSON().getLevel(uuid, Ability.ASTRAL_TURRET) > 0) {
            thrown.customName(ConfigUtils.toComponent("astral"));
            InventoryUtils.consumeItem(thrower, playerArena, offhand, -1);
            AstralTurretManager.getInstance();
        }
        
        projectileConsume(hand, thrower, playerArena);
        
        // Add particle effects for prickly
        if (plugin.getJSON().getLevel(uuid, Ability.KINGSMANS_BLUDGERS) > 0) {
            ArenaUtils.spiralTrail(thrown, Particle.INSTANT_EFFECT, null);
        }

        // More delay + particles for impact trigger
        if (plugin.getJSON().getLevel(uuid, Ability.IMPACT_TRIGGER) > 0) {
            ArenaUtils.spiralTrail(thrown, Particle.SMOKE, null);
        }
        
        // Increase delay if using kingsmans bludgers
        int delay = 20;
        int bludgers = plugin.getJSON().getLevel(uuid, Ability.KINGSMANS_BLUDGERS);
        if (bludgers > 0) {
            delay = (int) (ConfigUtils.getAbilityStat(Ability.KINGSMANS_BLUDGERS, bludgers, Stat.DURATION) * 20);
            bludgerStore.put(thrower, thrown);
        }

        // Schedule structure spawn after 1 second (or more, if impact trigger), if snowball is still alive
        String structure = structureName;
        ConfigUtils.schedule(delay, () -> {
            // Bludgers should be removed as they can no longer be spawned
            if (bludgers > 0 && bludgerStore.containsKey(thrower) && 
                    bludgerStore.get(thrower).getUniqueId().equals(thrown.getUniqueId())) {
                bludgerStore.remove(thrower);
            }
            
            if (spawnUtility(thrower, thrown, structure, playerArena, thrown.getLocation())) {
                return;
            }
            
            if (poke) {
                InventoryUtils.regiveItem(thrower, offhand);
            }
        });
    }
    
    // Handle kingsmans bludgers left click manual spawn
    @EventHandler
    public void manualUtilitySpawn(PlayerInteractEvent event) {
        if (!event.getAction().isLeftClick()) {
            return;
        }
        
        Player player = event.getPlayer();
        ThrowableProjectile bludger = bludgerStore.get(player);
        if (bludger == null || bludger.isDead()) {
            bludgerStore.remove(player);
            return;
        }
        
        // Why the fuck do I need to do this? Why does the server think I left clicked
        // when I throw the thing even though I clearly only right clicked?
        if (bludger.getTicksLived() == 0) {
            return;
        }
        
        Arena arena = ArenaUtils.getArena(player);
        if (arena == null) {
            return;
        }
        
        ItemStack item = event.getItem();
        ItemStack bludgerItem = bludger.getItem();
        if (item == null || item.getType() != bludgerItem.getType()) {
            return;
        }
        
        int bludgers = MissileWarsPlugin.getPlugin().getJSON().getLevel(player.getUniqueId(), Ability.KINGSMANS_BLUDGERS);
        if (bludgers <= 0) {
            return;
        }
        
        int delay = (int) (ConfigUtils.getAbilityStat(Ability.KINGSMANS_BLUDGERS, bludgers, Stat.CUTOFF) * 20);
        bludgerStore.remove(player);
        bludger.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, bludger.getLocation(), 25, 0, 0, 0, 1, null, true);
        ConfigUtils.sendConfigSound("bludger-activate", bludger.getLocation());
        ConfigUtils.sendConfigSound("bludger-activate", player);
        ConfigUtils.schedule(delay, () -> 
            spawnUtility(player, bludger, InventoryUtils.getStructureFromItem(bludgerItem), arena, bludger.getLocation()));
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
        String structureName = InventoryUtils.getStructureFromItem(hand);
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
        boolean isTorpedo = structure.contains("torpedo");
        if (isTorpedo && spawnLoc.distance(thrower.getLocation()) < 2) {
            ConfigUtils.sendConfigMessage("torpedo-too-close", thrower);
            InventoryUtils.regiveItem(thrower, item);
            return false;
        }
        
        boolean red = isRedTeam(thrower);
        boolean isMissile = InventoryUtils.isMissile(structure);
        if (!SchematicManager.spawnNBTStructure(thrower, structure, spawnLoc, red, isMissile, true)) {
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
        } else if (isTorpedo) {
            sound = "spawn-torpedo";
            
            // Ignite level 2 torpedos if a player gets trapped inside
            boolean lvl2 = structure.contains("2");
            Location tntLoc = spawnLoc.clone();
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
                
                break;
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
        if (MissileWarsPlugin.getPlugin().getJSON().getLevel(thrower.getUniqueId(), Ability.KINGSMANS_BLUDGERS) > 0) {
            return;
        }

        // Otherwise, also cancel all collisions
        event.setCancelled(true);
    }
    
    // The following section handles splash-specific mechanics
    
    // Handle splash conversion to molotov splash
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Arena playerArena = ArenaUtils.getArena(player);
        if (playerArena == null) {
            return;
        }

        if (MissileWarsPlugin.getPlugin().getJSON().getLevel(player.getUniqueId(), Ability.MOLOTOV_SPLASH) == 0) {
            return;
        }
        
        boolean swapOn = event.getOffHandItem().getType() == Material.SPLASH_POTION;
        if (!swapOn && event.getMainHandItem().getType() != Material.SPLASH_POTION) {
            return;
        }
        
        ItemStack splashItem = swapOn ? event.getOffHandItem() : event.getMainHandItem();
        ItemStack instanceItem = InventoryUtils.findInstanceItem(playerArena.getPlayerInArena(player.getUniqueId()), splashItem);
        if (instanceItem == null) {
            return;
        }
        
        // Set instance item meta
        PotionMeta meta = (PotionMeta) instanceItem.getItemMeta();
        String name = ConfigUtils.toPlain(meta.customName());
        if (swapOn && !name.contains("Molotov")) {
            name = name
                .replace("Splash", "Molotov Splash")
                .replace("9", "6");
            meta.setColor(Color.ORANGE);
        } else if (!swapOn && name.contains("Molotov")) {
            name = name
                .replace("Molotov ", "")
                .replace("6", "9");
            meta.setColor(null);
        } else {
            return;
        }
        
        meta.customName(ConfigUtils.toComponent(name));
        instanceItem.setItemMeta(meta);
        
        // Move instance item to inventory
        ItemStack toGive = instanceItem.clone();
        toGive.addUnsafeEnchantments(splashItem.getEnchantments());
        toGive.setAmount(splashItem.getAmount());
        if (swapOn) {
            event.setOffHandItem(toGive);
        } else {
            event.setMainHandItem(toGive);
        }
    }

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
        String utility = InventoryUtils.getUtilityFromItem(hand);

        // Make sure it's splash potion of water
        if (utility == null || !thrown.getEffects().isEmpty()) {
            return;
        }

        // Check the duration here
        double duration = getItemStat(utility, "duration");
        int extend = (int) getItemStat(utility, "extend");
        int molotov = MissileWarsPlugin.getPlugin().getJSON().getLevel(thrower.getUniqueId(), Ability.MOLOTOV_SPLASH);
        String name = "splash:" + duration + ":" + extend;
        
        // Handle molotov details
        if (ConfigUtils.toPlain(hand.displayName()).contains("Molotov")) {
            // Add radius stat
            double radius = ConfigUtils.getAbilityStat(Ability.MOLOTOV_SPLASH, molotov, Stat.RADIUS);
            name = "molotov" + name + ":" + radius;
            ArenaUtils.spiralTrail(thrown, Particle.FLAME, null);
        }
        
        thrown.customName(ConfigUtils.toComponent(name));
        playerArena.getPlayerInArena(thrower.getUniqueId()).incrementStat(MissileWarsPlayer.Stat.UTILITY);
        projectileConsume(hand, thrower, playerArena);
    }

    // Handle splash hit block mechanics
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
        
        // Defuse primed TNT and trident
        boolean isMolotov = customName.startsWith("molotov");
        if (hitEntity != null) {
            if (hitEntity.getType() == EntityType.TNT) {
                // Molotov splashes instantly explode tnt
                if (isMolotov) {
                    ((TNTPrimed) hitEntity).setFuseTicks(1);
                    return;
                }
                
                Location loc = hitEntity.getLocation();
                if (loc.getBlock().getType() != Material.NETHER_PORTAL) {
                    hitEntity.remove();
                    loc.getBlock().setType(Material.TNT, false);
                }
                
                return;
            }
            
            // Triton give trident
            if (hitEntity.getType() != EntityType.PLAYER) {
                return;
            }
            
            Player hitPlayer = (Player) hitEntity;
            if (hitPlayer.equals(thrower) && MissileWarsPlugin.getPlugin().getJSON().getLevel(hitPlayer.getUniqueId(), Ability.TRITON) > 0) {
                ItemStack[] invItems = hitPlayer.getInventory().getContents();
                for (int i = 0; i < invItems.length; i++) {
                    ItemStack item = invItems[i];
                    if (item == null || item.getType() != Material.GOLDEN_SWORD) {
                        continue;
                    }
                    
                    ItemStack trident = tritonHandler.addPlayer(hitPlayer, item);
                    hitPlayer.getInventory().setItem(i, trident);
                    ConfigUtils.sendConfigSound("triton-activate", hitPlayer.getLocation());
                    return;
                }
            }
            
            // If no triton, then attempt to spawn splash at player feet
            hitBlock = hitPlayer.getLocation().getBlock();
        }

        // Handle hitting oak_wood to fully repair canopies
        if (!isMolotov && hitBlock.getType() == Material.OAK_WOOD) {
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
        Material liquid = isMolotov ? Material.LAVA : Material.WATER;
        Location location = hitEntity != null ? hitBlock.getLocation() : hitBlock.getRelative(event.getHitBlockFace()).getLocation();
        Block spawnBlock = location.getBlock();
        
        // Check for splashes going through moving pistons
        boolean blockUpdates = true;
        if (!isSplashReplaceable(spawnBlock)) {
            if (spawnBlock.getType() == Material.NETHER_PORTAL) {
                blockUpdates = false;
                Arena arena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(thrower.getWorld());
                if (arena instanceof ClassicArena carena) {
                    carena.registerPortalBreak(location.clone(), thrower);
                }
            } else if (spawnBlock.getType().toString().contains("PISTON")) {
                location = hitBlock.getRelative(BlockFace.UP).getLocation();
            }
        }
        
        double radius = isMolotov ? Double.parseDouble(args[3]) : 0;
        int duration = (int) (Double.parseDouble(args[1]) * 20);
        levelledSplash(location, liquid, isMolotov ? 5 : 0, duration, blockUpdates, thrower);
        if (radius >= 1) {
            List<Location> locs = List.of(
                location.clone().add(1, 0, 0),
                location.clone().add(-1, 0, 0),
                location.clone().add(0, 0, 1),
                location.clone().add(0, 0, -1)
            );
            
            ConfigUtils.schedule(5, () -> {
                locs.forEach(loc -> levelledSplash(loc, liquid, 7, duration, true, thrower));
            });
        }
        
        if (radius >= 1.5) {
            List<Location> locs = List.of(
                location.clone().add(1, 0, 1),
                location.clone().add(-1, 0, 1),
                location.clone().add(1, 0, -1),
                location.clone().add(-1, 0, -1)
            );
            
            ConfigUtils.schedule(10, () -> {
                locs.forEach(loc -> levelledSplash(loc, liquid, 7, duration, true, thrower));
            });
        }
        
        if (isMolotov) {
            ConfigUtils.sendConfigSound("molotov-hit", location);
        }
    }
    
    private void levelledSplash(Location center, Material liquid, int level, int duration, boolean blockUpdates, Player source) {
        Block spawnBlock = center.getBlock();
        boolean isLava = liquid == Material.LAVA;
        MovingTNTHandler tntHandler = MovingTNTHandler.getInstance();
        if (level > 5) {
            if (isLava && tntHandler.igniteTNT(spawnBlock, source, 10)) {
                return;
            }
            
            // Attempt to spawn the splash 1 block lower
            if (!isSplashReplaceable(spawnBlock) || !ArenaUtils.isBlockSupported(spawnBlock)) {
                spawnBlock = spawnBlock.getRelative(BlockFace.DOWN);
                if (isLava && tntHandler.igniteTNT(spawnBlock, source, 10)) {
                    return;
                }
                
                if (!isSplashReplaceable(spawnBlock)) {
                    return;
                }
            }
            
            if (!ArenaUtils.isBlockSupported(spawnBlock)) {
                return;
            }
        }
        
        Levelled levelled = (Levelled) liquid.createBlockData();
        levelled.setLevel(level);
        spawnBlock.setBlockData(levelled, blockUpdates);
        if (isLava) {
            tntHandler.igniteTNT(spawnBlock.getRelative(BlockFace.DOWN), source, 10);
            LavaHandler.getInstance().addLavaSource(center, source);
        }
        
        // Delayed by 6 instead of 5 because lava and water spread in 5 and 30 ticks respectively and
        // if they disappear we replace them next tick (same tick doesn't work)
        new BukkitRunnable() {
            
            int timeAlive = 0;
            
            @Override
            public void run() {
                Block curBlock = center.getBlock();
                boolean unchanged = curBlock.getType() == liquid || curBlock.getType() == Material.AIR;
                timeAlive += 5;
                if (timeAlive >= duration || !unchanged) {
                    if (unchanged) {
                        curBlock.setType(Material.AIR);
                    }
                    
                    if (isLava) {
                        LavaHandler.getInstance().removeLavaSource(center);
                    }
                    
                    this.cancel();
                    return;
                }
                
                if (!blockUpdates && timeAlive == 5) {
                    curBlock.setType(Material.AIR);
                    curBlock.setBlockData(levelled);
                } else if (level > 0) {
                    curBlock.setBlockData(levelled, false);
                }
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), 6, 5);
    }
    
    private boolean isSplashReplaceable(Block block) {
        Material type = block.getType();
        return type.isAir() || type == Material.WATER || type == Material.LAVA;
    }
}
