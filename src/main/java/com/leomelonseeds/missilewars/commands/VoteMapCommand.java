package com.leomelonseeds.missilewars.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.invs.MapVoting;

public class VoteMapCommand extends MissileWarsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Ensure user is a player
        if (!(sender instanceof Player)) {
            sendErrorMsg(sender, "You are not a player!");
        }
        Player player = (Player) sender;

        // Ensure player is in an arena that is not running
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());
        if (playerArena == null) {
            sendErrorMsg(player, "You are not in an arena!");
            return true;
        }
        if (playerArena.isRunning() || playerArena.isResetting()) {
            sendErrorMsg(player, "The game has already started!");
            return true;
        }

        // Open voting menu
        new MapVoting(player);

        return true;
    }
}
