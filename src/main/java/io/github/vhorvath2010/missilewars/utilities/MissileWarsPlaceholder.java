package io.github.vhorvath2010.missilewars.utilities;

import java.text.DecimalFormat;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class MissileWarsPlaceholder extends PlaceholderExpansion {


    @Override
    public String getIdentifier() {
        return "umw";
    }

    @Override
    public String getAuthor() {
        return "Vhbob";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena playerArena = manager.getArena(player.getUniqueId());

        // General purpose placeholders
        if (params.equalsIgnoreCase("focus")) {
            return ConfigUtils.getFocusName(player);
        }
        
        if (params.equalsIgnoreCase("team")) {
            return playerArena == null ? "no team" : ChatColor.stripColor(playerArena.getTeam(player.getUniqueId()));
        } 
        
        if (playerArena == null) {
            return null;
        }
        
        // Arena specific placeholders
        
        if (params.equalsIgnoreCase("arena")) {
            return playerArena.getName();
        }
        
        if (params.equalsIgnoreCase("gamemode")) {
            return ChatColor.GREEN + "Classic";
        }
        
        boolean inGame = playerArena.isRunning() || playerArena.isResetting();
        
        if (params.equalsIgnoreCase("ingame")) {
            return inGame ? "true" : "false";
        }
        
        if (params.equalsIgnoreCase("red_queue")) {
            return Integer.toString(playerArena.getRedQueue());
        }
        
        if (params.equalsIgnoreCase("blue_queue")) {
            return Integer.toString(playerArena.getBlueQueue());
        }
        
        if (!inGame) {
            return null;
        }

        // In-Game placeholders
        DecimalFormat df = new DecimalFormat("##.##");
        
        if (params.equalsIgnoreCase("map")) {
            return ConfigUtils.getMapText(playerArena.getMapType(), playerArena.getMapName(), "name");
        }
        
        if (params.equalsIgnoreCase("time_remaining")) {
            return playerArena.getTimeRemaining();
        }
        
        if (params.equalsIgnoreCase("red_shield_health")) {
            double health = Math.max(0, playerArena.getRedShieldHealth());
            ChatColor chatcolor;
            if (health >= 90) {
                chatcolor = ChatColor.DARK_GREEN;
            } else if (health >= 80) {
                chatcolor = ChatColor.GREEN;
            } else if (health >= 70) {
                chatcolor = ChatColor.YELLOW; 
            } else if (health >= 60) {
                chatcolor = ChatColor.GOLD;
            } else if (health >= 50) {
                chatcolor = ChatColor.RED;
            } else {
                chatcolor = ChatColor.DARK_RED;
            }
            return chatcolor + df.format(health)+ "%";
        }

        if (params.equalsIgnoreCase("blue_shield_health")) {
            double health = Math.max(0, playerArena.getBlueShieldHealth());
            ChatColor chatcolor;
            if (health >= 90) {
                chatcolor = ChatColor.DARK_GREEN;
            } else if (health >= 80) {
                chatcolor = ChatColor.GREEN;
            } else if (health >= 70) {
                chatcolor = ChatColor.YELLOW; 
            } else if (health >= 60) {
                chatcolor = ChatColor.GOLD;
            } else if (health >= 50) {
                chatcolor = ChatColor.RED;
            } else {
                chatcolor = ChatColor.DARK_RED;
            }
            return chatcolor + df.format(health)+ "%";
        }
        
        if (params.equalsIgnoreCase("red_team")) {
            return Integer.toString(playerArena.getRedTeam());
        }
        
        if (params.equalsIgnoreCase("blue_team")) {
            return Integer.toString(playerArena.getBlueTeam());
        }
        
        if (params.equalsIgnoreCase("red_portals")) {
            String firstPortal = playerArena.getRedFirstPortalStatus() ? ChatColor.WHITE + "⬛" : ChatColor.DARK_PURPLE + "⬛";
            String secondPortal = playerArena.getRedSecondPortalStatus() ? ChatColor.WHITE + "⬛" : ChatColor.DARK_PURPLE + "⬛";
            if (playerArena.getTeam(player.getUniqueId()).equalsIgnoreCase(ChatColor.RED + "red" + ChatColor.RESET)) {
                return firstPortal + secondPortal;
            }
            return secondPortal + firstPortal;
        }
        
        if (params.equalsIgnoreCase("blue_portals")) {
            String firstPortal = playerArena.getBlueFirstPortalStatus() ? ChatColor.WHITE + "⬛" : ChatColor.DARK_PURPLE + "⬛";
            String secondPortal = playerArena.getBlueSecondPortalStatus() ? ChatColor.WHITE + "⬛" : ChatColor.DARK_PURPLE + "⬛";
            if (playerArena.getTeam(player.getUniqueId()).equalsIgnoreCase(ChatColor.BLUE + "blue" + ChatColor.RESET)) {
                return secondPortal + firstPortal;
            }
            return firstPortal + secondPortal;
        }

        return null; // Placeholder is unknown by the Expansion
    }

}
