package com.leomelonseeds.missilewars.utilities;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.decks.DeckStorage;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

public class ArenaUtils {

    /**
     * Determine if a player is out of bounds
     * 
     * @param player
     * @param arena
     * @return
     */
    public static boolean outOfBounds(Player player, Arena arena) {
        if (arena == null || !arena.isRunning() || arena.isWaitingForTie()) {
            return false;
        }
        double toohigh = ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), "too-high");
        double toofar = ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), "too-far");
        Location loc = player.getLocation();
        return loc.getBlockY() > toohigh || loc.getBlockX() < toofar;
    }

    /**
     * Gets the cause/associated player of an explosive entity
     * mainly used for tracking kills and broken portals
     * 
     * @param killerEntity
     * @param arena
     * @return
     */
    public static Player getAssociatedPlayer(Entity killerEntity, Arena arena) {
        Player player = null;
        if (killerEntity.getType() == EntityType.CREEPER) {
            Creeper creeper = (Creeper) killerEntity;
            if (creeper.isCustomNameVisible()) {
                String name = ConfigUtils.removeColors(ConfigUtils.toPlain(creeper.customName()));
                String[] args = name.split("'");
                player = Bukkit.getPlayer(args[0]);
            }
        } else if (killerEntity.getType() == EntityType.TNT) {
            TNTPrimed tnt = (TNTPrimed) killerEntity;
            if (tnt.getSource() instanceof Player) {
                player = (Player) tnt.getSource();
            }
        } else if (killerEntity.getType() == EntityType.TNT_MINECART) {
            ExplosiveMinecart cart = (ExplosiveMinecart) killerEntity;
            player = arena.getTracker().getTNTMinecartSource(cart);
        }
        return player;
    }

    /**
     * Check if given location is in given arena with given team
     * 
     * @param arena
     * @param location
     * @param team
     * @param bias Number of blocks on all directions of shield that are also counted
     * @return
     */
    public static boolean inShield(Arena arena, Location location, TeamName team, int bias) {
        if (team == TeamName.NONE) {
            return false;
        }
        
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        String gamemode = arena.getGamemode();
        String mapName = arena.getMapName();
        int x1 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.x1");
        int x2 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.x2");
        int y1 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.y1");
        int y2 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.y2");
        int z1 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.z1");
        int z2 = (int) ConfigUtils.getMapNumber(gamemode, mapName, team + "-shield.z2");
        if (x1 - bias <= x && x <= x2 + bias && 
            y1 - bias <= y && y <= y2 + bias &&
            z1 - bias <= z && z <= z2 + bias) {
            return true;
        }
        return false;
    }

    /**
     * inShield with no biases
     * 
     * @param arena
     * @param location
     * @param team
     * @return
     */
    public static boolean inShield(Arena arena, Location location, TeamName team) {
        return inShield(arena, location, team, 0);
    }
    
    /**
     * Shorthand for getting arena from player
     * 
     * @param player
     * @return null or arena
     */
    public static Arena getArena(Player player) {
        return MissileWarsPlugin.getPlugin().getArenaManager().getArena(player.getUniqueId());
    }

    /**
     * Perform an action asynchronously every tick, typically adding a trail,
     * until the given entity is dead (if its an arrow, if it hits a block)
     * 
     * @param entity
     * @param consumer the action to run every tick, with parameter as the ticks this has run
     */
    public static BukkitTask doUntilDead(Entity entity, Consumer<Integer> consumer) {
        return doUntilDead(entity, consumer, true);
    }
    
    /**
     * Perform an action every tick until the given entity is dead 
     * (if its an arrow, if it hits a block)
     * 
     * @param entity
     * @param consumer the action to run every tick, with parameter as the ticks this has run
     */
    public static BukkitTask doUntilDead(Entity entity, Consumer<Integer> consumer, boolean async) {
        BukkitRunnable runnable = getDoUntilDeadRunnable(entity, consumer);
        if (async) {
            return runnable.runTaskTimerAsynchronously(MissileWarsPlugin.getPlugin(), 1, 1);
        } else {
            return runnable.runTaskTimer(MissileWarsPlugin.getPlugin(), 1, 1);
        }
    }
    
    private static BukkitRunnable getDoUntilDeadRunnable(Entity entity, Consumer<Integer> consumer) {
        boolean isArrow = entity instanceof AbstractArrow;
        return new BukkitRunnable() {
            
            int timeAlive = 0;
            
            @Override
            public void run() {
                if (isArrow && ((AbstractArrow) entity).isInBlock()) {
                    this.cancel();
                    return;
                }
                
                if (entity.isDead()) {
                    this.cancel();
                    return;
                }
                
                consumer.accept(timeAlive);
                timeAlive++;
            }
        };
    }
    
    /**
     * Get the rank requirement to play a certain map
     * 
     * @param gamemode
     * @param map
     * @return
     */
    public static int getRankRequirement(String gamemode, String map) {
        return (int) ConfigUtils.getMapNumber(gamemode, map, "required-rank");
    }
    
    /**
     * Spawn a spiral trail around a projectile. We use circle
     * equation with 2 unit basis vectors to get the circle in R3.
     * The x basis vector is obtained by the cross product of the
     * projectile vector with [0, 1, 0]. The y basis vector is then
     * calculated by the cross product of the x basis vector with
     * the projectile vector.
     * 
     * @param projectile
     * @param particle
     * @param dustOptions can be null
     * @return the bukkittask that controls this trail
     */
    public static BukkitTask spiralTrail(Projectile projectile, Particle particle, Function<Projectile, DustOptions> dustOptions) {
        final double r;
        switch (projectile.getType()) {
            case ARROW:
            case SPECTRAL_ARROW:
                r = 0.75;
                break;
            case SPLASH_POTION:
            case LINGERING_POTION:
                r = 0.25;
                break;
            default:
                r = 0.5;
        }
        
        final int period = 8;
        final int amountPerTick = 3;
        final double tau = 2 * Math.PI;
        final double offset = Math.random() * tau;
        return doUntilDead(projectile, t -> {
            // Calculate particle position
            Vector dir = projectile.getVelocity();
            Vector vx = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
            Vector vy = vx.clone().crossProduct(dir).normalize();
            Vector center = projectile.getLocation().toVector();
            
            // Spawn particles
            int curPhase = (t % period) * amountPerTick;
            int adjustedPeriod = period * amountPerTick;
            DustOptions dust = dustOptions == null ? null : dustOptions.apply(projectile);
            for (int i = 0; i < amountPerTick; i++) {
                double angle = offset + tau - tau * (curPhase + i)  / adjustedPeriod;
                Vector curCenter = center.clone().add(dir.clone().multiply(i / (double) amountPerTick));
                Vector pos = curCenter
                        .add(vx.clone().multiply(r * Math.cos(angle)))
                        .add(vy.clone().multiply(r * Math.sin(angle)));
                projectile.getWorld().spawnParticle(particle, pos.getX(), pos.getY(), pos.getZ(), 1, 0, 0, 0, 0, dust, true);
            }
        });
    }
    
    /**
     * Use spherical coordinates to create a hollow ball using particles
     * 
     * @param center
     * @param particle
     * @param radius
     * @param spacing the space between each particle
     */
    public static void createParticleSphere(Location center, Particle particle, double radius, double spacing) {
        final double r = radius;
        final int steps_theta = (int) Math.ceil(r * Math.PI / spacing);
        final int steps_phi = steps_theta * 2;
        for (int i = 0; i < steps_theta; i++) {
            double theta = i * Math.PI / steps_theta;
            for (int j = 0; j < steps_phi; j++) {
                double phi = j * 2 * Math.PI / steps_phi;
                double x = r * Math.sin(theta) * Math.cos(phi);
                double z = r * Math.sin(theta) * Math.sin(phi); // minecraft flips y and z
                double y = r * Math.cos(theta);
                center.getWorld().spawnParticle(
                        particle, 
                        center.getX() + x,
                        center.getY() + y,
                        center.getZ() + z, 
                        1, 0, 0, 0, 0, null, true);
            }
        }
    }
    
    /**
     * Get a point on the rhumb line for a sphere
     * 
     * @param radius
     * @param angle the angle of the rhumb line to the horizontal
     * @param t negative is lower, positive is higher
     * @return a double[3] of coords xyz which can be added to a center 
     * coord to get the location of the point in a world
     */
    public static double[] rhumbLine(double radius, double angle, double t) {
        final double k = Math.tan(angle);
        double x = radius * Math.cos(t) / Math.cosh(k * t);
        double z = radius * Math.sin(t) / Math.cosh(k * t);
        double y = radius * Math.tanh(k * t);
        return new double[] {x, y, z};
    }
    
    /**
     * Check if a location is within the bounds of a sphere
     * 
     * @param toCheck
     * @param center
     * @param radius
     * @return
     */
    public static boolean isInSphere(Location toCheck, Location center, double radius) {
        double dx = toCheck.getX() - center.getX();
        double dy = toCheck.getY() - center.getY();
        double dz = toCheck.getZ() - center.getZ();
        return dx * dx + dy * dy + dz * dz < radius * radius;
    }
    
    /**
     * Play a sound that is emitted by an entity (follows its location)
     * 
     * @param sound
     * @param volume
     * @param pitch
     * @param entity
     */
    public static void playSoundFollowingEntity(Sound sound, Entity entity, float volume, float pitch) {
        net.kyori.adventure.sound.Sound playSound = net.kyori.adventure.sound.Sound.sound(
                RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT).getKey(sound), 
                net.kyori.adventure.sound.Sound.Source.MASTER, 
                volume, pitch);
        entity.getWorld().playSound(playSound, entity);
    }
    
    public static double getMaxHealth(Player player) {
        return player.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
    }
    
    public static Color getTeamParticleColor(boolean isRed) {
        Color color = isRed ? Color.fromRGB(0xff4040) : Color.fromRGB(0x4040ff);
        return color.setAlpha(128);
    }

    /**
     * Set player boots based on the deck that he has selected.
     * Does not check whether player is in arena or not
     * 
     * @param player
     */
    public static void updatePlayerBoots(Player player) {
        updatePlayerBoots(player, false);
    }
    
    
    /**
     * Set player boots based on the deck that he has selected
     * 
     * @param player
     * @param checkForArena whether to verify if player is in arena
     */
    public static void updatePlayerBoots(Player player, boolean checkForArena) {
        UUID uuid = player.getUniqueId();
        if (checkForArena) {
            Arena arena = getArena(player);
            if (arena == null) {
                return;
            }

            if (arena.getTeam(uuid) != TeamName.NONE) {
                return;
            }
        }
        
        JSONObject json = MissileWarsPlugin.getPlugin().getJSON().getPlayer(uuid);
        if (json == null) {
            return;
        }
        
        DeckStorage deck = DeckStorage.fromString(json.getString("Deck"));
        player.getInventory().setBoots(deck.getBoots());
    }
}
