package io.github.vhorvath2010.missilewars.commands;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.teams.MissileWarsPlayer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpectateCommand implements CommandExecutor {

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

    /**
     * Send the given user an error message.
     *
     * @param target the user
     * @param msg the error message
     */
    private void sendErrorMsg(CommandSender target, String msg) {
        target.sendMessage(ChatColor.RED + "Error: " + ChatColor.GRAY + msg);
    }

    /**
     * Send the given user a success message.
     *
     * @param target the user
     * @param msg the error message
     */
    private void sendSuccessMsg(CommandSender target, String msg) {
        target.sendMessage(ChatColor.GREEN + "Success! " + ChatColor.GRAY + msg);
    }

}
