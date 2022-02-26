package io.github.vhorvath2010.missilewars.commands;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MissileWarsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Ensure user is a player
        if (!(sender instanceof Player)) {
            sendErrorMsg(sender, "You must be a player to do that!");
            return true;
        }
        Player player = (Player) sender;

        // Send info if no action taken
        if (args.length == 0) {
            sendErrorMsg(player, "Usage: /umw <CreateArena/OpenGameMenu>");
            return true;
        }

        // Check for arena creation
        String action = args[0];
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        ArenaManager arenaManager = plugin.getArenaManager();
        if (action.equalsIgnoreCase("CreateArena")) {
            // Ensure player is allowed to create an arena
            if (!player.hasPermission("umw.create-arena")) {
                sendErrorMsg(player, "You do not have permission to do that!");
                return true;
            }

            // Validate given arena name
            if (args.length < 2) {
                sendErrorMsg(player, "Usage: /umw CreateArena <arena-name>");
                return true;
            }
            String arenaName = args[1];
            if (arenaManager.getArena(arenaName) != null) {
                sendErrorMsg(player, "An arena with that name already exists!");
                return true;
            }

            // Create new arena
            if (arenaManager.createArena(arenaName, player)) {
                sendSuccessMsg(player, "New arena created!");
                return true;
            }
        }

        // Open game selector
        if (action.equalsIgnoreCase("OpenGameMenu")) {
            // Ensure player is allowed to open game menu
            if (!player.hasPermission("umw.create-arena")) {
                sendErrorMsg(player, "You do not have permission to do that!");
                return true;
            }

            // Check if opening for another player
            Player target = player;
            if (args.length == 2) {
                Player possibleTarget = Bukkit.getPlayer(args[1]);
                if (possibleTarget != null) {
                    target = possibleTarget;
                } else {
                    sendErrorMsg(player, "Targeted player not found!");
                    return true;
                }
            }
            arenaManager.openArenaSelector(target);
            sendSuccessMsg(player, "Game selector opened!");
            return true;
        }
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
