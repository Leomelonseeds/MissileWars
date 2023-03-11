package com.leomelonseeds.missilewars.arenas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import io.github.a5h73y.parkour.Parkour;

public class TrainingArena extends Arena {
    
    public TrainingArena(String name, int capacity) {
        super(name, capacity);
        this.gamemode = "training";
    }

    @Override
    public void enqueueRed(UUID uuid) {
        for (MissileWarsPlayer player : players) {
            if (player.getMCPlayerId().equals(uuid)) {
                ConfigUtils.sendConfigMessage("messages.training-blue-only", player.getMCPlayer(), this, null); 
            }
        }
    }
    
    @Override
    public void enqueueBlue(UUID uuid) {
        for (MissileWarsPlayer player : players) {
            if (player.getMCPlayerId().equals(uuid)) {
                
                // Make sure people can't break the game
                if (startTime != null) {
                    int time = (int) Duration.between(LocalDateTime.now(), startTime).toSeconds();
                    if (time <= 1 && time >= -1) {
                        ConfigUtils.sendConfigMessage("messages.queue-join-time", player.getMCPlayer(), this, null);
                        return;
                    }
                }
                
                if (!running) {
                    blueQueue.add(player);
                    ConfigUtils.sendConfigMessage("messages.queue-waiting-blue", player.getMCPlayer(), this, null);
                    removeSpectator(player);
                } else {
                    removeSpectator(player);
                    redTeam.removePlayer(player);
                    blueTeam.addPlayer(player);
                    player.giveDeckGear();
                    checkNotEmpty();
                    announceMessage("messages.queue-join-blue", player);
                }
                break;
            }
        }
    }
    
    @Override
    public boolean joinPlayer(Player player, boolean asSpectator) {

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

        // Make sure player not in parkour
        if (Parkour.getInstance().getParkourSessionManager().isPlayingParkourCourse(player)) {
            ConfigUtils.sendConfigMessage("messages.leave-parkour", player, this, null);
            return false;
        }
        
        // Save inventory if player in world
        if (player.getWorld().getName().equals("world")) {
            InventoryUtils.saveInventory(player, true);
        }

        player.teleport(getPlayerSpawn(player));
        InventoryUtils.clearInventory(player, true);
        ConfigUtils.sendConfigMessage("messages.join-arena", player, this, null);

        for (MissileWarsPlayer mwPlayer : players) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-others", mwPlayer.getMCPlayer(), null, player);
        }

        ConfigUtils.sendConfigMessage("messages.joined-training", player, this, null);
        TextChannel discordChannel = DiscordSRV.getPlugin().getMainTextChannel();
        discordChannel.sendMessage(":arrow_backward: " + player.getName() + " left and joined arena training").queue();

        player.setHealth(20);
        player.setFoodLevel(20);
        players.add(new MissileWarsPlayer(player.getUniqueId()));
        player.setBedSpawnLocation(getPlayerSpawn(player), true);
        player.setGameMode(GameMode.ADVENTURE);

        for (Player worldPlayer : Bukkit.getWorld("world").getPlayers()) {
            ConfigUtils.sendConfigMessage("messages.joined-arena-lobby", worldPlayer, this, player);
        }
        
        // Give player items
        ItemStack leave = MissileWarsPlugin.getPlugin().getDeckManager().createItem("held.to-lobby", 0, false);
        addHeldMeta(leave, "leave");
        player.getInventory().setItem(8, leave);
        
        // Auto-join team if setting turned on
        if (!player.hasPermission("umw.disableautoteam") && running) {
            enqueueBlue(player.getUniqueId());
        }

        // Check for game start
        checkForStart();
        return true;
    }
    
    @Override
    public void checkForStart() {
        if (running || resetting) {
            return;
        }
        if (getNumPlayers() >= 1) {
            scheduleStart(5);
        }
    }
    
    @Override
    public void checkEmpty() {}
    
    @Override
    public String getTimeRemaining() {
        return "∞";
    }
    
    @Override
    public void performTimeSetup() {}
    
    @Override
    protected void startTeams() {
        // Literally place everyone on blue
        for (MissileWarsPlayer player : players) {
            if (!spectators.contains(player)) {
                blueTeam.addPlayer(player);
            }
        }

        // Send messages
        redTeam.distributeGear();
        blueTeam.distributeGear();
        redTeam.scheduleDeckItems();
        blueTeam.scheduleDeckItems();
    }
}
