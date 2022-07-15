package com.leomelonseeds.missilewars.commands;

import java.util.logging.Level;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.TourneyArena;
import com.leomelonseeds.missilewars.invs.ArenaSelector;
import com.leomelonseeds.missilewars.invs.PresetSelector;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class MissileWarsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Send info if no action taken
        if (args.length == 0) {
            sendErrorMsg(sender, "Usage: /umw <CreateArena/DeleteArena/Join/Leave/OpenGameMenu/EnqueueRed/EnqueueBlue/ForceStart>");
            return true;
        }

        // Check for arena creation
        String action = args[0];
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        ArenaManager arenaManager = plugin.getArenaManager();
        
        // A command specifically used to test passives/abilities
        if (action.equalsIgnoreCase("set")) {
            // Ensure player is allowed to create an arena
            if (!sender.hasPermission("umw.set")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (args.length != 4) {
                sendErrorMsg(sender, "Usage: /umw set [gpassive/passive/ability] [name] [level]");
                return true;
            }
            
            // The admin gotta be smart man
            String type = args[1];
            String name = args[2];
            int level = 0;
            try {
                level = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sendErrorMsg(sender, "You suck, use a number next time (" + args[3] + ")");
                return true;
            }
            
            JSONObject json = plugin.getJSON().getPlayerPreset(player.getUniqueId());
            if (!json.has(type)) {
                sendErrorMsg(sender, "You suck, use gpassive/passive/ability please");
                return true; 
            }
            
            // Make sure shit that isn't supposed to happen can't happen
            if (name.equalsIgnoreCase("none")) {
                name = "None";
                level = 0;
            }
            
            JSONObject actual = json.getJSONObject(type);
            actual.put("selected", name);
            actual.put("level", level);
            
            sendSuccessMsg(sender, "You set your " + type + " " + name + " to " + level);
            return true; 
        }
        
        if (action.equalsIgnoreCase("DeleteArena")) {
            // Ensure player is allowed to create an arena
            if (!sender.hasPermission("umw.delete-arena")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }

            // Validate given arena name
            if (args.length < 2) {
                sendErrorMsg(sender, "Usage: /umw DeleteArena <arena-name>");
                return true;
            }
            String arenaName = args[1];
            Arena toRemove = arenaManager.getArena(arenaName);

            if (toRemove == null) {
                sendErrorMsg(sender, "That arena does not exist!");
                return true;
            }

            // Delete the arena
            if (arenaManager.removeArena(toRemove)) {
                sendSuccessMsg(sender, "The arena has been deleted!");
                return true;
            } else {
                sendErrorMsg(sender, "Something went wrong deleting the arena. Notify an admin.");
                return true;
            }
        }

        if (action.equalsIgnoreCase("CreateArena")) {
            // Ensure player is allowed to delete an arena
            if (!sender.hasPermission("umw.create-arena")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }

            // Validate given arena name
            if (args.length < 3) {
                sendErrorMsg(sender, "Usage: /umw CreateArena <arena-name> [type] [capacity]");
                return true;
            }
            String arenaName = args[1];
            if (arenaManager.getArena(arenaName) != null) {
                sendErrorMsg(sender, "An arena with that name already exists!");
                return true;
            }

            int arenaCapacity = MissileWarsPlugin.getPlugin().getConfig().getInt("arena-cap");
            String arenaType = args[2];

            if (args.length > 3) {
                try {
                    arenaCapacity = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sendErrorMsg(sender, "Capacity must be a number!");
                    return true;
                }
            }

            // Create new arena
            if (arenaManager.createArena(arenaName, arenaType, arenaCapacity)) {
                sendSuccessMsg(sender, "New arena created!");
                return true;
            } else {
                sendErrorMsg(sender, "Something went wrong creating the arena. Notify an admin.");
                return true;
            }
        }

        // Update all arenas. Might take a while
        if (action.equalsIgnoreCase("PerformArenaUpgrade")) {
            if (sender instanceof Player) {
                return false;
            }
            MissileWarsPlugin.getPlugin().getArenaManager().performArenaUpgrade();
            return true;
        }

        // Clear inventories the umw way
        if (action.equalsIgnoreCase("clear")) {

            if (args.length == 1) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    InventoryUtils.clearInventory(player);
                    sendSuccessMsg(sender, "Inventory cleared!");
                    return true;
                } else {
                    sendSuccessMsg(sender, "Must specify a target.");
                    return true;
                }
            } else if (args.length == 2) {
                Player target = getCommandTarget(args, sender);
                if (target == null) {
                    sendErrorMsg(sender, "No target found!");
                    return true;
                }

                InventoryUtils.clearInventory(target);
                return true;
            }
        }

        // Open game selector
        if (action.equalsIgnoreCase("OpenGameMenu")) {
            // Ensure player is allowed to open game menu
            if (!sender.hasPermission("umw.staff")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }
            
            if (args.length < 3) {
                sendErrorMsg(sender, "Syntax: /umw opengamemenu [player] [gamemode]");
                return true;
            }

            // Check if opening for another player
            String possibleTarget = args[1];
            Player target = Bukkit.getPlayer(possibleTarget);
            if (target == null) {
                sendErrorMsg(sender, "No target found!");
                return true;
            }
            
            String gamemode = args[2];
            new ArenaSelector(target, gamemode);
            return true;
        }

        // Quit to waiting lobby of a game
        if (action.equalsIgnoreCase("Leave") && sender instanceof Player) {

            Player player = (Player) sender;
            Arena arena = arenaManager.getArena(player.getUniqueId());
            if (arena == null) {
                sendErrorMsg(sender, "You must be in a game to do this!");
                return true;
            }

            if (!(arena.leaveGame(player.getUniqueId()))) {
                sendErrorMsg(sender, "You cannot do this now!");
                return true;
            }

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
            
            // Ensure player can play >:D
            if (player.hasPermission("umw.new")) {
                ConfigUtils.sendConfigMessage("messages.watch-the-fucking-video", player, null, null);
                return false;
            }

            // Allow player to join the fullest arena, or specify an arena name
            if (args.length == 1) {
                int cap = MissileWarsPlugin.getPlugin().getConfig().getInt("arena-cap");
                for (Arena arena : arenaManager.getLoadedArenas("classic", Arena.byPlayers)) {
                    if (arena.getCapacity() == cap && arena.getNumPlayers() < arena.getCapacity() && !arena.isResetting()) {
                        arena.joinPlayer(player);
                        return true;
                    }
                }
            } else if (args.length >= 2) {
                for (Arena arena : arenaManager.getLoadedArenas()) {
                    if (arena.getName().equalsIgnoreCase(args[1])) {
                        arena.joinPlayer(player);
                        return true;
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
            if (args.length == 1) {
                Player player = (Player) sender;
                // Check if player is in arena
                Arena arena = arenaManager.getArena(player.getUniqueId());
                if (arena == null) {
                    sendErrorMsg(sender, "You are not in an arena!");
                    return true;
                }
                
                if (arena instanceof TourneyArena) {
                    ConfigUtils.sendConfigMessage("messages.queue-deny-tourney", player, null, null);
                    return true;
                }
                
                if (!(arena.getTeam(player.getUniqueId()).equals("no team") || sender.hasPermission("umw.enqueue"))) {
                    sendErrorMsg(sender, "You are already on a team!");
                    return true;
                }
                
                arena.enqueueRed(player.getUniqueId());
                return true;
            }
            
            // Ensure player is allowed
            if (!sender.hasPermission("umw.staff")) {
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
            return true;
        }

        // Queue for blue team
        if (action.equalsIgnoreCase("EnqueueBlue")) {
            if (args.length == 1) {
                Player player = (Player) sender;
                // Check if player is in arena
                Arena arena = arenaManager.getArena(player.getUniqueId());
                if (arena == null) {
                    sendErrorMsg(sender, "You are not in an arena!");
                    return true;
                }
                
                if (arena instanceof TourneyArena) {
                    ConfigUtils.sendConfigMessage("messages.queue-deny-tourney", player, null, null);
                    return true;
                }
                
                if (!(arena.getTeam(player.getUniqueId()).equals("no team") || sender.hasPermission("umw.enqueue"))) {
                    sendErrorMsg(sender, "You are already on a team!");
                    return true;
                }
                
                arena.enqueueBlue(player.getUniqueId());
                return true;
            }
            
            // Ensure player is allowed
            if (!sender.hasPermission("umw.staff")) {
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
            return true;
        }

        // Force start an arena
        if (action.equalsIgnoreCase("Start")) {
            // Ensure sender has permission
            if (!sender.hasPermission("umw.staff")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }

            // Check for arena and start it
            if (args.length < 2) {
                sendErrorMsg(sender, "Syntax: /umw start [arena] <countdown>");
                return true;
            }
            
            int countdown = 0;
            if (args.length == 3) {
                try {
                    countdown = Integer.parseInt(args[2]);
                    if (countdown < 0) {
                        countdown = 0;
                    }
                } catch (NumberFormatException e) {
                    sendErrorMsg(sender, "That's not a number, dude");
                    return true;
                }
            }
            
            Arena target = arenaManager.getArena(args[1]);
            if (target == null) {
                sendErrorMsg(sender, "Arena not found!");
                return true;
            }
            if (target.isRunning()) {
                sendErrorMsg(sender, "Arena is already running!");
                return true;
            } 
            
            if (countdown == 0) {
                target.start();
            } else if (countdown < 10) {
                sendErrorMsg(sender, "Your countdown must be 0, or higher than 10.");
                return true;
            } else {
                target.scheduleStart(countdown);
            }
            sendSuccessMsg(sender, "Arena started!");
            return true;
        }
        
        if (action.equalsIgnoreCase("Give")) {
            // Ensure sender has permission
            if (!sender.hasPermission("umw.give")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }

            if (args.length != 4) {
                sendErrorMsg(sender, "Usage: /mw give [player] [item] [level]");
                return true;
            }

            try {
                Player player = Bukkit.getPlayer(args[1]);
                int level = Integer.parseInt(args[3]);
                ItemStack item = plugin.getDeckManager().createItem(args[2], level, false);
                player.getInventory().addItem(item);
                return true;
            } catch (Exception e) {
                sendErrorMsg(sender, "Bad arguments!");
                return true;
            }
        }
        
        if (action.equalsIgnoreCase("Deck")) {

            Player player = (Player) sender;

            Arena arena = arenaManager.getArena(player.getUniqueId());

            if (!(arena == null || arena.getTeam(player.getUniqueId()).equals("no team"))) {
                sendErrorMsg(sender, "You cannot change decks while playing.");
                return true;
            }

            if (args.length < 2) {
                sendErrorMsg(sender, "Usage: /mw deck Vanguard/Berserker/Sentinel/Architect [Preset]");
                return true;
            }

            String deck = StringUtils.capitalize(args[1].toLowerCase());

            if (!plugin.getDeckManager().getDecks().contains(deck)) {
                sendErrorMsg(sender, "Please specify a valid deck!");
                return true;
            }
            
            if (args.length == 3) {
                String preset = args[2].toUpperCase();
                if (!plugin.getDeckManager().getPresets().contains(preset)) {
                    sendErrorMsg(sender, "Please specify a valid preset!");
                    return true;
                }
                
                // Update deck in cache
                JSONObject currentDeck = plugin.getJSON().getPlayer(player.getUniqueId());
                currentDeck.put("Deck", deck);
                currentDeck.put("Preset", preset);

                sendSuccessMsg(sender, "Set your deck to " + deck + " - Preset " + preset + "!");
                return true;
            }
            
            // Open preset selector if player doesn't specify
            new PresetSelector(player, deck);
            return true;
        }
        
        if (action.equalsIgnoreCase("tutorial")) {
            if (args.length != 2) {
                return true;
            }
            String s = args[1];
            Player player = (Player) sender;
            if (s.equals("new")) {
                Bukkit.getLogger().log(Level.INFO, player.getName() + " is new to Missile Wars.");
                ConfigUtils.sendTitle("video", player);
                ConfigUtils.sendConfigMessage("messages.tutorial-new", player, null, null);
                return true;
            }
            if (s.equals("decks")) {
                Bukkit.getLogger().log(Level.INFO, player.getName() + " has played Missile Wars.");
                ConfigUtils.sendTitle("video", player);
                ConfigUtils.sendConfigMessage("messages.tutorial-decks", player, null, null);
                return true;
            }
        }

        return true;
    }

    /**
     * Send the given user an error message.
     *
     * @param target the user
     * @param msg the error message
     */
    protected void sendErrorMsg(CommandSender target, String msg) {
        target.sendMessage(ChatColor.RED + "Error: " + ChatColor.GRAY + msg);
    }

    /**
     * Send the given user a success message.
     *
     * @param target the user
     * @param msg the error message
     */
    protected void sendSuccessMsg(CommandSender target, String msg) {
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
