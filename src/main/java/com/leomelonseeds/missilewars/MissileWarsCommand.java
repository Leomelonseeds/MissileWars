package com.leomelonseeds.missilewars;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.TourneyArena;
import com.leomelonseeds.missilewars.arenas.TutorialArena;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.decks.DeckStorage;
import com.leomelonseeds.missilewars.invs.ArenaSelector;
import com.leomelonseeds.missilewars.invs.CosmeticMenu;
import com.leomelonseeds.missilewars.invs.MapVoting;
import com.leomelonseeds.missilewars.invs.deck.DeckInventory;
import com.leomelonseeds.missilewars.invs.deck.OldPresetSelector;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.cinematic.AbilitiesReplay;
import com.leomelonseeds.missilewars.utilities.cinematic.CinematicManager;
import com.leomelonseeds.missilewars.utilities.cinematic.DefendingReplay;
import com.leomelonseeds.missilewars.utilities.cinematic.PortalReplay;
import com.leomelonseeds.missilewars.utilities.cinematic.RidingReplay;
import com.leomelonseeds.missilewars.utilities.cinematic.TutorialReplay;

public class MissileWarsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Send info if no action taken
        if (args.length == 0) {
            sendErrorMsg(sender, "Usage: /umw <Join/Leave/Spectate/Votemap/Deck/EnqueueRed/EnqueueBlue>");
            return true;
        }

        // Check for arena creation
        String action = args[0];
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        ArenaManager arenaManager = plugin.getArenaManager();
        
        // Reload all config files
        if (action.equalsIgnoreCase("reload")) {
            // Ensure player is allowed to reload
            if (!sender.hasPermission("umw.admin")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }
            
            ConfigUtils.reloadConfigs();
            sendSuccessMsg(sender, "Files reloaded.");
        }
        
        // Test commands
        if (action.equalsIgnoreCase("test")) {
            if (!sender.hasPermission("umw.admin")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }

            Player target = (Player) sender;
            if (args[1].equals("npc")) {
                int replayId = 0;
                if (args.length > 2) {
                    replayId = Integer.parseInt(args[2]);
                }
                
                World tutorialWorld = Bukkit.getWorld("mwarena_tutorial");
                TutorialReplay replay;
                switch (replayId) {
                    case 0:
                        replay = new RidingReplay(tutorialWorld);
                        break;
                    case 1:
                        replay = new PortalReplay(tutorialWorld);
                        break;
                    case 2:
                        replay = new DefendingReplay(tutorialWorld);
                        break;
                    case 3:
                        replay = new AbilitiesReplay(tutorialWorld);
                        break;
                    default:
                        return true;
                }
                
                replay.startReplay();
                return true;
            }
            
            if (args[1].equals("cinematic")) {
                CinematicManager cm = MissileWarsPlugin.getPlugin().getCinematicManager();
                cm.setStartingFrame(0);
                if (args.length > 2) {
                    if (Bukkit.getPlayer(args[2]) != null) {
                        target = Bukkit.getPlayer(args[2]);
                    } else {
                        cm.setStartingFrame(Integer.parseInt(args[2]));
                    }
                }
                cm.play(target);
                return true;
            }
             
            return true;
        }
        
        // Go to lobby
        if (action.equalsIgnoreCase("lobby")) {
            if (!(sender instanceof Player)) {
                return true;
            }
            
            Player player = (Player) sender;
            Arena arena = arenaManager.getArena(player.getUniqueId());
            if (arena == null) {
                player.teleport(ConfigUtils.getSpawnLocation());
            } else {
                arena.removePlayer(player.getUniqueId(), true);
            }
        }
        
        // Skip a tutorial stage
        if (action.equalsIgnoreCase("skip")) {
            if (!(sender instanceof Player)) {
                return true;
            }
            
            Player player = (Player) sender;
            Arena arena = arenaManager.getArena(player.getUniqueId());
            if (arena == null || !(arena instanceof TutorialArena)) {
                sendErrorMsg(player, "This command can only be used in the tutorial arena");
                return true;
            }
            
            ((TutorialArena) arena).registerStageSkip(player);
        }
        
        // Reset coldowns
        if (action.equalsIgnoreCase("resetCooldowns")) {
            Player player = getCommandTarget(args, sender);
            if (player == null) {
                return true;
            }
            
            InventoryUtils.resetCooldowns(player);
        }
        
        // Make changes to map rotation
        if (action.equalsIgnoreCase("rotation")) {
            // Ensure player is allowed
            if (!sender.hasPermission("umw.admin")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }
            
            if (args.length < 3) {
                sendErrorMsg(sender, "Usage: /umw rotation [gamemode] list/add/remove/listall/default");
                return true;
            }
            
            FileConfiguration maps = ConfigUtils.getConfigFile("maps.yml");
            if (!maps.contains(args[1] + ".rotation")) {
                sendErrorMsg(sender, "That gamemode doesn't exist (yet?)");
                return true;
            }
            
            List<String> cur = maps.getStringList(args[1] + ".rotation");
            switch (args[2]) {
            case "add":
            case "remove":
                if (args.length < 4) {
                    sendErrorMsg(sender, "Please specify a map!");
                    return true;
                }
                
                String map = args[3];
                if (map.equals("rotation") || !maps.contains(args[1] + "." + map)) {
                    sendErrorMsg(sender, "The map you specified is invalid");
                    return true;
                }
                
                if (args[2].equals("add")) {
                    if (cur.contains(map)) {
                        sendErrorMsg(sender, "That map is already in the rotation!");
                        return true;
                    }
                    cur.add(map);
                    sendSuccessMsg(sender, "Added " + map + " to the rotation.");
                } else {
                    if (!cur.contains(map)) {
                        sendErrorMsg(sender, "That map is not in rotation!");
                        return true;
                    }
                    cur.remove(map);
                    sendSuccessMsg(sender, "Removed " + map + " from the rotation.");
                }
            case "default":
                // Because add/remove runs this code too, we must verify that it is default
                if (args[2].equals("default")) {
                    cur.clear();
                    cur.add("default-map");
                    cur.add("double-layer");
                }
                
                maps.set(args[1] + ".rotation", cur);
                File file = new File(plugin.getDataFolder().toString(), "maps.yml");
                try {
                    maps.save(file);
                } catch (IOException e) {
                    sendErrorMsg(sender, "Couldn't save the map file for some reason!");
                    e.printStackTrace();
                    return true;
                }
                
                ConfigUtils.reloadConfigs();
            case "list":
                sendSuccessMsg(sender, "Current " + args[1] + " maps: " + String.join(", ", cur));
                return true;
            case "listall":
                List<String> all = new ArrayList<>();
                for (String m : maps.getConfigurationSection(args[1]).getKeys(false)) {
                    if (!m.equals("rotation")) {
                        all.add(m);
                    }
                }
                
                sendSuccessMsg(sender, "All " + args[1] + " maps: " + String.join(", ", all));
                return true;
            }
        }
        
        if (action.equalsIgnoreCase("votemap")) {
            // Ensure user is a player
            if (!(sender instanceof Player)) {
                sendErrorMsg(sender, "You are not a player!");
            }
            Player player = (Player) sender;

            // Ensure player is in an arena that is not running
            Arena playerArena = arenaManager.getArena(player.getUniqueId());
            if (playerArena == null) {
                sendErrorMsg(player, "You are not in an arena!");
                return true;
            }
            
            if (playerArena instanceof TourneyArena) {
                sendErrorMsg(player, "You cannot vote in this arena!");
                return true;
            }
            
            if (playerArena.isRunning() || playerArena.isResetting()) {
                String ret = ConfigUtils.getConfigText("messages.map", player, playerArena, null);
                ret = ret.replaceAll("%map%", ConfigUtils.getMapText(playerArena.getGamemode(), playerArena.getMapName(), "name"));
                player.sendMessage(ConfigUtils.toComponent(ret));
                return true;
            }

            // Open voting menu
            new MapVoting(player, playerArena);

            return true;
        }
        
        if (action.equalsIgnoreCase("spectate")) {
            if (!(sender instanceof Player)) {
                sendErrorMsg(sender, "You must be a player");
                return true;
            }
            Player player = (Player) sender;

            // Try to find Arena
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
        
        // A command used to set player cosmetics
        if (action.equalsIgnoreCase("cosmetic")) {
            Player player = (Player) sender;
            
            if (args.length != 2) {
                sendErrorMsg(sender, "Usage: /umw cosmetic [cosmetic]");
                return true;
            }
            
            // The admin gotta be smart man
            String type = args[1];
            JSONObject json = plugin.getJSON().getPlayer(player.getUniqueId());
            if (!json.has(type)) {
                sendErrorMsg(sender, "Please enter a valid cosmetic type");
                return true; 
            }
            
            new CosmeticMenu(player, type);
            return true; 
        }
        
        // A command specifically used to test passives/abilities
        if (action.equalsIgnoreCase("set")) {
            // Ensure player is allowed to create an arena
            if (!sender.hasPermission("umw.admin")) {
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
        
        // A(nother) command specifically used to test passives/abilities
        if (action.equalsIgnoreCase("view")) {
            // Ensure player is allowed to create an arena
            if (!sender.hasPermission("umw.admin")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (args.length != 2) {
                sendErrorMsg(sender, "Usage: /umw view [gpassive/passive/ability]");
                return true;
            }
            
            // The admin gotta be smart man
            String type = args[1];
            
            JSONObject json = plugin.getJSON().getPlayerPreset(player.getUniqueId());
            if (!json.has(type)) {
                sendErrorMsg(sender, "You suck, use gpassive/passive/ability please");
                return true; 
            }
            
            JSONObject actual = json.getJSONObject(type);
            String name = actual.getString("selected");
            int level = actual.getInt("level");
            
            sendSuccessMsg(sender, "You have " + type + " " + name + " at " + level);
            return true; 
        }
        
        if (action.equalsIgnoreCase("DeleteArena")) {
            // Ensure player is allowed to create an arena
            if (!sender.hasPermission("umw.admin")) {
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
            if (!sender.hasPermission("umw.admin")) {
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
            // Ensure player is allowed
            if (!sender.hasPermission("umw.staff")) {
                sendErrorMsg(sender, "You do not have permission to do that!");
                return true;
            }
            
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

            // Allow player to join the fullest arena, or specify an arena name
            if (args.length == 1) {
                // Join training if nobody online
                if (Bukkit.getOnlinePlayers().size() == 1 && !player.hasPermission("umw.disableautotraining")) {
                    ConfigUtils.sendConfigMessage("messages.nobody-on", player, null, null);
                    arenaManager.getArena("training").joinPlayer(player);
                    return true;
                }
                
                // Otherwise join fullest arena
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
        
        // Queue for a team
        if (action.toLowerCase().contains("enqueue")) {
            String team = action.substring(7);
            if (!(team.equalsIgnoreCase("red") || team.equalsIgnoreCase("blue"))) {
                sendErrorMsg(sender, "Please use enqueuered or enqueueblue!");
                return true;
            }
            
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
                
                if (!(arena.getTeam(player.getUniqueId()) == TeamName.NONE || sender.hasPermission("umw.enqueue"))) {
                    sendErrorMsg(sender, "You are already on a team!");
                    return true;
                }
                
                arena.enqueue(player.getUniqueId(), team);
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

            arena.enqueue(target.getUniqueId(), team, true);
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
            if (!sender.hasPermission("umw.admin")) {
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

            if (!(arena == null || arena.getTeam(player.getUniqueId()) == TeamName.NONE)) {
                sendErrorMsg(sender, "You cannot change decks while playing.");
                return true;
            }

            if (args.length < 2) {
                sendErrorMsg(sender, "Usage: /mw deck Vanguard/Berserker/Sentinel/Architect [Preset]");
                return true;
            }

            String deck = StringUtils.capitalize(args[1].toLowerCase());
            if (DeckStorage.fromString(deck) == null) {
                sendErrorMsg(sender, "Please specify a valid deck!");
                return true;
            }
            
            if (args.length == 3) {
                String preset = args[2].toUpperCase();
                
                // Ensure sender has permission
                if (!sender.hasPermission("umw.admin")) {
                    sendErrorMsg(sender, "You do not have permission to do that!");
                    return true;
                }
                
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
            if (sender.hasPermission("umw.olddeckmenu")) {
                new OldPresetSelector(player, deck);
            } else {
                new DeckInventory(player, deck);
            }
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
        target.sendMessage(ConfigUtils.toComponent("§cError: §7" + msg));
    }

    /**
     * Send the given user a success message.
     *
     * @param target the user
     * @param msg the error message
     */
    protected void sendSuccessMsg(CommandSender target, String msg) {
        target.sendMessage(ConfigUtils.toComponent("§aSuccess! §7" + msg));
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
