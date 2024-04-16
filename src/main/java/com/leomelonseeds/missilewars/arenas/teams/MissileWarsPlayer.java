package com.leomelonseeds.missilewars.arenas.teams;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.decks.DeckItem;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.SchematicManager;

/** Represents a Missile Wars Player. */
public class MissileWarsPlayer {
    
    public enum Stat {
        PORTALS,
        KILLS,
        DEATHS,
        UTILITY,
        MISSILES
    }

    private UUID playerId;
    private Deck deck;
    private Map<Stat, Integer> stats;
    private List<BukkitTask> tasks;
    /** The time that the player joined the game */
    private LocalDateTime joinTime;
    /** Player should be invulnerable and not be able to spawn missiles if this is true */
    private boolean justSpawned;
    /** If the player is out of bounds */
    private boolean outOfBounds;


    /**
     * Create a MissileWarsPlayer from a Minecraft player.
     *
     * @player the Minecraft player
     */
    public MissileWarsPlayer(UUID playerID) {
        this.playerId = playerID;
        this.stats = new HashMap<>();
        this.tasks = new ArrayList<>();
        resetPlayer();
    }
    
    /**
     * Make player invulnerable and unable to interact
     * for a short time
     */
    public void setJustSpawned() {
        justSpawned = true;
        ConfigUtils.schedule(MissileWarsPlugin.getPlugin().getConfig().getInt("respawn-disable"), () -> {
            justSpawned = false;
        });
    }
    
    /**
     * @return should the player be invulnerable
     */
    public boolean justSpawned() {
        return justSpawned;
    }
    
    /**
     * @return is the player currently out of bounds
     */
    public boolean outOfBounds() {
        return outOfBounds;
    }
        
    /**
     * Missile preview feature
     */
    public void missilePreview(boolean isRed) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        tasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Player player = getMCPlayer();
            if (player == null) {
                return;
            }
            
            if (player.getLocation().getBlockY() < -64) {
                return;
            }

            // Make sure player is aiming for a block
            Block target = player.getTargetBlock(null, 4);
            if (target == null || target.getType() == Material.AIR) {
                return;
            }

            // Player must be holding item
            PlayerInventory inv = player.getInventory();
            ItemStack mainhand = inv.getItem(EquipmentSlot.HAND);
            ItemStack offhand = inv.getItem(EquipmentSlot.OFF_HAND);
            ItemStack hand = mainhand.getType() == Material.AIR ? offhand.getType() == Material.AIR ? null : offhand : mainhand;
            if (hand == null || player.hasCooldown(hand.getType())) {
                return;
            }

            // Item must be a missile
            String structureName = InventoryUtils.getStringFromItem(hand, "item-structure");// Switch to throwing logic if using a throwable
            if (structureName == null || structureName.contains("shield-") || structureName.contains("platform-") || 
                    structureName.contains("torpedo-") || structureName.contains("canopy")) {
                return;
            }

