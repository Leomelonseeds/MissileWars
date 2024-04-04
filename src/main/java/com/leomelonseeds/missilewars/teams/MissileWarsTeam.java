package com.leomelonseeds.missilewars.teams;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.TrainingArena;
import com.leomelonseeds.missilewars.arenas.TutorialArena;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.decks.DeckItem;
import com.leomelonseeds.missilewars.listener.handler.CanopyManager;
import com.leomelonseeds.missilewars.listener.handler.EnderSplashManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.format.NamedTextColor;

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
    /** Whether the team's decks should be distributing items in chaos-mode. */
    private boolean chaosMode;
    /** Map containing locations and statuses of all portals */
    private Map<Location, Boolean> portals;
    /** The number of shield blocks broken. */
    private int shieldBlocksBroken;
    /** The number of shield blocks in total */
    private int shieldVolume;
    /** Register this team to a vanilla team */
    private Team team;
    /** the multiplier for all cooldown items */
    private double multiplier;

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
        this.shieldBlocksBroken = 0;
        this.multiplier = 1;
        
        // Register team
        String teamName = arena.getName() + "." + name;
        team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
        if (team == null) {
            team = Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam(teamName);
            team.displayName(name.equals("red") ? ConfigUtils.toComponent("&cRED") : ConfigUtils.toComponent("&9BLUE"));
            team.color(name.equals("red") ? NamedTextColor.RED : NamedTextColor.BLUE);
        }
        
        // Temp value while async calculations run
        shieldVolume = 23850;
        
        // Calculate total shield blocks async, do later to make sure maps is actually there
        Bukkit.getScheduler().runTaskLaterAsynchronously(MissileWarsPlugin.getPlugin(), () -> {
            // Ignore if arena not running
            if (arena == null || !(arena.isRunning() || arena.isResetting())) {
                shieldVolume = 1;
            }

            // Get locations
            int x1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.x1");
            int x2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.x2");
            int y1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.y1");
            int y2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.y2");
            int z1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.z1");
            int z2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.z2");
            
            // Calculate volume by adding up all non-air locations
            int tempShieldVolume = 0;
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) {
                        Location l = new Location(arena.getWorld(), x, y, z);
                        Block b = l.getBlock();
                        if (b.getType() != Material.AIR) {
                            tempShieldVolume++;
                        }
                    }
                }
            }
            shieldVolume = tempShieldVolume;
        }, 20L);
    }
    
    public void unregisterTeam() {
        if (Bukkit.getScoreboardManager().getMainScoreboard().getTeams().contains(team)) {
            team.unregister();
        }
    }
    
    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }
    
    public double getMultiplier() {
        return multiplier;
    }
    
    public String getName() {
        return name;
    }
    
    public Map<Location, Boolean> getPortals() {
        return portals;
    }
    
    public Set<MissileWarsPlayer> getMembers() {
        return members;
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

    /**
     * Add a player to the team.
     *
     * @param player the player to add
     */
    public void addPlayer(MissileWarsPlayer player) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        members.add(player);

        // TP to team spawn and give armor
        Player mcPlayer = player.getMCPlayer();
        team.addPlayer(mcPlayer);
        mcPlayer.teleport(spawn);
        mcPlayer.setHealth(20);
        mcPlayer.setGameMode(GameMode.SURVIVAL);
        mcPlayer.setFireTicks(0);
        player.setJoinTime(LocalDateTime.now());
        player.setJustSpawned();
        if (arena instanceof TrainingArena) {
            ConfigUtils.sendConfigMessage("messages.training-start", mcPlayer, null, null);
        } else if (!(arena instanceof TutorialArena)) {
            ConfigUtils.sendConfigMessage("messages." + arena.getGamemode() + "-start", mcPlayer, null, null);
        }
        
        // Drop any alcohol items and clear inventory, then add armor
        PlayerInventory inv = mcPlayer.getInventory();
        boolean dropped = false;
        for (int i = 0; i <= 8; i++) {
            ItemStack ci = inv.getContents()[i];
            if (InventoryUtils.isPotion(ci)) {
                arena.getWorld().dropItem(spawn, ci);
                dropped = true;
            }
        }
        if (dropped) {
            ConfigUtils.sendConfigMessage("messages.dropped-alcohol", mcPlayer, null, null);
        }
        InventoryUtils.clearInventory(mcPlayer, true);
        inv.setChestplate(createColoredArmor(Material.LEATHER_CHESTPLATE));
        inv.setLeggings(createColoredArmor(Material.LEATHER_LEGGINGS));
        
        // Create and register deck. Initdeck is in arena code!
        Deck deck = plugin.getDeckManager().getPlayerDeck(player);
        player.setDeck(deck);
        player.missilePreview(arena);
        for (DeckItem di : deck.getItems()) {
            mcPlayer.setCooldown(di.getInstanceItem().getType(), 36000);
            di.registerTeam(this);
        }
        arena.addCallback(player);
     
        // Architect Haste
        JSONObject json = plugin.getJSON().getPlayerPreset(mcPlayer.getUniqueId());
        if (json.has("haste")) {
            ItemStack item = mcPlayer.getInventory().getItemInMainHand();
            if (item == null || item.getType() != Material.IRON_PICKAXE) {
                return;
            }
            
            int level = json.getInt("haste");
            if (level <= 0) {
                return;
            }
            
            mcPlayer.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 30 * 60 * 20, level * 2 - 1));
        }
        
        // Passive activation
        String passive = json.getJSONObject("passive").getString("selected");
        int level = json.getJSONObject("passive").getInt("level");
        switch (passive) {
        case "bunny":
            int bamp = (int) ConfigUtils.getAbilityStat("Vanguard.passive.bunny", level, "amplifier");
            mcPlayer.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 30 * 60 * 20, bamp));
            break;
        case "adrenaline":
            int aamp = (int) ConfigUtils.getAbilityStat("Vanguard.passive.adrenaline", level, "amplifier");
            mcPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30 * 60 * 20, aamp));
            break;
        case "warden":
            Arena arena = plugin.getArenaManager().getArena(player.getMCPlayerId());
            int amplifier = (int) ConfigUtils.getAbilityStat("Sentinel.passive.warden", level, "amplifier");
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Cancel if no team
                    if (!containsPlayer(player.getMCPlayerId())) {
                        this.cancel();
                        return;
                    }
                    // Don't allow if not in base
                    if (!ConfigUtils.inShield(arena, mcPlayer.getLocation(), name, 5)) {
                        return;
                    }
                    mcPlayer.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20, amplifier));
                    mcPlayer.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20, amplifier));
                }
            }.runTaskTimer(plugin, 10, 10);
            break;
        default:
            return;
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
        meta.setColor(DyeColor.valueOf(name.toUpperCase()).getColor());
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Remove a given player from the team.
     *
     * @param player the player to remove
     */
    public void removePlayer(MissileWarsPlayer player) {
        if (!members.contains(player)) {
            return;
        }
        
    	Player mcPlayer = player.getMCPlayer();
    	team.removePlayer(mcPlayer);
        members.remove(player);
        InventoryUtils.clearInventory(mcPlayer);
        player.stopDeck();
        player.resetPlayer();
        mcPlayer.setLevel(0);
        mcPlayer.setExp(0F);
        mcPlayer.setGlowing(false);
        CanopyManager.getInstance().removePlayer(mcPlayer);
        EnderSplashManager.getInstance().removePlayer(mcPlayer);
        for (PotionEffect effect : mcPlayer.getActivePotionEffects()){
            mcPlayer.removePotionEffect(effect.getType());
        }
        
        if (arena.isRunning()) {
            arena.addLeft(player.getMCPlayerId());
            arena.applyMultipliers(); // Check for cooldowns
        }
    }

    /**
     * Register a portal break at a given location.
     *
     * @param loc the location
     * @return true if a portal's broken status was changed
     */
    public boolean registerPortalBreak(Location loc) {
        return registerPortalBreak(loc, true);
    }
    
    /**
     * @param loc
     * @param count set to false to not count the portal as broken
     * @return
     */
    public boolean registerPortalBreak(Location loc, boolean count) {
        // Trace portal block to the most positive x and y positions
        while (loc.clone().add(1, 0, 0).getBlock().getType() != Material.OBSIDIAN) {
            loc.add(1, 0, 0);
        }
        
        while (loc.clone().add(0, 1, 0).getBlock().getType() != Material.OBSIDIAN) {
            loc.add(0, 1, 0);
        }
        
        // Return if no registered portal at location
        if (portals.get(loc) == null) {
            return false;
        }
        
        // If portal still exists there
        if (portals.get(loc)) {
            portals.put(loc, false);
            // Reset this to true after 5 sec if don't count
            if (!count) {
                Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> portals.put(loc, true), 100);
            }
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
        return shieldVolume;
    }

    /**
     * Gets the shield health of this team based on shield volume and broken blocks
     *
     * @return the shield health as a percentage
     */
    public double getShieldHealth() {
        int totalBlocks = shieldVolume;
        double percentage = Math.abs((totalBlocks - shieldBlocksBroken) / (double) totalBlocks);
        return Math.min(100 * percentage, 100);
    }

    public Boolean isChaos() {
        return chaosMode;
    }
}
