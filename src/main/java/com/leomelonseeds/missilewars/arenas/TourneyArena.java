package com.leomelonseeds.missilewars.arenas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class TourneyArena extends Arena {

    public TourneyArena(String name, int capacity) {
        super(name, capacity);
        gamemode = "tourney";
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
                            redTeam.addPlayer(toAdd);
                        } else {
                            toAssign.add(0, toAdd);
                        }
                    }
                    if (!blueQueue.isEmpty()) {
                        MissileWarsPlayer toAdd = blueQueue.remove();
                        toAssign.remove(toAdd);
                        if (blueTeam.getSize() < maxQueue) {
                            blueTeam.addPlayer(toAdd);
                        } else {
                            toAssign.add(0, toAdd);
                        }
                    }
                }

                // Send messages
                redTeam.distributeGear();
                redTeam.sendSound("game-start");
                blueTeam.distributeGear();
                blueTeam.sendSound("game-start");
                redTeam.sendTitle("classic-start");
                blueTeam.sendTitle("classic-start");
                
                giveItemsRanked();
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), 5L);
        // Start deck distribution for each team and send messages
    }
    
    /**
     * Similar to normal deck distribution, except everyone gets the same things
     */
    public void giveItemsRanked() {
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
                // Distribute items
                Random random = new Random();
                int i_missile = random.nextInt(0, 5);
                int i_utility = random.nextInt(0, 3);
                double chance = 0.375;
                double rng = random.nextDouble();
                for (MissileWarsPlayer mwplayer : players) {
                    if (!getTeam(mwplayer.getMCPlayerId()).equals("no team")) {
                        Deck deck = mwplayer.getDeck();
                        List<ItemStack> missiles = deck.getMissiles();
                        List<ItemStack> utility = deck.getUtility();
                        ItemStack poolItem;
                        if (rng < chance) {
                            poolItem = utility.get(i_utility);
                        } else {
                            poolItem = missiles.get(i_missile);
                        }
                        
                        Player player = mwplayer.getMCPlayer();
                        double toohigh = ConfigUtils.getMapNumber(getGamemode(), getMapName(), "too-high");
                        double toofar = ConfigUtils.getMapNumber(getGamemode(), getMapName(), "too-far");
                        Location loc = player.getLocation();
                        
                        // Don't give item if they are out of bounds
                        if (loc.getBlockY() > toohigh || loc.getBlockX() < toofar) {
                            deck.refuseItem(player, poolItem, "messages.out-of-bounds");
                            return;
                        }
                        
                        // Don't give item if their inventory space is full
                        if (!mwplayer.getDeck().hasInventorySpace(player, true)) {
                            deck.refuseItem(player, poolItem, "messages.inventory-limit");
                            return;
                        }
                        
                        // Check if can add to offhand
                        ItemStack offhand = player.getInventory().getItemInOffHand();
                        if (offhand.getType().toString().equals(poolItem.getType().toString()) && offhand.getMaxStackSize() != 1) {
                            offhand.setAmount(offhand.getAmount() + poolItem.getAmount());
                            return;
                        }
                        
                        player.getInventory().addItem(poolItem);
                    }
                }
                // Enqueue next distribution
                giveItemsRanked();
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(),  secsBetween * 20L));
    }
}
