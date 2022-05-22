package com.leomelonseeds.missilewars.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;

public class SpectateCommand extends MissileWarsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Ensure sender is a Player
        if (!(sender instanceof Player)) {
            sendErrorMsg(sender, "You must be a player");
            return true;
        }
        Player player = (Player) sender;

        // Try to find Arena
        ArenaManager arenaManager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = arenaManager.getArena(player.getUniqueId());
        if (arena == null) {
            sendErrorMsg(player, "You are not in an arena!");
            return true;
        }

        // Check if player is currently spectating
        MissileWarsPlayer missileWarsPlayer = arena.getPlayerInArena(player.getUniqueId());
        if (arena.isSpectating(missileWarsPlayer)) {
            arena.removeSpectator(missileWarsPlayer);
            return true;
        }

        // Allow player to spectate
        arena.addSpectator(player.getUniqueId());
        return true;
    }
}
