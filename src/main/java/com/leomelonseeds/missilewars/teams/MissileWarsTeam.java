package com.leomelonseeds.missilewars.teams;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.decks.DeckManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

/** Represents a team of Missile Wars Players. */
/**
 * @author leona
 *
 */
public class MissileWarsTeam {

    /** The name of the team */
    private String name;
    /** The arena this team is linked to. */
    private Arena arena;
    /** The members of the team. */
    private Set<MissileWarsPlayer> members;
    /** The spawn location for the team. */
    private Location spawn;
    /** The current task for Deck pool item distribution. */
    private BukkitTask poolItemRunnable;
    /** Whether the team's decks should be distributing items in chaos-mode. */
    private boolean chaosMode;
    /** Map containing locations and statuses of all portals */
    private Map<Location, Boolean> portals;
    /** The number of shield blocks broken. */
    private int shieldBlocksBroken;

    /**
     * Create a {@link MissileWarsTeam} with a given name
     *
     * @param name the name of the team
     * @param spawn the spawn for the team
     * @param arena the arena the team is linked to
     */
    public MissileWarsTeam(String name, Arena arena, Location spawn) {
        this.name = name;
        this.members = new HashSet<>();
        this.portals = new HashMap<>();
        this.spawn = spawn;
        this.arena = arena;
    }
    
    public Map<Location, Boolean> getPortals() {
        return portals;
    }

    /**
     * Get the size of the team.
     *
     * @return the number of players on the team.
     */
    public int getSize() {
        return members.size();
    }

    /**
     * Set the status of chaos mode.
     *
     * @param chaosMode the new status of chaos mode
     */
    public void setChaosMode(boolean chaosMode) {
        this.chaosMode = chaosMode;
    }

    /**
     * Get the team's spawn location.
     *
     * @return the team's spawn location
     */
    public Location getSpawn() {
        return spawn;
    }

    /**
     * Get the number of shield blocks broken.
     *
     * @return the number of shield blocks broken
     */
    public int getShieldBlocksBroken() {
        return shieldBlocksBroken;
    }
    
    /**
     * Get remaining portals
     * 
     * @return
     */
    public int getRemainingPortals() {
        int count = 0;
        for (Location loc : portals.keySet()) {
            if (portals.get(loc)) {
                count++;
            }
        }
        return count;
    }
    
    public int getTotalPortals() {
        return portals.size();
    }

    /**
     * Check if a team contains a specific player based on their MC UUID.
     *
     * @param uuid the uuid of player to check for
     * @return true if the player is on this team
     */
    public boolean containsPlayer(UUID uuid) {
        for (MissileWarsPlayer player : members) {
            if (player.getMCPlayerId().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    public void giveItems(MissileWarsPlayer player) {
        // TP to team spawn and give armor
        Player mcPlayer = player.getMCPlayer();
        InventoryUtils.clearInventory(mcPlayer);
        mcPlayer.getInventory().setChestplate(createColoredArmor(Material.LEATHER_CHESTPLATE));
        mcPlayer.getInventory().setLeggings(createColoredArmor(Material.LEATHER_LEGGINGS));
    }
    
    /**
     * Add a player to the team.
     *
     * @param player the player to add
     */
    public void addPlayer(MissileWarsPlayer player) {
        addPlayer(player, false);
    }

    /**
     * Add a player to the team.
     *
     * @param player the player to add
     */
    public void addPlayer(MissileWarsPlayer player, Boolean ranked) {
        
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();

        members.add(player);
        DeckManager dm = plugin.getDeckManager();
        player.setDeck(dm.getPlayerDeck(player.getMCPlayerId(), ranked));

        // TP to team spawn and give armor
        Player mcPlayer = player.getMCPlayer();
        mcPlayer.teleport(spawn);
        mcPlayer.setHealth(20);
        mcPlayer.setGameMode(GameMode.SURVIVAL);
        mcPlayer.setFireTicks(0);
        ConfigUtils.sendConfigMessage("messages.classic-start", mcPlayer, null, null);
        giveItems(player);
        player.setJoinTime(LocalDateTime.now());
        
        // Vanguard bunny
        int bunny = plugin.getJSON().getAbility(player.getMCPlayerId(), "bunny");
        if (bunny > 0) {
            int level = (int) ConfigUtils.getAbilityStat("Vanguard.passive.bunny", bunny, "amplifier");
            mcPlayer.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 30 * 60 * 20, level));
        }
        
        // Sentinel priest
        int priest = plugin.getJSON().getAbility(player.getMCPlayerId(), "priest");
        if (priest > 0) {
            Arena arena = plugin.getArenaManager().getArena(player.getMCPlayerId());
            int radius = (int) ConfigUtils.getAbilityStat("Sentinel.passive.priest", priest, "radius");
            new BukkitRunnable() {
                @Override
                public void run() {
                    String team = arena.getTeam(player.getMCPlayerId());
                    if (!team.equals("no team")) {
                        // For entities in radius, if it is player and in same team,
                        // give that entity regeneration 1 for 1 second
                        for (Entity e : mcPlayer.getNearbyEntities(radius, radius, radius)) {
                            if (!(e instanceof Player)) {
                                continue;
                            }
                            Player p = (Player) e;
                            if (team.equals(arena.getTeam(p.getUniqueId()))) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20, 0));
                            }
                        }
                    } else {
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 10, 10);
        }
    }

