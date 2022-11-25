package com.leomelonseeds.missilewars.arenas;

import org.bukkit.entity.Player;

public class MapVote {
    
    private Player player;
    private String map;
    private int vote;
    
    public MapVote(Player player, String map, int vote) {
        this.player = player;
        this.map = map;
        this.vote = vote;
    }

}
