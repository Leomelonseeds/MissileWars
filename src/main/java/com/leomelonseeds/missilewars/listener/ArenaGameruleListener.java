package com.leomelonseeds.missilewars.listener;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.ClassicArena;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CosmeticUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;

import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.ess3.api.events.AfkStatusChangeEvent;
import net.kyori.adventure.text.Component;

/** Class to listen for events relating to Arena game rules. */
public class ArenaGameruleListener implements Listener {
    
    public static Map<Player, Location> bowShots = new HashMap<>();
    public static Set<Player> saidGG = new HashSet<>();

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

    /** Handle all sorts of deaths. */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
    	//Check if entity is player
    	if (!(event.getEntity() instanceof Player)) {
            return;
        }

        // Cause instant death so player can respawn faster
    	Player player = (Player) event.getEntity();
        if (event.getCause() == DamageCause.VOID) {
            event.setDamage(40.0);
        }
        
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            return;
        }
        
        // Don't handle players with no team
        if (playerArena.getTeam(player.getUniqueId()).equals("no team")) {
            return;
        }
        
        if (playerArena.getPlayerInArena(player.getUniqueId()).justSpawned()) {
            event.setCancelled(true);
            return;
        }
    }

    /** Handle player deaths. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.setCancelled(true);
        event.setShouldPlayDeathSound(true);
        event.setReviveHealth(20);
        Player player = event.getEntity();
        
        // Make player undrunk
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "brew " + player.getName() + " 0 10");
        
        // Check if player was killed in an Arena
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        ArenaManager manager = plugin.getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> player.teleport(ConfigUtils.getSpawnLocation()), 1);
            return;
        }

        Location spawn = playerArena.getPlayerSpawn(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.teleport(spawn);
            player.setFireTicks(0);
            player.setSaturation(5F);
            player.removePotionEffect(PotionEffectType.POISON);
            player.removePotionEffect(PotionEffectType.GLOWING);
            player.removePotionEffect(PotionEffectType.SLOW);
        }, 1);
        Component deathMessage = event.deathMessage();
        event.deathMessage(ConfigUtils.toComponent(""));
        if (playerArena.getTeam(player.getUniqueId()).equals("no team")) {
            return;
        }

        // Count death if player is on a team
        MissileWarsPlayer mwp = playerArena.getPlayerInArena(player.getUniqueId());
        mwp.incrementDeaths();
        
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
        mwp.setJustSpawned();
        player.setKiller(null);

        // Un-obstruct spawns
        Location spawn2 = spawn.clone().add(0, 1, 0);
        for (Location l : new Location[] {spawn, spawn2}) {
            l.getBlock().setType(Material.AIR);
        }
    }
    
    // Handle sentinel longshot bow shoot event + bow cooldowns
    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        // Ensure we are handling a player in an arena
        if (event.getEntityType() != EntityType.PLAYER) {
            return;
        }
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Player player = (Player) event.getEntity();
        ArenaManager arenaManager = plugin.getArenaManager();
        Arena arena = arenaManager.getArena(player.getUniqueId());
        if ((arena == null) || !arena.isRunning()) {
            return;
        }
        
        if (arena.getTeam(player.getUniqueId()).equals("no team")) {
            return;
        }
        
        // Berserker user
        if (event.getBow().getType() == Material.CROSSBOW) {
            player.setCooldown(Material.CROSSBOW, Math.max(player.getCooldown(Material.ARROW), player.getCooldown(Material.TIPPED_ARROW)));
            
            // Check for creepershot
            ItemStack crossbow = event.getBow();
            CrossbowMeta cmeta = (CrossbowMeta) crossbow.getItemMeta();
            boolean charged = false;
            boolean creepershot = false;
            for (ItemStack proj : cmeta.getChargedProjectiles()) {
                if (proj.getType() == Material.FIREWORK_ROCKET) {
                    creepershot = true;
                    if (ConfigUtils.toPlain(proj.displayName()).contains("Charged")) {
                        charged = true;
                    }
                    break;
                }
            }
            
            if (!creepershot) {
                return;
            }
            
            Entity proj = event.getProjectile();
            Location spawnLoc = proj.getLocation();
            Creeper creeper = (Creeper) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.CREEPER);
            if (charged) {
                creeper.setPowered(true);
            }
            creeper.customName(ConfigUtils.toComponent(ConfigUtils.getFocusName(player) + "'s &7Creeper"));
            creeper.setCustomNameVisible(true);
            creeper.setVelocity(proj.getVelocity().multiply(2.5));
            proj.remove();
            
            ConfigUtils.sendConfigSound("creepershot", player);
            return;
        }
        
        ItemStack toConsume = event.getConsumable();
        ItemStack[] contents = player.getInventory().getContents();
        int slot = -1;
        for (int i = 0; i < contents.length; i++) {
            if (toConsume.isSimilar(contents[i])) {
                slot = i;
            }
        }
        InventoryUtils.consumeItem(player, arena, toConsume, slot);
        
        // Longshot
        if (plugin.getJSON().getAbility(player.getUniqueId(), "longshot") > 0) {
            bowShots.put(player, player.getLocation());
            // 5 seconds should be enough for a bow shot, riiiight
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                bowShots.remove(player);
            }, 100);
        }
        
        // Spectral arrows
        int spectral = plugin.getJSON().getAbility(player.getUniqueId(), "spectral");
        if (spectral > 0 && event.getProjectile() instanceof SpectralArrow) {
            SpectralArrow arrow = (SpectralArrow) event.getProjectile();
            int duration = (int) ConfigUtils.getAbilityStat("Sentinel.passive.spectral", spectral, "duration");
            arrow.setGlowingTicks(duration * 20);
        }
    }
    
    // Handle crossbow load cooldowns
    @EventHandler
    public void onCrossbowLoad(EntityLoadCrossbowEvent event) {
        // Ensure we are handling a player in an arena
        if (event.getEntityType() != EntityType.PLAYER) {
            return;
        }
        Player player = (Player) event.getEntity();
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        ArenaManager arenaManager = plugin.getArenaManager();
        Arena arena = arenaManager.getArena(player.getUniqueId());
        if ((arena == null) || !arena.isRunning()) {
            return;
        }
        
        if (arena.getTeam(player.getUniqueId()).equals("no team")) {
            return;
        }
        
        // At this point, the player is 100% using berserker. Obtain the arrow item
        PlayerInventory inv = player.getInventory();
        ItemStack toConsume = null;
        int slot = -1;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            if (item.getType() == Material.ARROW || item.getType() == Material.TIPPED_ARROW) {
                toConsume = item;
                slot = i;
                break;
            }
        }
        
        InventoryUtils.consumeItem(player, arena, toConsume, slot);
        
        // Creepershot
        if (plugin.getJSON().getAbility(player.getUniqueId(), "creepershot") > 0) {
            ItemStack offhand = inv.getItemInOffHand();
            if (offhand.getType() == Material.CREEPER_HEAD && !player.hasCooldown(offhand.getType())) {
                // Prepare fake creeper item
                boolean charged = ConfigUtils.toPlain(offhand.displayName()).contains("Charged");
                ItemStack creeper = new ItemStack(Material.FIREWORK_ROCKET);
                ItemMeta meta = creeper.getItemMeta();
                meta.displayName(ConfigUtils.toComponent(charged ? "&fCharged Creeper" : "&fCreeper"));
                creeper.setItemMeta(meta);
                
                // Load "creeper" into crossbow
                InventoryUtils.consumeItem(player, arena, offhand, -1);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ItemStack crossbow = event.getCrossbow();
                    CrossbowMeta cmeta = (CrossbowMeta) crossbow.getItemMeta();
                    List<ItemStack> newProjs = new ArrayList<>();
                    for (int i = 0; i < cmeta.getChargedProjectiles().size(); i++) {
                        newProjs.add(new ItemStack(creeper));
                    }
                    cmeta.setChargedProjectiles(newProjs);
                    crossbow.setItemMeta(cmeta);
                }, 1);
            }
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
            if (!(projectile.getShooter() instanceof Player)) {
                return;
            }
            
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
                    damager.sendMessage(ConfigUtils.toComponent(msg));
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
                Item newitem = damager.getWorld().dropItem(damager.getLocation(), item);
                newitem.setPickupDelay(0);
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
        else if ((entity == EntityType.PRIMED_TNT || entity == EntityType.MINECART_TNT ||
                 entity == EntityType.CREEPER) && possibleArena instanceof ClassicArena) {
            event.blockList().forEach(block -> {
                // Register portal brake if block was broken
                if (block.getType() == Material.NETHER_PORTAL) {
                    ((ClassicArena) possibleArena).registerPortalBreak(block.getLocation(), event.getEntity());
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
        String type = block.getType().toString();
        if (type.contains("OBSIDIAN") || type.equals("NETHER_PORTAL")) {
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
            Item newitem = player.getWorld().dropItem(player.getLocation(), item);
            newitem.setPickupDelay(0);
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
     *  Also handles glow effect in opponent base
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
        
        String team = arena.getTeam(player.getUniqueId());
        if (team.equals("no team")) {
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
                }, 20);
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
    
    // Reset cooldowns if player goes in creative
    @EventHandler
    public void onGamemode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.CREATIVE) {
            InventoryUtils.resetCooldowns(event.getPlayer());
        }
    }
    
    // ---------------------------------------------------------
    // This section ignites tnt if b36 hit with flame arrow
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
    
    // ------------------------------------------------
    // This section handles jump pads (code stolen from HubBasics)
    // ------------------------------------------------
    
    @EventHandler
    public void onPressurePlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }
        
        if (event.getClickedBlock().getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals("world")) {
            return;
        }

        double radians = Math.toRadians(player.getLocation().getYaw());
        double x = -Math.sin(radians) * 1.5;
        double y = 1.0;
        double z = Math.cos(radians) * 1.5;
        player.setVelocity(new Vector(x, y, z));
        ConfigUtils.sendConfigSound("lobby-plate", player);
        event.setCancelled(true);
    }
    
    // --------------------------------------------------
    // This section helps high-ping users with fireball deflections
    // event is high priority to execute after player drop/interact
    // --------------------------------------------------
    
    public static Set<UUID> notLeftClick = new HashSet<>();
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        // Cancel if a left click was recently detected
        Player player = event.getPlayer();
        if (notLeftClick.contains(player.getUniqueId())) {
            return;
        }

        Entity target = player.getTargetEntity(2); // 2 is enough for 3 block reach (for some reason)
        if (target == null) {
            return;
        }
        
        // Handle dragon fireballs by registering an EDBEE for the handler
        if (target instanceof Slime) {
            @SuppressWarnings("deprecation")
            EntityDamageByEntityEvent extraEvent = new EntityDamageByEntityEvent(player, target, DamageCause.ENTITY_ATTACK, 0.001);
            Bukkit.getPluginManager().callEvent(extraEvent);
            return;
        }
        
        // Thank you so much mister CAG2 for suggesting Player#attack, absolute genius if you ask me
        if (target instanceof Fireball) {
            player.attack(target);
        }
    }
}