            // At this point, we know the player is holding a missile item facing a block
            Location loc = target.getLocation();
            Location[] spawns = SchematicManager.getCorners(structureName, loc, isRed, player.hasPermission("umw.oldoffsets"));
            double x1 = Math.min(spawns[0].getX(), spawns[1].getX()) + 0.5;
            double x2 = Math.max(spawns[0].getX(), spawns[1].getX()) - 0.5;
            double y1 = Math.min(spawns[0].getY(), spawns[1].getY()) + 0.5;
            double y2 = Math.max(spawns[0].getY(), spawns[1].getY()) - 0.5;
            double z1 = Math.min(spawns[0].getZ(), spawns[1].getZ()) + 0.5;
            double z2 = Math.max(spawns[0].getZ(), spawns[1].getZ()) - 0.5;
            DustOptions dustOptions = new DustOptions(Color.LIME, 1.0F);
            for (double x = x1; x <= x2; x++) {
                for (double y = y1; y <= y2; y++) {
                    for (double z = z1; z <= z2; z++) {
                        boolean isX = x == x1 || x == x2;
                        boolean isY = y == y1 || y == y2;
                        boolean isZ = z == z1 || z == z2;
                        if (isX ? (isY || isZ) : (isY && isZ)) {
                            player.spawnParticle(Particle.REDSTONE, x, y, z, 1, dustOptions);
                        }
                    }
                }
            }
        }, 20, 10));
    }

    // EXP bar cooldown preview + out of bounds handling
    // TODO: Enemy in-base red vignette
    private void cooldownPreview(Arena arena) {
        Player player = getMCPlayer();
        tasks.add(new BukkitRunnable() {
            
            ItemStack lastItem = null;
            boolean lastAvailable = false;
            
            @Override
            public void run() {
                if (ArenaUtils.outOfBounds(player, arena)) {
                    player.sendActionBar(ConfigUtils.toComponent(ConfigUtils.getConfigText("messages.out-of-bounds", player, null, null)));
                    outOfBounds = true;
                    lastAvailable = false;
                    return;
                } else {
                    outOfBounds = false;
                }
                
                PlayerInventory inv = player.getInventory();
                ItemStack item = inv.getItemInMainHand();
                DeckItem di = deck.getDeckItem(item);
                if (di == null) {
                    if (item.getType().toString().contains("BOW")) {
                        for (DeckItem temp : deck.getItems()) {
                            if (temp.getInstanceItem().getType().toString().contains("ARROW")) {
                                di = temp;
                                break;
                            }
                        }
                        
                        if (di == null) {
                            return;
                        }
                    } else {
                        player.setLevel(0);
                        player.setExp(1F);
                        player.sendActionBar(ConfigUtils.toComponent(""));
                        lastItem = null;
                        lastAvailable = false;
                        return; 
                    }
                }
                
                int cd = di.getCurrentCooldown();
                int maxcd = di.getCooldown();
                float exp = maxcd == 0 ? 1F : (maxcd - cd) / (float) maxcd;
                player.setLevel(cd);
                player.setExp(Math.max(Math.min(exp, 1F), 0F)); // Make sure it's actually within 0 and 1

                // Actionbar stuff
                String action;
                if (player.hasCooldown(item.getType())) {
                    action = ConfigUtils.getConfigText("messages.item-cooldown", player, null, null);
                    action = action.replace("%cd%", cd + "");
                    lastAvailable = false;
                } else if (di.matches(lastItem) && lastAvailable) {
                    return;
                } else {
                    action = ConfigUtils.getConfigText("messages.item-ready", player, null, null);
                    lastAvailable = true;
                }
                player.sendActionBar(ConfigUtils.toComponent(action));
                lastItem = di.getInstanceItem();
            }
        }.runTaskTimerAsynchronously(MissileWarsPlugin.getPlugin(), 2, 2));
    }

    /**
     * Set the user's current {@link Deck} and give gear items
     * The actual deck items are given later when initDeck is called by the Arena
     *
     * @param joinedBefore whether this player has previously been in the arena
     */
    public void setDeck(Deck deck) {
        this.deck = deck;
        Player player = getMCPlayer();
        if (player == null) {
            return;
        }
        
        for (ItemStack gearItem : deck.getGear()) {
            if (gearItem.getType().toString().contains("BOOTS")) {
                player.getInventory().setBoots(gearItem);
            } else {
                player.getInventory().setItem(0, gearItem);
            }
        }
    }
    
    /**
     * Initialize deck cooldowns and exp cooldown
     * 
     * @param joinedBefore
     */
    public void initDeck(boolean joinedBefore, Arena arena) {
        Player player = getMCPlayer(); // Not null due to check in arena

        // Game start randomizer
        List<Integer> cooldowns = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            cooldowns.add(i);
        }
        Collections.shuffle(cooldowns);
        
        for (int i = 0; i < 8; i++) {
            DeckItem di = deck.getItems().get(i);
            String name = di.getInstanceItem().getType().toString();
            player.getInventory().setItem(i + 1, di.getInstanceItem());
            
            // Add cooldown for crossbow, only at the start of the game
            // since cooldown only applied after shooting crossbow otherwise
            int maxcd = di.getCooldown();
            if (name.contains("ARROW")) {
                player.setCooldown(Material.CROSSBOW, maxcd * 20);
            }
            
            if (name.contains("SPAWN_EGG") && !joinedBefore) {
                di.initCooldown((int) (maxcd * ((double) cooldowns.remove(0) / 4) + 1));
            } else {
                di.initCooldown(maxcd);
            }
        }
        
        if (joinedBefore) {
            ConfigUtils.sendConfigMessage("messages.already-joined", player, null, null);
        }
        
        cooldownPreview(arena);
    }
 
    /**
     * Get the user's currently selected {@link Deck}.
     *
     * @return the user's currently selected {@link Deck}
     */
    public Deck getDeck() {
        return deck;
    }

    /**
     * Obtain the UUID of the Minecraft player associated with the MissileWarsPlayer.
     *
     * @return the UUID of the Minecraft player associated with the MissileWarsPlayer
     */
    public UUID getMCPlayerId() {
        return playerId;
    }

    /**
     * Return the MC player this MissileWarsPlayer represents.
     *
     * @return the MC player this MissileWarsPlayer represents
     */
    public Player getMCPlayer() {
        return Bukkit.getPlayer(playerId);
    }
    
    /**
     * Fetch a statistic
     * 
     * @param stat
     * @return
     */
    public int getStat(Stat stat) {
        return stats.get(stat);
    }
    
    /**
     * Increment a statistic
     * 
     * @param stat
     */
    public void incrementStat(Stat stat) {
        stats.put(stat, stats.get(stat) + 1);
    }

    /** Reset the stats of this {@link MissileWarsPlayer} back to 0 */
    public void resetPlayer() {
        for (Stat stat : Stat.values()) {
            stats.put(stat, 0);
        }
        deck = null;
        justSpawned = false;
        outOfBounds = false;
    }
    
    /**
     * Stops all deck and tasks associated with this player. Run this when
     * the player should not be able play anymore
     */
    public void stopDeck() {
        tasks.forEach(t -> t.cancel());
        if (getMCPlayer() != null) {
            getMCPlayer().sendActionBar(ConfigUtils.toComponent(""));
        }
        tasks.clear();
        
        if (deck == null) {
            return;
        }
        
        for (DeckItem di : deck.getItems()) {
            di.stop();
        }
    }

    /** Set the join time of this {@link MissileWarsPlayer} */
    public void setJoinTime(LocalDateTime time) {
        joinTime = time;
    }

    /** Resets missile preview and deck actionbar for {@link MissileWarsPlayer} */
    public void resetTasks() {
        tasks.forEach(t -> t.cancel());
        Player player = getMCPlayer();
        if (player != null) {
            player.sendActionBar(ConfigUtils.toComponent(""));
        }
        tasks.clear();
    }

    /**
     * Get the join time of this {@link MissileWarsPlayer}.
     *
     * @return the join time of this {@link MissileWarsPlayer}
     */
    public LocalDateTime getJoinTime() {
        return joinTime;
    }

    /**
     * Checks to see if this MissileWarsPlayer is equal to another Object.
     *
     * @param o the object
     * @return true if o is a MissileWarsPlayer with the same playerId
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MissileWarsPlayer player = (MissileWarsPlayer) o;
        return Objects.equals(playerId, player.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }

}
