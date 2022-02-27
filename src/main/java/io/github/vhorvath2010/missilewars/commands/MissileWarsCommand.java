package io.github.vhorvath2010.missilewars.commands;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
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
        // Send info if no action taken
        if (args.length == 0) {
            sendErrorMsg(sender, "Usage: /umw <CreateArena/OpenGameMenu/EnqueueRed/EnqueueBlue>");
            return true;
        }

        // Check for arena creation
        String action = args[0];
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        ArenaManager arenaManager = plugin.getArenaManager();
        if (action.equalsIgnoreCase("CreateArena")) {
            // Ensure player is allowed to create an arena
            if (!sender.hasPermission("umw.create-arena")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }

            // Validate given arena name
            if (args.length < 2) {
                sendErrorMsg(sender, "Usage: /umw CreateArena <arena-name>");
                return true;
            }
            String arenaName = args[1];
            if (arenaManager.getArena(arenaName) != null) {
                sendErrorMsg(sender, "An arena with that name already exists!");
                return true;
            }

            // Create new arena
            if (arenaManager.createArena(arenaName, sender)) {
                sendSuccessMsg(sender, "New arena created!");
                return true;
            }
        }

        // Open game selector
        if (action.equalsIgnoreCase("OpenGameMenu")) {
            // Ensure player is allowed to open game menu
            if (!sender.hasPermission("umw.open-arena-menu")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }

            // Check if opening for another player
            if (args.length == 2) {
                Player possibleTarget = Bukkit.getPlayer(args[1]);
                if (possibleTarget != null) {
                    arenaManager.openArenaSelector(possibleTarget);
                } else {
                    sendErrorMsg(sender, "Targeted player not found!");
                    return true;
                }
            } else {
                if (!(sender instanceof Player)) {
                    sendErrorMsg(sender, "You are not a player!");
                }
                arenaManager.openArenaSelector((Player) sender);
            }
            sendSuccessMsg(sender, "Game selector opened!");
            return true;
        }

        // Queue for red team
        if (action.equalsIgnoreCase("EnqueueRed")) {
            // Ensure player is allowed to open team menu
            if (!sender.hasPermission("umw.enqueue")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }

            // Check if opening for another player
            Player target = null;
            if (args.length == 2) {
                Player possibleTarget = Bukkit.getPlayer(args[1]);
                if (possibleTarget != null) {
                    target = possibleTarget;
                } else {
                    sendErrorMsg(sender, "Targeted player not found!");
                    return true;
                }
            } else {
                if (!(sender instanceof Player)) {
                    sendErrorMsg(sender, "You are not a player!");
                }
                target = (Player) sender;
            }

            // Check if player is in arena
            Arena arena = arenaManager.getArena(target.getUniqueId());
            if (arena == null) {
                sendErrorMsg(sender, "Target is not in an arena");
                return true;
            }

            // Enqueue for red team
            arena.enqueueRed(target.getUniqueId());

            sendSuccessMsg(sender, "Enqueued player for red team!");
            return true;
        }

        // Queue for blue team
        if (action.equalsIgnoreCase("EnqueueBlue")) {
            // Ensure player is allowed to open team menu
            if (!sender.hasPermission("umw.enqueue")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }

            // Check if opening for another player
            Player target = null;
            if (args.length == 2) {
                Player possibleTarget = Bukkit.getPlayer(args[1]);
                if (possibleTarget != null) {
                    target = possibleTarget;
                } else {
                    sendErrorMsg(sender, "Targeted player not found!");
                    return true;
                }
            } else {
                if (!(sender instanceof Player)) {
                    sendErrorMsg(sender, "You are not a player!");
                }
                target = (Player) sender;
            }

            // Check if player is in arena
            Arena arena = arenaManager.getArena(target.getUniqueId());
            if (arena == null) {
                sendErrorMsg(sender, "Target is not in an arena");
                return true;
            }

            // Enqueue for red team
            arena.enqueueBlue(target.getUniqueId());

            sendSuccessMsg(sender, "Enqueued player for blue team!");
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
