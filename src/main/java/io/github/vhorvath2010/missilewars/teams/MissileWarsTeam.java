package io.github.vhorvath2010.missilewars.teams;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Represents a team of Missile Wars Players. */
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
    /** Whether the first and second portals are destroyed. Default to false. */
    private boolean firstPortalDestroyed, secondPortalDestroyed;

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
        this.spawn = spawn;
        this.arena = arena;
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
        mcPlayer.getInventory().clear();
        new BukkitRunnable() {
        	@Override
        	public void run() {
		        mcPlayer.getInventory().setChestplate(createColoredArmor(Material.LEATHER_CHESTPLATE));
		        mcPlayer.getInventory().setLeggings(createColoredArmor(Material.LEATHER_LEGGINGS));
		        mcPlayer.getInventory().setBoots(createColoredArmor(Material.LEATHER_BOOTS));
        	}
        }.runTaskLater(MissileWarsPlugin.getPlugin(), 5);
    }

    /**
     * Add a player to the team.
     *
     * @param player the player to add
     */
    public void addPlayer(MissileWarsPlayer player) {
        // Send messages
        broadcastConfigMsg("messages.queue-join-others", player);
        members.add(player);
        ConfigUtils.sendConfigMessage("messages.queue-join", player.getMCPlayer(), arena, null);

        // Assign default deck
        player.setDeck(MissileWarsPlugin.getPlugin().getDeckManager().getDefaultDeck());

        // TP to team spawn and give armor
        Player mcPlayer = player.getMCPlayer();
        mcPlayer.teleport(spawn);
        mcPlayer.setHealth(20);
        mcPlayer.setGameMode(GameMode.SURVIVAL);
        giveItems(player);
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
            player.givePoolItem();
        }
    }

    /** Schedule the distribution of in-game Deck items. */
    public void scheduleDeckItems() {
        FileConfiguration settings = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder().toString(),
                "default-settings.yml");
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
                    arena.setXpLevel(finalSecInCd);
                }
            }.runTaskLater(MissileWarsPlugin.getPlugin(), (secsBetween - secInCd) * 20);
        }

        poolItemRunnable = new BukkitRunnable() {
            @Override
            public void run() {
                // Distribute items
                for (MissileWarsPlayer player : members) {
                    player.givePoolItem();
                }
                // Enqueue next distribution
                scheduleDeckItems();
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(),  secsBetween * 20L);
    }

    /** Stop the distribution of in-game Deck items. */
    public void stopDeckItems() {
        poolItemRunnable.cancel();
    }

    /**
     * Remove a given player from the team.
     *
     * @param player the player to remove
     */
    public void removePlayer(MissileWarsPlayer player) {
        if (members.contains(player)) {
            player.getMCPlayer().sendMessage(ConfigUtils.getConfigText("messages.leave-team", player.getMCPlayer(), arena,
                    player.getMCPlayer()));
            members.remove(player);
            broadcastConfigMsg("messages.leave-team-others", player);
        }
    }

    /**
     * Register a portal break at a given location.
     *
     * @param loc the location
     * @return true if a portal's broken status was changed
     */
    public boolean registerPortalBreak(Location loc) {
        // Check if portal break was within location of first portal
        FileConfiguration mapsConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "maps.yml");
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int x1 = mapsConfig.getInt("default-map.portal.x1");
        int y1 = mapsConfig.getInt("default-map.portal.y1");
        int x2 = mapsConfig.getInt("default-map.portal.x2");
        int y2 = mapsConfig.getInt("default-map.portal.y2");
        if (!firstPortalDestroyed && x1 <= x && x2 >= x && y1 <= y && y2 >= y) {
            firstPortalDestroyed = true;
            return true;
        }

        // Check if second portal was broken
        int x3 = mapsConfig.getInt("default-map.portal.x3");
        int y3 = mapsConfig.getInt("default-map.portal.y3");
        int x4 = mapsConfig.getInt("default-map.portal.x4");
        int y4 = mapsConfig.getInt("default-map.portal.y4");
        if (!secondPortalDestroyed && x3 <= x && x4 >= x && y3 <= y && y4 >= y) {
            secondPortalDestroyed = true;
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
        return !firstPortalDestroyed || !secondPortalDestroyed;
    }

    /**
     * Send the team a title at a given path.
     *
     * @param path the path
     */
    public void sendTitle(String path) {
        // Find titles and subtitles from config
        String title = ConfigUtils.getConfigText("titles." + path + ".title", null, null, null);
        List<String> subtitles = ConfigUtils.getConfigTextList("titles." + path + ".subtitle", null,
                null, null);
        String subtitle;
        if (!subtitles.isEmpty()) {
            subtitle = subtitles.get(new Random().nextInt(subtitles.size()));
        } else {
            subtitle = ConfigUtils.getConfigText("titles." + path + ".subtitle", null, null,
                    null);
        }
        
        int length = Integer.parseInt(ConfigUtils.getConfigText("titles." + path + ".length", null, null, null));

        // Send titles to players
        for (MissileWarsPlayer member : members) {
        	Player player = member.getMCPlayer();
            player.sendTitle(title, subtitle, 10, length, 20);
            ConfigUtils.sendConfigSound(path, player);
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
    		ConfigUtils.sendConfigSound(path, player);;
    	}
    }
}
