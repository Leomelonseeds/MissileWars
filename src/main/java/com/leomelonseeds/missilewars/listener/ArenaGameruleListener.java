package com.leomelonseeds.missilewars.listener;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
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
import org.bukkit.event.entity.ProjectileHitEvent;
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
import com.leomelonseeds.missilewars.decks.DeckItem;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CosmeticUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;

import io.papermc.paper.event.player.AsyncChatEvent;
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
    
    // Stop players from regen health in opponent base
    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {

        if (event.getRegainReason() != RegainReason.SATIATED) { 
            return; 
        }

        //Check if entity is player
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        // Check if player is in arena
        Player player = (Player) event.getEntity();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            return;
        }
        
        String team = playerArena.getTeam(player.getUniqueId());
        if (team.equals("no team")) {
            return;
        }
        
        String opponentTeam = team.contains("red") ? "blue" : "red";
        if (ConfigUtils.inShield(playerArena, player.getLocation(), opponentTeam, 8)) {
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
        
        // Stop fall damage on game start
        if (event.getCause() == DamageCause.FALL) {
            Player player = (Player) event.getEntity();
            ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
            Arena playerArena = manager.getArena(player.getUniqueId());
            if (playerArena != null && playerArena.getSecondsUntilStart() >= -1) {
                event.setCancelled(true);
            }
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
        
        player.setBedSpawnLocation(playerArena.getPlayerSpawn(player), true);
        Component deathMessage = event.deathMessage();
        event.deathMessage(Component.text(""));
        
        if (playerArena.getTeam(player.getUniqueId()).equals("no team")) {
            return;
        }

        // Count death if player is on a team
        playerArena.getPlayerInArena(player.getUniqueId()).incrementDeaths();
        
        // Find the player's killer
        Player killer = player.getKiller();
        if (killer == null && player.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) player.getLastDamageCause();
            killer = ConfigUtils.getAssociatedPlayer(damageEvent.getDamager(), playerArena);
        }

        // Find killer and increment kills
        if (killer != null) {
            String team1 = playerArena.getTeam(player.getUniqueId());
            String team2 = playerArena.getTeam(killer.getUniqueId());
            if (!(killer.equals(player) || team1.equals(team2))) {
                playerArena.getPlayerInArena(killer.getUniqueId()).incrementKills();
                ConfigUtils.sendConfigSound("player-kill", killer);
            }
        }
        
        
        // Send death message
        Component customDeathMessage = CosmeticUtils.getDeathMessage(player, killer);
        for (Player p : player.getWorld().getPlayers()) {
            if (p.hasPermission("umw.vanilladeathmessages")) {
                p.sendMessage(deathMessage);
            } else {
                p.sendMessage(customDeathMessage);
            }
        }
        
        // Remove canopy cooldown so canopy doesn't get used
        CustomItemListener.canopy_cooldown.remove(player.getUniqueId());

        // Un-obstruct spawns
        Location spawn1 = playerArena.getPlayerSpawn(player);
        Location spawn2 = spawn1.clone().add(0, 1, 0);
        Location spawn3 = spawn1.clone().add(1, 0, 0);
        Location spawn4 = spawn1.clone().add(-1, 0, 0);
        Location spawn5 = spawn1.clone().add(0, 0, 1);
        Location spawn6 = spawn1.clone().add(0, 0, -1);
        for (Location l : new Location[] {spawn1, spawn2}) {
            l.getBlock().setType(Material.AIR);
        }
        for (Location l : new Location[] {spawn3, spawn4, spawn5, spawn6}) {
            Block b = l.getBlock();
            if (b.getType() == Material.WATER || b.getType() == Material.LAVA) {
                b.setType(Material.AIR);
            }
        }
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
            
            player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 30 * 60 * 20, level * 2 - 1));
        }
        
        // Regive vanguard passives
        int bunny = plugin.getJSON().getAbility(player.getUniqueId(), "bunny");
        if (bunny > 0) {
            int level = (int) ConfigUtils.getAbilityStat("Vanguard.passive.bunny", bunny, "amplifier");
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 30 * 60 * 20, level));
        }
        
        int adrenaline = plugin.getJSON().getAbility(player.getUniqueId(), "adrenaline");
        if (adrenaline > 0) {
            int level = (int) ConfigUtils.getAbilityStat("Vanguard.passive.adrenaline", adrenaline, "amplifier");
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30 * 60 * 20, level));
        }
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
        
        if (arena.getTeam(player.getUniqueId()) == "no team") {
            return;
        }
        
        ItemStack toConsume = event.getConsumable();
        DeckItem di = arena.getPlayerInArena(player.getUniqueId()).getDeck().getDeckItem(toConsume);
        if (di != null) {
            di.consume();
        }
        
        if (MissileWarsPlugin.getPlugin().getJSON().getAbility(player.getUniqueId(), "longshot") > 0) {
            bowShots.put(player, player.getLocation());
            // 5 seconds should be enough for a bow shot, riiiight
            Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
                bowShots.remove(player);
            }, 100);
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
                // Check for boosterball (non-incendiary fireball) and multiply knockback
                // If not boosterball, deal no damage to entities
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
                } else {
                    event.setDamage(0.0001);
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
        if (arena.getTeam(player.getUniqueId()).equals(arena.getTeam(damager.getUniqueId())) &&
           !arena.getTeam(player.getUniqueId()).equals("no team")) {
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
                    if (bowShots.containsKey(damager) && bowShots.get(damager).getWorld().equals(damager.getWorld())) {
                        int longshot = plugin.getJSON().getAbility(damager.getUniqueId(), "longshot");
                        double plus = ConfigUtils.getAbilityStat("Sentinel.passive.longshot", longshot, "plus");
                        double max = ConfigUtils.getAbilityStat("Sentinel.passive.longshot", longshot, "max");
                        double dmg = event.getDamage();
                        double distance = bowShots.get(damager).distance(player.getLocation());
                        double cutoff = ConfigUtils.getAbilityStat("Sentinel.passive.longshot", longshot, "cutoff");
                        
                        // Calculate damage
                        if (distance >= cutoff) {
                            double extradistance = distance - cutoff;
                            double extradmg = Math.min(extradistance * plus, max);
                            event.setDamage(dmg + extradmg);
                        }
                        
                        bowShots.remove(damager);
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
                    int maxmultiplier = (int) ConfigUtils.getAbilityStat("Architect.passive.prickly", prickly, "multiplier");
                    
                    // Make sure its custom item
                    if ((item.getItemMeta() == null) || !item.getItemMeta().getPersistentDataContainer().has(
                            new NamespacedKey(plugin, "item-structure"), PersistentDataType.STRING)) {
                        return;
                    }
                    
                    // Get level of the item
                    String itemString = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,
                            "item-structure"), PersistentDataType.STRING);
                    String[] args = itemString.split("-");
                    int multiplier = Math.min(Integer.parseInt(args[1]), maxmultiplier);
                    
                    // Set custom damage and knockback
                    event.setDamage(0.0001);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Vector velocity = player.getVelocity();
                        double velx = velocity.getX() * multiplier;
                        double velz = velocity.getZ() * multiplier;
                        double vely = velocity.getY();
                        player.setVelocity(new Vector(velx, vely, velz));
                    }, 1);
                    
                    // Give item back on successful hit
                    damager.getInventory().addItem(item);
                }
            }
        }
    }

    /** Make sure players can't create portals */
    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {

        // Ensure it was in an arena world
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(event.getWorld());
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
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(event.getPlayer().getWorld());
        if (possibleArena == null) {
            return;
        }

        event.setCancelled(true);
    }

    /** Handle fireball and TNT explosions. */
    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        // Ensure it was in an arena world
        Arena possibleArena = MissileWarsPlugin.getPlugin().getArenaManager().getArena(event.getEntity().getWorld());
        if (possibleArena == null) {
            return;
        }

        // Check for shield breaks
        Bukkit.getScheduler().runTaskAsynchronously(MissileWarsPlugin.getPlugin(), 
                () -> event.blockList().forEach(block -> possibleArena.registerShieldBlockEdit(block.getLocation(), false)));
        
        // Fireball checks
        EntityType entity = event.getEntityType();
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
                    possibleArena.registerPortalBreak(block.getLocation(), event.getEntity());
                }
            });
        }
        
        // Register explosion to tracker
        possibleArena.getTracker().registerExplosion(event);
    }

    /** Handle shield block breaks breaks. */
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Block block = event.getBlock();
        Player player = event.getPlayer();
        // Ensure it was in an arena world
        Arena possibleArena = plugin.getArenaManager().getArena(block.getWorld());
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
        
        int deconstructor = plugin.getJSON().getAbility(player.getUniqueId(), "deconstructor");
        if (deconstructor <= 0) {
            return;
        }
        
        // Check if deconstructor can break block
        String type = block.getType().toString();
        List<String> whitelist = plugin.getConfig().getStringList("deconstructor-blocks");
        boolean proceed = false;
        for (String b : whitelist) {
            if (type.contains(b)) {
                proceed = true;
                break;
            }
        }
        
        if (!proceed) {
            return;
        }
        
        Random random = new Random();
        double percentage = ConfigUtils.getAbilityStat("Architect.passive.deconstructor", deconstructor, "percentage") / 100;
        if (random.nextDouble() < percentage) {
            ItemStack item = new ItemStack(block.getType());
            possibleArena.getWorld().dropItemNaturally(block.getLocation(), item);
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
    
    /**
     * Kick AFK players. Conditions for successful kick:
     * - Be in a team in a running game
     * - Be in the waiting lobby while game is not running
     * In all other cases, player is fine.
     * `
     * @param event
     */
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
        
        MissileWarsPlayer mwPlayer = arena.getPlayerInArena(player.getUniqueId());
        if (arena.isSpectating(mwPlayer)) {
            return;
        }
        
        // Kick players only if they are in pre-game and not spectating
        if (arena.getTeam(player.getUniqueId()).equals("no team") && (arena.isRunning() || arena.isResetting())) {
            return;
        }
        
        arena.announceMessage("messages.afk-removal", mwPlayer);
        arena.removePlayer(player.getUniqueId(), true);
    }
    
    Set<Player> saidGG = new HashSet<>();
    
    // Add exp if GG
    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (saidGG.contains(player)) {
            return;
        }
         
        ArenaManager arenaManager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = arenaManager.getArena(player.getUniqueId());
        if (arena == null) {
            return;
        }
        
        if (!(arena.isResetting() || arena.isWaitingForTie())) {
            return;
        }
        
        if (arena.getTeam(player.getUniqueId()).equals("no team")) {
            return;
        }

        String message = ConfigUtils.toPlain(event.message());
        if (Pattern.matches("good game|g+s*(wp)?!*", message.toLowerCase())) {
            // Add 1 EXP to player
            RankUtils.addExp(player, 1);
            ConfigUtils.sendConfigMessage("messages.gg-exp", player, null, null);
            saidGG.add(player);
            
            // Disallow giving xp for as long as victory wait time
            long waitTime = MissileWarsPlugin.getPlugin().getConfig().getInt("victory-wait-time") * 20L;
            Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
                saidGG.remove(player);
            }, waitTime);
            return;
        }
    }
    
    // ---------------------------------------------------------
    // The next section ignites tnt if b36 hit with flame arrow
    // ---------------------------------------------------------
    
    public static Map<Location, Location> tnt = new HashMap<>();
    
    @EventHandler
    public void extendTNT(BlockPistonExtendEvent e) {
        e.getBlocks().forEach(b -> addToList(b, e));
    }
    
    @EventHandler
    public void retractTNT(BlockPistonRetractEvent e) {
        e.getBlocks().forEach(b -> addToList(b, e));
    }
    
    private void addToList(Block b, BlockPistonEvent e) {
        if (b.getType() != Material.TNT) {
            return;
        }
        
        // Add location of where the block WILL BE to a list
        Location loc = b.getLocation().toCenterLocation();
        Vector direction = e.getDirection().getDirection().normalize();
        final Location finalLoc = loc.clone().add(direction);
        tnt.put(finalLoc, loc);
        
        // Update the spawning location of the tnt such that it spawns smoothly
        for (int i = 1; i <= 3; i++) {
            Vector toAdd = direction.clone().multiply(0.33 * i);
            int index = i;
            Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
                if (!tnt.containsKey(finalLoc)) {
                    return;
                }
                if (index < 3) {
                    tnt.get(finalLoc).add(toAdd);
                }
                else {
                    tnt.remove(finalLoc);
                }
            }, i);
        }
    }
    
    @EventHandler
    public void igniteTNT(ProjectileHitEvent e) {
        if (e.getEntityType() != EntityType.ARROW) {
            return;
        }
        
        if (((Arrow) e.getEntity()).getFireTicks() <= 0) {
            return;
        }
        
        if (e.getHitBlock() == null) {
            return;
        }
        
        Block b = e.getHitBlock();
        if (b.getType() != Material.MOVING_PISTON) {
            return;
        }
        
        Location loc = b.getLocation().toCenterLocation();
        if (!tnt.containsKey(loc)) {
            return;
        }
        
        b.setType(Material.AIR);
        Location spawnLoc = tnt.remove(loc).subtract(0, 0.5, 0);
        TNTPrimed primed = (TNTPrimed) b.getWorld().spawnEntity(spawnLoc, EntityType.PRIMED_TNT);
        primed.setFuseTicks(80);
    }
}
