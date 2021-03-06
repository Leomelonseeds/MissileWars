package com.leomelonseeds.missilewars.arenas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;

public class TourneyArena extends Arena {

    public TourneyArena(String name, int capacity) {
        super(name, capacity);
        gamemode = "tourney";
    }

    /**
     * Constructor from a serialized Arena.
     *
     * @param serializedArena the yml serialized Arena
     */
    public TourneyArena(Map<String, Object> serializedArena) {
        super(serializedArena);
    }
    
    /**
     * Attempt to add a player to the Arena.
     *
     * @param player the player
     * @return true if the player joined the Arena, otherwise false
     */
    @Override
    public boolean joinPlayer(Player player) {

        // Ensure world isn't resetting
        if (resetting) {
            ConfigUtils.sendConfigMessage("messages.arena-full", player, this, null);
            return false;
        }
        
        // Ensure player can play >:D
        if (player.hasPermission("umw.new")) {
            ConfigUtils.sendConfigMessage("messages.watch-the-fucking-video", player, this, null);
            return false;
        }

        player.teleport(getPlayerSpawn(player));

        // Make sure another plugin hasn't cancelled the event
        if (player.getWorld().getName().equals("world")) {
            ConfigUtils.sendConfigMessage("messages.leave-parkour", player, this, null);
            return false;
        }

        InventoryUtils.saveInventory(player, true);
        InventoryUtils.clearInventory(player);

        ConfigUtils.sendConfigMessage("messages.join-arena", player, this, null);

        for (MissileWarsPlayer mwPlayer : players) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-others", mwPlayer.getMCPlayer(), null, player);
        }

        ConfigUtils.sendConfigMessage("messages.joined-arena", player, this, null);
        ConfigUtils.sendConfigMessage("messages.joined-arena-ranked", player, this, null);
        TextChannel discordChannel = DiscordSRV.getPlugin().getMainTextChannel();
        discordChannel.sendMessage(":arrow_backward: " + player.getName() + " left and joined arena " + this.getName()).queue();

        player.setHealth(20);
        player.setFoodLevel(20);
        players.add(new MissileWarsPlayer(player.getUniqueId()));
        player.setBedSpawnLocation(getPlayerSpawn(player), true);
        player.setGameMode(GameMode.ADVENTURE);

