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
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.schematics.SchematicManager;
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
     * Get string data from custom item
     * 
     * @param item
     * @param id
     * @return
     */
    private String getStringFromItem(ItemStack item, String id) {
        if ((item.getItemMeta() == null) || !item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), id),
                PersistentDataType.STRING)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get( new NamespacedKey(MissileWarsPlugin.getPlugin(),
                id), PersistentDataType.STRING);
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
            redTeam = arena.getTeam(player.getUniqueId()).equalsIgnoreCase(ChatColor.RED +
                    "red" + ChatColor.RESET);
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
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 30 * 60 * 20, level - 1));
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

        Arena playerArena = getPlayerArena(player);
        if (playerArena == null) {
            return;
        }
        
        // Check if gamemode survival
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        
        // Check if player is frozen by a canopy
        if (canopy_freeze.contains(player)) {
            event.setCancelled(true);
            return;
        }
        
        // Check if player used a structure or utility
        PlayerInventory inv = player.getInventory();
        ItemStack hand = inv.getItem(event.getHand());
        Block clicked = event.getClickedBlock();
        String structureName = getStringFromItem(hand, "item-structure");
        String utility = getStringFromItem(hand, "item-utility");
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
                spawnCanopy(player, playerArena, structureName, hand);
                return;
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
            
            if (SchematicManager.spawnNBTStructure(structureName, clicked.getLocation(), isRedTeam(player), mapName)) {
                hand.setAmount(hand.getAmount() - 1);
                int adrenaline = plugin.getJSON().getAbility(player.getUniqueId(), "adrenaline");
                if (adrenaline > 0) {
                    int level = (int) ConfigUtils.getAbilityStat("Vanguard.passive.adrenaline", adrenaline, "amplifier");
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, level));
                }
                playerArena.getPlayerInArena(player.getUniqueId()).incrementMissiles();
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
        
        // Spawn a utility item
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
                    hand.setAmount(hand.getAmount() - 1);
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
                hand.setAmount(hand.getAmount() - 1);
                for (Player players : player.getWorld().getPlayers()) {
                     ConfigUtils.sendConfigSound("spawn-fireball", players, player.getLocation());
                }
                playerArena.getPlayerInArena(player.getUniqueId()).incrementUtility();
            }
        }
    }

    Set<UUID> canopy_cooldown = new HashSet<>();
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
        if ((playerArena == null) || !event.getItemInHand().getType().toString().contains("LEAVES")) {
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
            if (ConfigUtils.inShield(playerArena, loc, team, 5)) {
                double percentage = ConfigUtils.getAbilityStat("Architect.passive.repairman", repairman, "percentage") / 100;
                Random random = new Random();
                if (random.nextDouble() < percentage) {
                    ItemStack item = event.getItemInHand();
                    item.setAmount(item.getAmount());
                    EquipmentSlot slot = event.getHand();
                    player.getInventory().setItem(slot, item);
                }
            }
        }
        
        // Remove leaves after 30 sec
        new BukkitRunnable() {
            @Override
            public void run() {
                // Must still be leaves
                if (!loc.getBlock().getType().toString().contains("LEAVES")) {
                    return;
                }
                
                // No repairman/outside base = remove leaves
                if (repairman == 0 || !ConfigUtils.inShield(playerArena, loc, team)) {
                    loc.getBlock().setType(Material.AIR);
                    return;
                }
                
                // Replace blocks with endstone if repairman
                loc.getBlock().setType(Material.END_STONE);
                
            }
        }.runTaskLater(plugin, 30 * 20 * durationMultiplier);
    }

    private void spawnCanopy(Player player, Arena playerArena, String utility, ItemStack hand) {
        new BukkitRunnable() {
            @Override
            public void run() {

                // Make sure the canopy spawn hasn't already been cancelled
                if (!canopy_cooldown.contains(player.getUniqueId())) {
                    return;
                }

                canopy_cooldown.remove(player.getUniqueId());

                // Ignore offline players. Obviously
                if (!player.isOnline()) {
                    return;
                }

                // Ignore if player is not holding a canopy
                if (hand.getType() != Material.ENDER_EYE) {
                    ConfigUtils.sendConfigMessage("messages.canopy-cancel", player, null, null);
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
                if (SchematicManager.spawnNBTStructure("canopy-1", spawnLoc, isRedTeam(player), mapName)) {
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
                    
                    hand.setAmount(hand.getAmount() - 1);
                    for (Player players : player.getWorld().getPlayers()) {
                        ConfigUtils.sendConfigSound("spawn-canopy", players, spawnLoc);
                    }
                    playerArena.getPlayerInArena(player.getUniqueId()).incrementUtility();
                    despawnCanopy(spawnLoc, 5);
                } else {
                    ConfigUtils.sendConfigMessage("messages.cannot-place-structure", player, null, null);
                }

            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), 20);
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
        String structureName = getStringFromItem(hand, "item-structure");
        if (structureName == null) {
            return;
        }

        // Add meta for structure identification
        thrown.customName(Component.text(structureName));

        // Schedule structure spawn after 1 second if snowball is still alive
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!thrown.isDead()) {
                    if (!thrower.isOnline()) {
                        return;
                    }
                    spawnUtility(structureName, thrown.getLocation(), thrower, thrown, playerArena);
                }
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), 20);
    }

    /** Utility should go through players */
    @EventHandler
    public void handleUtilityCollisions(ProjectileHitEvent event) {
        // Ensure we are tracking a utility thrown by a player
        if (!(event.getEntity().getType() == EntityType.SNOWBALL ||
              event.getEntity().getType() == EntityType.EGG ||
              event.getEntity().getType() == EntityType.ENDER_PEARL ||
              event.getEntity().getType() == EntityType.SPLASH_POTION)) {
            return;
        }

        Projectile thrown = event.getEntity();

        // Make sure a player threw this projectile
        // If it has a custom name, it definitely is a missile wars item
        if (!(thrown.getShooter() instanceof Player) || (thrown.customName() == null)) {
            return;
        }

        // Make sure it's in an arena
        Player thrower = (Player) thrown.getShooter();
        Arena playerArena = getPlayerArena(thrower);
        if (playerArena == null) {
            return;
        }
        
        // Check if prickly
        if (MissileWarsPlugin.getPlugin().getJSON().getAbility(thrower.getUniqueId(), "prickly") > 0) {
            return;
        }

        // Make them go through entities
        if (event.getHitEntity() != null) {
            event.setCancelled(true);
            return;
        }
    }

    /** Handle spawning of utility structures */
    public void spawnUtility(String structureName, Location spawnLoc, Player thrower, ThrowableProjectile thrown, Arena playerArena) {
        String mapName = "default-map";
        if (playerArena.getMapName() != null) {
            mapName = playerArena.getMapName();
        }
        if (SchematicManager.spawnNBTStructure(structureName, spawnLoc, isRedTeam(thrower), mapName)) {
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
            } 
            for (Player players : thrower.getWorld().getPlayers()) {
                ConfigUtils.sendConfigSound(sound, players, spawnLoc);
            }
            // Repairman
            int repairman = MissileWarsPlugin.getPlugin().getJSON().getAbility(thrower.getUniqueId(), "repairman");
            if (repairman > 0) {
                double percentage = ConfigUtils.getAbilityStat("Architect.passive.repairman", repairman, "percentage") / 100;
                Random random = new Random();
                if (random.nextDouble() < percentage) {
                    ItemStack item = thrown.getItem();
                    thrower.getInventory().addItem(item);
                }
            }
        } else {
            ConfigUtils.sendConfigMessage("messages.cannot-place-structure", thrower, null, null);
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
        String utility = getStringFromItem(hand, "item-utility");

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

        Block hitBlock = event.getHitBlock();

        // Spawn some water
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
                SchematicManager.spawnNBTStructure("canopy-1", newSpawn, isRedTeam(thrower), "default-map");
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
                            SchematicManager.spawnNBTStructure("obsidianshieldclear-1", location, red, mapName, false);
                            for (Player player : location.getWorld().getPlayers()) {
                                ConfigUtils.sendConfigSound("break-obsidian-shield", player, location);
                            }
                        } else if (finalDuration % 2 == 0) {
                            SchematicManager.spawnNBTStructure("obsidianshielddeplete-1", location, red, mapName, false);
                        } else {
                            SchematicManager.spawnNBTStructure("obsidianshield-1", location, red, mapName, false);
                        }
                    }
                }
            }.runTaskLater(MissileWarsPlugin.getPlugin(), i * 10L);
        }
    }
}
