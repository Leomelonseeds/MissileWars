package io.github.vhorvath2010.missilewars.utilities;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import java.text.DecimalFormat;

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

        if(params.equalsIgnoreCase("team")) {
            return playerArena == null ? "no team" : ChatColor.stripColor(playerArena.getTeam(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("focus")) {
            return ConfigUtils.getFocusName(player);
        }

        // Decimal formatter for percentages
        DecimalFormat df = new DecimalFormat("##.##");

        if (params.equalsIgnoreCase("shield_health_red")) {
            return df.format(playerArena.getRedShieldHealth())+ "%";
        }

        if (params.equalsIgnoreCase("shield_health_blue")) {
            return df.format(playerArena.getBlueShieldHealth()) + "%";
        }

        return null; // Placeholder is unknown by the Expansion
    }

}
