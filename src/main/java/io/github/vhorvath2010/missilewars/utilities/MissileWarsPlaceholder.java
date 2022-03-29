package io.github.vhorvath2010.missilewars.utilities;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

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

        if(params.equalsIgnoreCase("team")) {
            Arena playerArena = manager.getArena(player.getUniqueId());
            return playerArena == null ? "no team" : playerArena.getTeam(player.getUniqueId());
        }

        if (params.equalsIgnoreCase("focus")) {
            Arena playerArena = manager.getArena(player.getUniqueId());
            if (playerArena != null) {
                ChatColor teamColor = playerArena.getTeamColor(player.getUniqueId());
                if (teamColor != null) {
                    return teamColor + ChatColor.stripColor(player.getPlayer().getDisplayName());
                }
            }
            return player.getPlayer().getDisplayName();
        }

        return null; // Placeholder is unknown by the Expansion
    }

}