    /**
     * Broadcast a config message to all members of the team.
     *
     * @param path the path to the message in the message.yml file
     * @param focus the key player that is the focus of the message
     */
    public void broadcastConfigMsg(String path, MissileWarsPlayer focus) {
        if (focus != null) {
            for (MissileWarsPlayer player : members) {
                ConfigUtils.sendConfigMessage(path, player.getMCPlayer(), arena, focus.getMCPlayer());
            }
        } else {
            for (MissileWarsPlayer player : members) {
                ConfigUtils.sendConfigMessage(path, player.getMCPlayer(), arena, null);
            }
        }
    }

    /**
     * Create a piece of team-colored leather armor.
     *
     * @param type the item type
     * @return an item of type value with this team's color
     */
    private ItemStack createColoredArmor(Material type) {
        ItemStack item = new ItemStack(type);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(DyeColor.valueOf(ChatColor.stripColor(name).toUpperCase()).getColor());
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Give each {@link MissileWarsPlayer} their gear. */
    public void distributeGear() {
        for (MissileWarsPlayer player : members) {
            player.giveDeckGear();
            player.givePoolItem(true);
        }
    }

    /** Schedule the distribution of in-game Deck items. */
    public void scheduleDeckItems() {
        FileConfiguration settings = MissileWarsPlugin.getPlugin().getConfig();
        double timeBetween = settings.getInt("item-frequency." + Math.max(1, Math.min(members.size(), 3)));
        if (chaosMode) {
            timeBetween /= settings.getInt("chaos-mode.multiplier");
        }

        int secsBetween = (int) Math.floor(timeBetween);

        // Setup level countdown till distribution
        for (int secInCd = secsBetween; secInCd > 0; secInCd--) {
            int finalSecInCd = secInCd;
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (MissileWarsPlayer player : members) {
                        player.getMCPlayer().setLevel(finalSecInCd);
                    }
                }
            }.runTaskLater(MissileWarsPlugin.getPlugin(), (secsBetween - secInCd) * 20);
        }

        poolItemRunnable = new BukkitRunnable() {
            @Override
            public void run() {
                // Distribute items
                for (MissileWarsPlayer player : members) {
                    player.givePoolItem(false);
                }
                // Enqueue next distribution
                scheduleDeckItems();
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(),  secsBetween * 20L);
    }

    /** Stop the distribution of in-game Deck items. */
    public void stopDeckItems() {
        if (poolItemRunnable != null) {
            poolItemRunnable.cancel();
        }
    }

    /**
     * Remove a given player from the team.
     *
     * @param player the player to remove
     */
    public void removePlayer(MissileWarsPlayer player) {
        if (members.contains(player)) {
        	Player mcPlayer = player.getMCPlayer();
            members.remove(player);
            InventoryUtils.clearInventory(mcPlayer);
            player.resetPlayer();
            for (PotionEffect effect : mcPlayer.getActivePotionEffects()){
                mcPlayer.removePotionEffect(effect.getType());
            }
        }
    }

    /**
     * Register a portal break at a given location.
     *
     * @param loc the location
     * @return true if a portal's broken status was changed
     */
    public boolean registerPortalBreak(Location loc) {
        // Trace portal block to the most positive x and y positions
        while (loc.clone().add(1, 0, 0).getBlock().getType() != Material.OBSIDIAN) {
            loc.add(1, 0, 0);
        }
        while (loc.clone().add(0, 1, 0).getBlock().getType() != Material.OBSIDIAN) {
            loc.add(0, 1, 0);
        }
        if (portals.get(loc)) {
            portals.put(loc, false);
            return true;
        }
        return false;
    }

    /**
     * Obtain whether the team has a living portal.
     *
     * @return true if either the first or second portal for this team exists
     */
    public boolean hasLivingPortal() {
        for (Location loc : portals.keySet()) {
            if (portals.get(loc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Send the team a title at a given path.
     *
     * @param path the path
     */
    public void sendTitle(String path) {
        // Send titles to players
        for (MissileWarsPlayer member : members) {
        	Player player = member.getMCPlayer();
            ConfigUtils.sendTitle(path, player);
        }
    }

    /**
     * Send the team a sound at a given path.
     *
     * @param path the path
     */
    public void sendSound(String path) {
    	for (MissileWarsPlayer member : members) {
    		Player player = member.getMCPlayer();
    		ConfigUtils.sendConfigSound(path, player);
    	}
    }

    /**
     * Attempt to register the placing or breaking of a shield block for this team.
     *
     * @param location the location of the edited block
     * @param place whether the block was placed
     * @return true if the updated block was a shield block for this team
     */
    public boolean registerShieldBlockUpdate(Location location, boolean place) {
        // Check if block was in shield location
        if (ConfigUtils.inShield(arena, location, name)) {
            shieldBlocksBroken += place ? -1 : 1;
            return true;
        }
        return false;
    }

    /**
     * Gets the volume of the shield for this team in the current given map.
     *
     * @return the shield volume for this team in the current map
     */
    public int getShieldVolume() {
        // Ignore if arena not running
        if (arena == null || !(arena.isRunning() || arena.isResetting())) {
            return 1;
        }

        // Calculate volume
        String teamName = ChatColor.stripColor(name).toLowerCase();
        int x1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.x1");
        int x2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.x2");
        int y1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.y1");
        int y2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.y2");
        int z1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.z1");
        int z2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), teamName + "-shield.z2");
        return (x2 - x1) * (y2 - y1) * (z2 - z1);
    }

    /**
     * Gets the shield health of this team based on shield volume and broken blocks
     *
     * @return the shield health as a percentage
     */
    public double getShieldHealth() {
        int totalBlocks = getShieldVolume();
        return 100 * ((totalBlocks - shieldBlocksBroken) / (double) totalBlocks);
    }

    public Boolean isChaos() {
        return chaosMode;
    }
}
