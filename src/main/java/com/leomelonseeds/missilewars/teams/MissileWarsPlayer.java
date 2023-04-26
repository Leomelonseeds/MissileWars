package com.leomelonseeds.missilewars.teams;

import java.time.LocalDateTime;
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

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.decks.DeckItem;
import com.leomelonseeds.missilewars.schematics.SchematicManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

/** Represents a Missile Wars Player. */
public class MissileWarsPlayer {

    /** The UUID of the Spigot player this player represents. */
    private UUID playerId;
    /** The current deck the player has selected. */
    private Deck deck;
    /** The mvp stat of whatever gamemode the player is in */
    private int mvpStat;
    /** The number of kills the player has. */
    private int kills;
    /** The number of deaths the player has. */
    private int deaths;
    /** The number of utility the player used */
    private int utility;
    /** The number of missiles the player spawned */
    private int missiles;
    /** The time that the player joined the game */
    private LocalDateTime joinTime;
    /** Player should be invulnerable and not be able to spawn missiles if this is true */
    private boolean justSpawned;


    /**
     * Create a MissileWarsPlayer from a Minecraft player.
     *
     * @player the Minecraft player
     */
    public MissileWarsPlayer(UUID playerID) {
        this.playerId = playerID;
        justSpawned = false;
    }
    
    /**
     * Make player invulnerable and unable to interact
     * for a short time
     */
    public void setJustSpawned() {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        justSpawned = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            justSpawned = false;
        }, plugin.getConfig().getInt("respawn-disable"));
    }
    
    /**
     * @return should the player be invulnerable
     */
    public boolean justSpawned() {
        return justSpawned;
    }
    
    /**
     * Missile preview feature
     */
    public void missilePreview(Arena arena) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        new BukkitRunnable() {
            @Override
            public void run() {
                // If the player left, cancel task
                String team = arena.getTeam(playerId);
                if (team == "no team") {
                    this.cancel();
                    return;
                }

                // If player disabled preview, cancel task
                Player player = getMCPlayer();
                if (player.hasPermission("umw.disablepreview")) {
                    this.cancel();
                    return;
                }

                // Make sure player is aiming for a block
                Block target = player.getTargetBlock(null, 4);
                if (target.getType() == Material.AIR) {
                    return;
                }

                // Player must be holding item
                PlayerInventory inv = player.getInventory();
                ItemStack mainhand = inv.getItem(EquipmentSlot.HAND);
                ItemStack offhand = inv.getItem(EquipmentSlot.OFF_HAND);
                ItemStack hand = mainhand.getType() == Material.AIR ? offhand.getType() == Material.AIR ? null : offhand : mainhand;
                if (hand == null) {
                    return;
                }

                // Item must be a missile
                String structureName = ConfigUtils.getStringFromItem(hand, "item-structure");// Switch to throwing logic if using a throwable
                if (structureName == null || structureName.contains("shield-") || structureName.contains("platform-") || 
                        structureName.contains("torpedo-") || structureName.contains("canopy")) {
                    return;
                }

                // At this point, we know the player is holding a missile item facing a block
                Location loc = target.getLocation();
                Location[] spawns = SchematicManager.getCorners(structureName, loc, team == "red");
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
            }
        }.runTaskTimerAsynchronously(plugin, 20, 10);
    }
    
    /**
     * Similar to missile preview, but for item specific cooldowns
     * Sets player exp bar level
     */
    public void cooldownPreview(Arena arena) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // If the player left, cancel task
                String team = arena.getTeam(playerId);
                if (team == "no team") {
                    this.cancel();
                    return;
                }
                
                Player player = getMCPlayer();
                ItemStack item = player.getInventory().getItemInMainHand();
                DeckItem di = deck.getDeckItem(item);
                if (di == null) {
                    player.setLevel(0);
                    player.setExp(0F);
                    return;
                }
                
                double cd = di.getCurrentCooldown();
                player.setLevel((int) Math.ceil(cd));
                player.setExp((float) (cd - Math.floor(cd - 0.5)));
            }
        }.runTaskTimerAsynchronously(MissileWarsPlugin.getPlugin(), 2, 2);
    }

    /**
     * Set the user's current {@link Deck}.
     * Call when player joins the game. Gives player
     * all deck items and such
     *
     * @param deck the deck to let this MissileWarsPlayer use
     */
    public void setDeck(Deck deck) {
        this.deck = deck;
        Player player = getMCPlayer();
        if (deck == null || player == null) {
            return;
        }

        for (ItemStack gearItem : deck.getGear()) {
            if (gearItem.getType().toString().contains("BOOTS")) {
                player.getInventory().setBoots(gearItem);
            } else {
                player.getInventory().addItem(gearItem);
            }
        }
        
        for (int i = 0; i < 8; i++) {
            DeckItem di = deck.getItems().get(i);
            player.getInventory().setItem(i + 1, di.getInstanceItem());
        }
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
    
    public int getMVP() {
        return mvpStat;
    }
    
    public void addToMVP(int add) {
        mvpStat += add;
    }

    /**
     * Get the number of kills this {@link MissileWarsPlayer} has.
     *
     * @return the number of kills this {@link MissileWarsPlayer} has
     */
    public int getKills() {
        return kills;
    }

    /** Increment the kill count for this {@link MissileWarsPlayer}. */
    public void incrementKills() {
        kills++;
    }

    /**
     * Get the number of deaths this {@link MissileWarsPlayer} has.
     *
     * @return the number of deaths this {@link MissileWarsPlayer} has
     */
    public int getDeaths() {
        return deaths;
    }

    /** Increment the kill count for this {@link MissileWarsPlayer}. */
    public void incrementDeaths() {
        deaths++;
    }

    /** Increment the utility count for this {@link MissileWarsPlayer}. */
    public void incrementUtility() {
        utility++;
    }

    /**
     * Get the number of utility spawns this {@link MissileWarsPlayer} has.
     *
     * @return the number of utility spawns this {@link MissileWarsPlayer} has
     */
    public int getUtility() {
        return utility;
    }

    /** Increment the missile count for this {@link MissileWarsPlayer}. */
    public void incrementMissiles() {
        missiles++;
    }

    /**
     * Get the number of missile spawns this {@link MissileWarsPlayer} has.
     *
     * @return the number of missile spawns this {@link MissileWarsPlayer} has
     */
    public int getMissiles() {
        return missiles;
    }

    /** Reset the stats of this {@link MissileWarsPlayer} back to 0 */
    public void resetPlayer() {
        stopDeck();
        missiles = 0;
        utility = 0;
        kills = 0;
        deaths = 0;
        deck = null;
    }

    /** Set the join time of this {@link MissileWarsPlayer} */
    public void setJoinTime(LocalDateTime time) {
        joinTime = time;
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
    
    public void stopDeck() {
        if (deck == null) {
            return;
        }
        
        for (DeckItem di : deck.getItems()) {
            di.stop();
        }
    }

}
