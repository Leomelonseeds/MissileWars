package com.leomelonseeds.missilewars.utilities;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public class VanillaTeamManager {
    
    private Map<Color, Team> teams;
    
    /**
     * Simple class for setting up entity glow
     */
    public VanillaTeamManager() {
        this.teams = new HashMap<>();
    }
    
    public String getTeamWithColor(Color color) {
        String name = "color-" + Integer.toHexString(color.asRGB());
        if (teams.containsKey(color)) {
            return name;
        }
        
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam(name);
        team.color(NamedTextColor.nearestTo(TextColor.color(color.asRGB())));
        teams.put(color, team);
        return name;
    }
    
    public void deleteTeams() {
        teams.values().forEach(t -> t.unregister());
    }
}