        for (Player worldPlayer : Bukkit.getWorld("world").getPlayers()) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-lobby", worldPlayer, this, player);
        }

        // Check for game start
        checkForStart();
        return true;
    }
    
    /**
     * Enqueue a player with a given UUID to the red team.
     *
     * @param uuid the Player's UUID
     */
    @Override
    public void enqueueRed(UUID uuid) {
        for (MissileWarsPlayer player : players) {
            if (player.getMCPlayerId().equals(uuid)) {
                
               // Make sure people can't break the game
                if (startTime != null) {
                    int time = (int) Duration.between(LocalDateTime.now(), startTime).toSeconds();
                    if (time <= 2 && time >= -2) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-time", player.getMCPlayer(), this, null);
                        return;
                    }
                }
                
                if (!running) {
                    if (!redQueue.contains(player)) {
                        if (redQueue.size() >= getCapacity() / 2) {
                            ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), this, null);
                        } else {
                            blueQueue.remove(player);
                            redQueue.add(player);
                            ConfigUtils.sendConfigMessage("messages.queue-waiting-red", player.getMCPlayer(), this, null);
                            removeSpectator(player);
                        }
                    } else {
                        redQueue.remove(player);
                        ConfigUtils.sendConfigMessage("messages.queue-leave-red", player.getMCPlayer(), this, null);
                    }
                } else {
                    if (!player.getMCPlayer().isOp() && redTeam.getSize() - blueTeam.getSize() >= 1) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-error", player.getMCPlayer(), this, null);
                    } else if (!player.getMCPlayer().hasPermission("umw.joinfull") && redTeam.getSize() >= getCapacity() / 2) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), this, null);
                    } else {
                        removeSpectator(player);
                        blueTeam.removePlayer(player);
                        redTeam.addPlayer(player, true);
                        redTeam.giveItems(player);
                        player.giveDeckGear();
                        announceMessage("messages.queue-join-red", player);
                    }
                }
                break;
            }
        }
    }

    /**
     * Enqueue a player with a given UUID to the blue team.
     *
     * @param uuid the Player's UUID
     */
    @Override
    public void enqueueBlue(UUID uuid) {
        for (MissileWarsPlayer player : players) {
            if (player.getMCPlayerId().equals(uuid)) {
                
                // Make sure people can't break the game
                if (startTime != null) {
                    int time = (int) Duration.between(LocalDateTime.now(), startTime).toSeconds();
                    if (time <= 2 && time >= -2) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-time", player.getMCPlayer(), this, null);
                        return;
                    }
                }
                
                if (!running) {
                    if (!blueQueue.contains(player)) {
                        if (blueQueue.size() >= getCapacity() / 2) {
                            ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), this, null);
                        } else {
                            redQueue.remove(player);
                            blueQueue.add(player);
                            ConfigUtils.sendConfigMessage("messages.queue-waiting-blue", player.getMCPlayer(), this, null);
                            removeSpectator(player);
                        }
                    } else {
                        blueQueue.remove(player);
                        ConfigUtils.sendConfigMessage("messages.queue-leave-blue", player.getMCPlayer(), this, null);
                    }
                } else {
                    if (!player.getMCPlayer().isOp() && blueTeam.getSize() - redTeam.getSize() >= 1) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-error", player.getMCPlayer(), this, null);
                    } else if (!player.getMCPlayer().hasPermission("umw.joinfull") && blueTeam.getSize() >= getCapacity() / 2) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-full", player.getMCPlayer(), this, null);
                    } else {
                        removeSpectator(player);
                        redTeam.removePlayer(player);
                        blueTeam.addPlayer(player, true);
                        blueTeam.giveItems(player);
                        player.giveDeckGear();
                        announceMessage("messages.queue-join-blue", player);
                    }
                }
                break;
            }
        }
    }
    
    @Override
    public void checkForStart() {}
    
    /**
     * Assigns players and starts the teams
     */
    @Override
    public void startTeams() {
        // Assign players to teams based on queue (which removes their items)
        List<MissileWarsPlayer> toAssign = new ArrayList<>();
        for (MissileWarsPlayer player : players) {
            if (!spectators.contains(player)) {
                toAssign.add(player);
            }
        }
        Collections.shuffle(toAssign);
        double maxQueue = Math.ceil((double) players.size() / 2);
        
        // Teleport all players to center to remove lobby minigame items/dismount
        for (MissileWarsPlayer player : players) {
            player.getMCPlayer().teleport(getPlayerSpawn(player.getMCPlayer()));
            player.getMCPlayer().closeInventory();
        }
        
        new BukkitRunnable() {
            @Override
            public void run() {
                // Assign queued players. If a queue is larger than a team size put remaining
                // players into the front of the queue to be assigned first into random teams
                while (!blueQueue.isEmpty() || !redQueue.isEmpty()) {
                    if (!redQueue.isEmpty()) {
                        MissileWarsPlayer toAdd = redQueue.remove();
                        toAssign.remove(toAdd);
                        if (redTeam.getSize() < maxQueue) {
                            redTeam.addPlayer(toAdd, true);
                        } else {
                            toAssign.add(0, toAdd);
                        }
                    }
                    if (!blueQueue.isEmpty()) {
                        MissileWarsPlayer toAdd = blueQueue.remove();
                        toAssign.remove(toAdd);
                        if (blueTeam.getSize() < maxQueue) {
                            blueTeam.addPlayer(toAdd, true);
                        } else {
                            toAssign.add(0, toAdd);
                        }
                    }
                }

                // Send messages
                redTeam.sendSound("game-start");
                blueTeam.sendSound("game-start");
                redTeam.sendTitle("classic-start");
                blueTeam.sendTitle("classic-start");
                for (MissileWarsPlayer p : players) {
                    if (!getTeam(p.getMCPlayerId()).equals("no team")) {
                        p.giveDeckGear();
                    }
                }
                givePoolItems();
                scheduleItemsRanked();
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), 5L);
        // Start deck distribution for each team and send messages
    }
    
    /**
     * Similar to normal deck distribution, except everyone gets the same things
     */
    public void scheduleItemsRanked() {
        FileConfiguration settings = MissileWarsPlugin.getPlugin().getConfig();
        double timeBetween = 15;
        if (blueTeam.isChaos()) {
            timeBetween /= settings.getInt("chaos-mode.multiplier");
        }

        int secsBetween = (int) Math.floor(timeBetween);

        // Setup level countdown till distribution
        for (int secInCd = secsBetween; secInCd > 0; secInCd--) {
            int finalSecInCd = secInCd;
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (MissileWarsPlayer player : players) {
                        player.getMCPlayer().setLevel(finalSecInCd);
                    }
                }
            }.runTaskLater(MissileWarsPlugin.getPlugin(), (secsBetween - secInCd) * 20);
        }

        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                givePoolItems();
                scheduleItemsRanked();
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(),  secsBetween * 20L));
    }
    
    /**
     * Gives items
     */
    protected void givePoolItems() {
        Random random = new Random();
        int i_missile = random.nextInt(0, 5);
        int i_utility = random.nextInt(0, 3);
        double chance = 0.33;
        double rng = random.nextDouble();
        for (MissileWarsPlayer mwplayer : players) {
            if (!getTeam(mwplayer.getMCPlayerId()).equals("no team")) {
                Deck deck = mwplayer.getDeck();
                List<ItemStack> missiles = deck.getMissiles();
                List<ItemStack> utility = deck.getUtility();
                ItemStack poolItem;
                if (rng < chance) {
                    poolItem = new ItemStack(utility.get(i_utility));
                } else {
                    poolItem = new ItemStack(missiles.get(i_missile));
                }
                
                Player player = mwplayer.getMCPlayer();
                double toohigh = ConfigUtils.getMapNumber(getGamemode(), getMapName(), "too-high");
                double toofar = ConfigUtils.getMapNumber(getGamemode(), getMapName(), "too-far");
                Location loc = player.getLocation();
                
                // Don't give item if they are out of bounds
                if (loc.getBlockY() > toohigh || loc.getBlockX() < toofar) {
                    deck.refuseItem(player, poolItem, "messages.out-of-bounds");
                    continue;
                }
                
                // Don't give item if their inventory space is full
                if (!mwplayer.getDeck().hasInventorySpace(player, true)) {
                    deck.refuseItem(player, poolItem, "messages.inventory-limit");
                    continue;
                }
                
                // Check if can add to offhand
                ItemStack offhand = player.getInventory().getItemInOffHand();
                if (offhand.isSimilar(poolItem)) {
                    while (offhand.getAmount() < offhand.getMaxStackSize() && poolItem.getAmount() > 0) {
                        offhand.add();
                        poolItem.subtract();
                    }
                }
                
                player.getInventory().addItem(poolItem);
            }
        }
    }
    
    /** Remove Players from the map. */
    @Override
    public void removePlayers() {
        for (MissileWarsPlayer player : new HashSet<>(players)) {
            removePlayer(player.getMCPlayerId(), true);
        }
    }
}
