package com.leomelonseeds.missilewars.arenas.teams;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.fastasyncworldedit.core.function.mask.SingleBlockTypeMask;
import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.TrainingArena;
import com.leomelonseeds.missilewars.arenas.TutorialArena;
import com.leomelonseeds.missilewars.decks.Ability;
import com.leomelonseeds.missilewars.decks.Ability.Stat;
import com.leomelonseeds.missilewars.decks.Ability.Type;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.decks.DeckItem;
import com.leomelonseeds.missilewars.listener.handler.AstralTurretManager;
import com.leomelonseeds.missilewars.listener.handler.CanopyManager;
import com.leomelonseeds.missilewars.listener.handler.EnderSplashManager;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.mask.InverseSingleBlockTypeMask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockTypes;

/** Represents a team of Missile Wars Players. */
/**
 * @author leona
 *
 */
public class MissileWarsTeam {

    private TeamName name;
    private Arena arena;
    private Set<MissileWarsPlayer> members;
    private Location spawn;
    private Map<Location, ClassicPortal> portals;
    private int shieldBlocksBroken;
    private int shieldVolume;
    private double multiplier;

    /**
     * Create a {@link MissileWarsTeam} with a given name
     *
     * @param name the name of the team
     * @param spawn the spawn for the team
     * @param arena the arena the team is linked to
     */
    public MissileWarsTeam(TeamName name, Arena arena, Location spawn) {
        this.name = name;
        this.members = new HashSet<>();
        this.portals = new HashMap<>();
        this.spawn = spawn;
        this.arena = arena;
        this.shieldBlocksBroken = 0;
        this.multiplier = 1;
        
        // Temp value while async calculations run
        shieldVolume = 23850;
        
        // Calculate total shield blocks async, do later to make sure maps is actually there
        Bukkit.getScheduler().runTaskLaterAsynchronously(MissileWarsPlugin.getPlugin(), () -> {
            // Ignore if arena not running
            if (arena == null || !(arena.isRunning() || arena.isResetting())) {
                shieldVolume = 1;
                return;
            }

            // Get locations
            int x1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.x1");
            int x2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.x2");
            int y1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.y1");
            int y2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.y2");
            int z1 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.z1");
            int z2 = (int) ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), name + "-shield.z2");
            
            // Set WE parameters and count
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(arena.getWorld());
            BlockVector3 pos1 = BlockVector3.at(x1, y1, z1);
            BlockVector3 pos2 = BlockVector3.at(x2, y2, z2);
            Region region = new CuboidRegion(weWorld, pos1, pos2);
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                shieldVolume = editSession.countBlocks(region, new InverseSingleBlockTypeMask(weWorld, BlockTypes.AIR));
            }
        }, 20L);
    }
    
    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }
    
    public double getMultiplier() {
        return multiplier;
    }
    
    public TeamName getName() {
        return name;
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
     * Check if a team contains a specific player based on their MC UUID.
     *
     * @param uuid the uuid of player to check for
     * @return true if the player is on this team
     */
    public boolean containsPlayer(UUID uuid) {
        return members.stream().anyMatch(p -> p.getMCPlayerId().equals(uuid));
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
        for (int i = 0; i <= 8; i++) {
            ItemStack ci = inv.getContents()[i];
            if (InventoryUtils.isPotion(ci)) {
                InventoryUtils.regiveItem(mcPlayer, ci);
            }
        }
        
        InventoryUtils.clearInventory(mcPlayer, true);
        inv.setChestplate(createColoredArmor(Material.LEATHER_CHESTPLATE));
        inv.setLeggings(createColoredArmor(Material.LEATHER_LEGGINGS));
        
        // Create and register deck. Initdeck is in arena code!
        Deck deck = plugin.getDeckManager().getPlayerDeck(player);
        player.setDeck(deck);
        for (DeckItem di : deck.getItems()) {
            mcPlayer.setCooldown(di.getInstanceItem().getType(), 36000);
            di.registerTeam(this);
        }
        arena.addCallback(player);
     
        // Architect Haste
        int haste = plugin.getJSON().getEnchantLevel(mcPlayer.getUniqueId(), "haste");
        if (haste > 0) {
            ItemStack item = mcPlayer.getInventory().getItemInMainHand();
            if (item == null || item.getType() != Material.IRON_PICKAXE) {
                return;
            }
            
            mcPlayer.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 30 * 60 * 20, haste * 2 - 1));
        }
        
        // Potion effect passive activation
        Pair<Ability, Integer> jsonPassive = plugin.getJSON().getPassive(
                plugin.getJSON().getPlayerPreset(player.getMCPlayerId()), Type.PASSIVE);
        Ability passive = jsonPassive.getLeft();
        int level = jsonPassive.getRight();
        if (level <= 0) {
            return;
        }
        
        int amp = (int) ConfigUtils.getAbilityStat(passive, level, Stat.AMPLIFIER);
        if (amp == 0) {
            return;
        }
        
        switch (passive) {
            case BUNNY:
                mcPlayer.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30 * 60 * 20, amp));
                break;
            case ADRENALINE:
                mcPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30 * 60 * 20, amp));
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
        meta.setColor(DyeColor.valueOf(name.toString().toUpperCase()).getColor());
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
        members.remove(player);
        InventoryUtils.clearInventory(mcPlayer);
        player.stopDeck();
        player.resetPlayer();
        mcPlayer.setLevel(0);
        mcPlayer.setExp(0F);
        mcPlayer.setGlowing(false);
        mcPlayer.setWorldBorder(null);
        AstralTurretManager.getInstance().unregisterPlayer(mcPlayer, false);
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
        return registerPortalBreak(loc, -1);
    }
    
    /**
     * @param loc
     * @param secondsBeforeRestore how long to wait before restoring portal as alive.
     * Set to 0 or below to not restore the portal
     * @return
     */
    public boolean registerPortalBreak(Location loc, int secondsBeforeRestore) {
        // Trace portal block to the most positive x and y positions
        Location p1 = loc.clone();
        while (p1.clone().add(1, 0, 0).getBlock().getType() != Material.OBSIDIAN) {
            p1.add(1, 0, 0);
        }
        
        while (p1.clone().add(0, 1, 0).getBlock().getType() != Material.OBSIDIAN) {
            p1.add(0, 1, 0);
        }
        
        // Play effects and break portal using FAWE to prevent lag from large portals
        Bukkit.getScheduler().runTaskAsynchronously(MissileWarsPlugin.getPlugin(), () -> {
            Location p2 = loc.clone();
            while (p2.clone().add(-1, 0, 0).getBlock().getType() != Material.OBSIDIAN) {
                p2.add(-1, 0, 0);
            }
            
            while (p2.clone().add(0, -1, 0).getBlock().getType() != Material.OBSIDIAN) {
                p2.add(0, -1, 0);
            }
            
            // Replace all portal blocks with air (because portals can be broken by liquids)
            World world = loc.getWorld();
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
            Region region = new CuboidRegion(
                weWorld,
                BlockVector3.at(p1.getBlockX(), p1.getBlockY(), p1.getBlockZ()),
                BlockVector3.at(p2.getBlockX(), p2.getBlockY(), p2.getBlockZ())
            );
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                editSession.replaceBlocks(
                    region, 
                    new SingleBlockTypeMask(weWorld, BlockTypes.NETHER_PORTAL), 
                    BlockTypes.AIR
                );
            }

            // Find midpoint and play SFX
            // Sounds need to be played sync
            Vector mid = p2.toVector().getMidpoint(p1.toVector());
            Location midLoc = new Location(world, mid.getX(), mid.getY(), mid.getZ());
            Bukkit.getScheduler().runTask(MissileWarsPlugin.getPlugin(), () -> {
                world.playSound(midLoc, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.8f);
                world.playSound(midLoc, Sound.BLOCK_BEACON_DEACTIVATE, 2.0f, 1.0f);
            });
        });
        
        // Return if no registered portal at location, or somehow already broken
        ClassicPortal portal = portals.get(p1);
        if (portal == null || !portal.isAlive()) {
            return false;
        }
        
        portal.setAlive(false);
        
        // Reset this to true after 5 sec if don't count
        if (secondsBeforeRestore > 0) {
            ConfigUtils.schedule(secondsBeforeRestore * 20, () -> portal.setAlive(true));
        }
        
        return true;
    }
    
    public void addPortal(Location loc) {
        portals.put(loc, new ClassicPortal(loc));
    }

    /**
     * Obtain whether the team has a living portal.
     *
     * @return true if either the first or second portal for this team exists
     */
    public boolean hasLivingPortal() {
        return getRemainingPortals() > 0;
    }
    
    /**
     * Get remaining portals
     * 
     * @return
     */
    public int getRemainingPortals() {
        return (int) portals.values().stream().filter(p -> p.isAlive()).count();
    }
    
    public int getTotalPortals() {
        return portals.size();
    }
    
    /**
     * Show or hide portals for the specified player.
     * 
     * @param player
     * @param show shows if true, hides if false
     */
    public void setPortalGlow(MissileWarsPlayer player, boolean show) {
        if (show) {
            portals.values().forEach(p -> p.glow(player));
        } else {
            portals.values().forEach(p -> p.hideGlow(player));
        }
    }
    
    /**
     * Kills the glowing entity for all portals
     * 
     * @param reset whether to respawn the glows
     */
    public void destroyPortalGlow(boolean reset) {
        if (reset) {
            portals.values().forEach(p -> p.resetGlow());
        } else {
            portals.values().forEach(p -> p.removeGlow());
        }
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
        if (ArenaUtils.inShield(arena, location, name)) {
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
}
