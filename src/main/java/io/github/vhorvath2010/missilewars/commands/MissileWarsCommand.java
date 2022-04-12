package io.github.vhorvath2010.missilewars.commands;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;

public class MissileWarsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Send info if no action taken
        if (args.length == 0) {
            sendErrorMsg(sender, "Usage: /umw <CreateArena/OpenGameMenu/EnqueueRed/EnqueueBlue/ForceStart>");
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
            Player target = getCommandTarget(args, sender);
            if (target == null) {
                sendErrorMsg(sender, "No target found!");
                return true;
            }
            arenaManager.openArenaSelector(target);

            sendSuccessMsg(sender, "Game selector opened!");
            return true;
        }
        
        // Join the fullest available game
        if (action.equalsIgnoreCase("Join") && sender instanceof Player) {

            Player player = (Player) sender;
            
            // Require player to be in the lobby
            if (!player.getWorld().getName().equals("world")) {
                sendErrorMsg(sender, "You must be in the lobby to use this!");
                return true;
            }
            
            // Allow player to join the fullest arena, or specify an arena name
            if (args.length == 1) {
                for (Arena arena : arenaManager.getLoadedArenas()) {
                    if (arena.getNumPlayers() < arena.getCapacity()) {
                        if (arena.joinPlayer(player)) {
                            return true;
                        }
                    }
                }
            } else if (args.length >= 2) {
                for (Arena arena : arenaManager.getLoadedArenas()) {
                    if (arena.getName().equalsIgnoreCase(args[1])) {
                        if (arena.joinPlayer(player)) {
                            return true;
                        } else {
                            ConfigUtils.sendConfigMessage("messages.arena-full", player, arena, null);
                            return true;
                        }
                    }
                }
                
                sendErrorMsg(sender, "Please specify a valid arena name!");
                return true;      
            }
            
            sendErrorMsg(sender, "All arenas are full! Please open the menu and choose one to spectate.");
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
            Player target = getCommandTarget(args, sender);
            if (target == null) {
                sendErrorMsg(sender, "No target found!");
                return true;
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
            Player target = getCommandTarget(args, sender);
            if (target == null) {
                sendErrorMsg(sender, "No target found!");
                return true;
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

        // Force start an arena
        if (action.equalsIgnoreCase("ForceStart")) {
            // Ensure sender has permission
            if (!sender.hasPermission("umw.force-start")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }

            // Check for arena and start it
            if (args.length < 2) {
                sendErrorMsg(sender, "You must specify an arena name!");
                return true;
            }
            Arena target = arenaManager.getArena(args[1]);
            if (target == null) {
                sendErrorMsg(sender, "Arena not found!");
                return true;
            }
            if (target.start()) {
                sendSuccessMsg(sender, "Arena started!");
            } else {
                sendErrorMsg(sender, "Arena is already running!");
            }
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

    /**
     * Get the targeted player of a command.
     *
     * @param args the commands arguments
     * @param sender the sender of the command
     * @return the player to be targeted by the command. Null if there is no viable player
     */
    private Player getCommandTarget(String[] args, CommandSender sender) {
        Player target = null;
        if (args.length == 2) {
            Player possibleTarget = Bukkit.getPlayer(args[1]);
            if (possibleTarget != null) {
                target = possibleTarget;
            } else {
                sendErrorMsg(sender, "Targeted player not found!");
                return null;
            }
        } else {
            if (!(sender instanceof Player)) {
                sendErrorMsg(sender, "You are not a player!");
                return null;
            }
            target = (Player) sender;
        }
        return target;
    }

}
