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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.minecart.ExplosiveMinecart;
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
import org.bukkit.event.player.PlayerGameModeChangeEvent;
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
import com.leomelonseeds.missilewars.arenas.ClassicArena;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.decks.Passive;
import com.leomelonseeds.missilewars.decks.Passive.Stat;
import com.leomelonseeds.missilewars.listener.handler.CanopyManager;
import com.leomelonseeds.missilewars.listener.handler.EnderSplashManager;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
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
    private static List<PotionEffectType> effects = null;

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
        Arena playerArena = ArenaUtils.getArena(player);
        if (playerArena == null) {
            return;
        }
        
        TeamName team = playerArena.getTeam(player.getUniqueId());
        if (team == TeamName.NONE) {
            return;
        }
        
        if (ArenaUtils.inShield(playerArena, player.getLocation(), team.getOpposite(), 8)) {
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
        
        Arena playerArena = ArenaUtils.getArena(player);
        if (playerArena == null) {
            return;
        }
        
        // Don't handle players with no team
        if (playerArena.getTeam(player.getUniqueId()) == TeamName.NONE) {
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
        Arena playerArena = ArenaUtils.getArena(player);
        if (playerArena == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> player.teleport(ConfigUtils.getSpawnLocation()), 1);
            return;
        }

        Location spawn = playerArena.getPlayerSpawn(player);
        ItemStack canopy = CanopyManager.getInstance().removePlayer(player);
        EnderSplashManager.getInstance().removePlayer(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.teleport(spawn);
            player.setFireTicks(0);
            player.setSaturation(5F);
            player.removePotionEffect(PotionEffectType.POISON);
            player.removePotionEffect(PotionEffectType.GLOWING);
            player.removePotionEffect(PotionEffectType.SLOW);
            if (canopy != null) {
                InventoryUtils.regiveItem(player, canopy);
            }
        }, 1);
        Component deathMessage = event.deathMessage();
        event.deathMessage(ConfigUtils.toComponent(""));
        if (playerArena.getTeam(player.getUniqueId()) == TeamName.NONE) {
            return;
        }

        // Count death if player is on a team
        MissileWarsPlayer mwp = playerArena.getPlayerInArena(player.getUniqueId());
        mwp.incrementDeaths();
        
        // Find the player's killer
        Player killer = player.getKiller();
        if (killer == null && player.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) player.getLastDamageCause();
            killer = ArenaUtils.getAssociatedPlayer(damageEvent.getDamager(), playerArena);
        }

        // Find killer and increment kills
        if (killer != null) {
            TeamName team1 = playerArena.getTeam(player.getUniqueId());
            TeamName team2 = playerArena.getTeam(killer.getUniqueId());
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
        Arena arena = ArenaUtils.getArena(player);
        if ((arena == null) || !arena.isRunning()) {
            return;
        }
        
        if (arena.getTeam(player.getUniqueId()) == TeamName.NONE) {
            return;
        }
        
        // Berserker user
        UUID uuid = player.getUniqueId();
        if (event.getBow().getType() == Material.CROSSBOW) {
            player.setCooldown(Material.CROSSBOW, player.getCooldown(Material.ARROW));

            // Spiked Quiver
            int spiked = plugin.getJSON().getAbility(uuid, Passive.SPIKED_QUIVER);
            if (spiked > 0) {
                if (effects == null) {
                    effects = List.of(new PotionEffectType[] {
                        PotionEffectType.BLINDNESS,
                        PotionEffectType.WEAKNESS,
                        PotionEffectType.WITHER,
                        PotionEffectType.POISON,
                        PotionEffectType.CONFUSION,
                        PotionEffectType.SLOW,
                        PotionEffectType.SLOW_DIGGING,
                    });
                }
                
                Arrow arrow = (Arrow) event.getProjectile();
                int amplifier = (int) ConfigUtils.getAbilityStat(Passive.SPIKED_QUIVER, spiked, Stat.AMPLIFIER);
                int duration = (int) (ConfigUtils.getAbilityStat(Passive.SPIKED_QUIVER, spiked, Stat.DURATION) * 20);
                Random rand = new Random();
                PotionEffectType type = effects.get(rand.nextInt(effects.size()));
                arrow.addCustomEffect(new PotionEffect(type, duration, amplifier), true);
                return;
            }
            
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
        if (plugin.getJSON().getAbility(uuid, Passive.LONGSHOT) > 0) {
            bowShots.put(player, player.getLocation());
            // 5 seconds should be enough for a bow shot, riiiight
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                bowShots.remove(player);
            }, 100);
            return;
        }
        
        // Spectral arrows
        int spectral = plugin.getJSON().getAbility(uuid, Passive.SPECTRAL_ARROWS);
        if (spectral > 0 && event.getProjectile() instanceof SpectralArrow) {
            SpectralArrow arrow = (SpectralArrow) event.getProjectile();
            int duration = (int) ConfigUtils.getAbilityStat(Passive.SPECTRAL_ARROWS, spectral, Stat.DURATION);
            arrow.setGlowingTicks(duration * 20);
            return;
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
        Arena arena = ArenaUtils.getArena(player);
        if ((arena == null) || !arena.isRunning()) {
            return;
        }
        
        if (arena.getTeam(player.getUniqueId()) == TeamName.NONE) {
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
        if (plugin.getJSON().getAbility(player.getUniqueId(), Passive.CREEPERSHOT) > 0) {
            ItemStack offhand = inv.getItemInOffHand();
            if (offhand.getType() != Material.CREEPER_HEAD || player.hasCooldown(offhand.getType())) {
                return;
            }
            
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
            
            return;
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
        Arena arena = ArenaUtils.getArena(player);
        if (arena == null || !arena.isRunning()) {
            return;
        }
        
        // Fetch attacker player - if projectile, get projectile's shooter
        Player damager = null;
        boolean isProjectile = false;
        Entity eventDamager = event.getDamager();
        if (eventDamager.getType() == EntityType.PLAYER) {
            damager = (Player) event.getDamager();
        } else {
            // If the damager is explosive, handle right here. No need for
            // additional checks
            if (eventDamager instanceof Explosive || 
                eventDamager instanceof ExplosiveMinecart ||
                eventDamager instanceof Creeper) {
                // Check bers rocketeer
                int rocketeer = plugin.getJSON().getAbility(player.getUniqueId(), Passive.ROCKETEER);
                if (rocketeer > 0) {
                    // If boots have UNBREAKING, it means the custom blastprot is set (see DeckManager)
                    // In this case, the level of unbreaking corresponds to the level of blastprot
                    // We must also provide manual damage reduction
                    ItemStack boots = player.getInventory().getBoots();
                    int blastprot = boots.getEnchantmentLevel(Enchantment.DURABILITY);
                    if (blastprot > 0) {
                        event.setDamage(event.getDamage() * (1 - 0.08 * blastprot));
                    }
                    
                    // Apply extra velocity from rocketeer if available and restore enchantment
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        double rmult = ConfigUtils.getAbilityStat(Passive.ROCKETEER, rocketeer, Stat.MULTIPLIER);
                        Vector velocity = player.getVelocity();
                        double velx = velocity.getX() * rmult;
                        double velz = velocity.getZ() * rmult;
                        player.setVelocity(new Vector(velx, velocity.getY(), velz));
                    }, 1);
                }
                
                // Fireballs should not do dmg to players
                if (eventDamager instanceof Fireball) {
                    event.setDamage(0.0001);
                }
                
                return;
            }
            
            // Otherwise, if damager is a non-explosive projectile, assign source here
            if (eventDamager instanceof Projectile projectile) {
                if ((projectile.getShooter() instanceof Player)) {
                    damager = (Player) projectile.getShooter();
                    isProjectile = true;
                }
            }
            
            if (damager == null) {
                return;
            }
        }
        
        if (CanopyManager.getInstance().isFrozen(damager)) {
            event.setCancelled(true);
            return;
        }
        
        // Bow boosting calculation
        if (player.equals(damager)) {
            
        }

        // Stop event if damager and damaged are on same team
        TeamName team = arena.getTeam(player.getUniqueId());
        if (team.equals(arena.getTeam(damager.getUniqueId())) && team != TeamName.NONE) {
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
                    int longshot = plugin.getJSON().getAbility(damager.getUniqueId(), Passive.LONGSHOT);
                    double plus = ConfigUtils.getAbilityStat(Passive.LONGSHOT, longshot, Stat.PLUS);
                    double max = ConfigUtils.getAbilityStat(Passive.LONGSHOT, longshot, Stat.MAX);
                    double dmg = event.getDamage();
                    double distance = bowShots.get(damager).distance(player.getLocation());
                    double cutoff = ConfigUtils.getAbilityStat(Passive.LONGSHOT, longshot, Stat.CUTOFF);
                    
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
                int prickly = plugin.getJSON().getAbility(damager.getUniqueId(), Passive.PRICKLY_PROJECTILES);
                if (prickly == 0) {
                    return;
                }

                ThrowableProjectile proj = (ThrowableProjectile) projectile;
                ItemStack item = proj.getItem();
                int maxmultiplier = (int) ConfigUtils.getAbilityStat(Passive.PRICKLY_PROJECTILES, prickly, Stat.MULTIPLIER);
                
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
                InventoryUtils.regiveItem(damager, item);
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
        
        int deconstructor = plugin.getJSON().getAbility(player.getUniqueId(), Passive.DECONSTRUCTOR);
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
        double percentage = ConfigUtils.getAbilityStat(Passive.DECONSTRUCTOR, deconstructor, Stat.PERCENTAGE) / 100;
        if (random.nextDouble() < percentage) {
            ItemStack item = new ItemStack(block.getType());
            InventoryUtils.regiveItem(player, item);
        }
    }

    /** Stop chickens spawning from eggs */
    @EventHandler
    public void onEgg(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == SpawnReason.EGG) {
            event.setCancelled(true);
        }
    }
    
    /** 
     * Experimental setting to give players poison if they go too high.
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        checkBounds(event.getPlayer(), event.getFrom(), event.getTo());
    }
    
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        checkBounds(event.getPlayer(), event.getFrom(), event.getTo());
    }
    
    private void checkBounds(Player player, Location from, Location to) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        
        Arena arena = ArenaUtils.getArena(player);
        if (arena == null) {
            return;
        }
        
        if (arena.getTeam(player.getUniqueId()) == TeamName.NONE) {
            return;
        }
        
        // Straight up kill people
        double tooLobby = plugin.getConfig().getDouble("barrier.center.x");
        double wayTooHigh = plugin.getConfig().getDouble("max-height");
        if (to.getBlockY() >= wayTooHigh || to.getBlockX() >= tooLobby) {
            ConfigUtils.sendConfigMessage("messages.out-of-bounds-death", player, null, null);
            player.setHealth(0);
            return;
        }
        
        // Experimental poison 
        if (plugin.getConfig().getBoolean("experimental.poison")) {
            double toohigh = ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), "too-high");
            if (from.getBlockY() <= toohigh - 1 && to.getBlockY() >= toohigh) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.getLocation().getBlockY() >= toohigh) {
                        ConfigUtils.sendConfigMessage("messages.poison", player, null, null);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 60 * 30, 1, false));
                    }
                }, 20);
                return;
            }
            
            if (to.getBlockY() <= toohigh - 1 && from.getBlockY() >= toohigh) {
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
        
        @SuppressWarnings("deprecation")
        Player player = event.getAffected().getBase();
        Arena arena = ArenaUtils.getArena(player);
        if (arena == null) {
            return;
        }
        
        MissileWarsPlayer mwPlayer = arena.getPlayerInArena(player.getUniqueId());
        if (arena.isSpectating(mwPlayer)) {
            return;
        }
        
        // Kick players only if they are in pre-game and not spectating
        if (arena.getTeam(player.getUniqueId()) == TeamName.NONE && 
                (arena.isRunning() || arena.isResetting())) {
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
        
        Arena arena = ArenaUtils.getArena(player);
        if (arena == null) {
            return;
        }
        
        if (!(arena.isResetting() || arena.isWaitingForTie())) {
            return;
        }
        
        if (arena.getTeam(player.getUniqueId()) == TeamName.NONE) {
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
}
