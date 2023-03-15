package com.leomelonseeds.missilewars.arenas;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class VoteManager {
    
    private SortedMap<String, Integer> allVotes;
    private Set<VotePlayer> playerVotes;
    
    public VoteManager(Arena arena) {
        this.allVotes = new TreeMap<>();
        this.playerVotes = new HashSet<>();
        
        // Add all arena maps to the list
        FileConfiguration mapConfig = ConfigUtils.getConfigFile("maps.yml");
        for (String m : mapConfig.getStringList(arena.getGamemode() + ".rotation")) {
            allVotes.put(m, 0);
        }
        
        // Troll for training arena
        if (arena instanceof TrainingArena) {
            allVotes.put("default-map", 64);
        }
    }
    
    // Get voter
    private VotePlayer getVoter(Player player) {
        VotePlayer vPlayer = null;
        for (VotePlayer v : playerVotes) {
            if (v.getPlayer().equals(player)) {
                vPlayer = v;
                break;
            }
        }
        return vPlayer;
    }
    
    // Remove a certain vote
    private void removeVote(Vote vote) {
        String map = vote.getMap();
        int amount = vote.getAmount();
        allVotes.put(map, allVotes.get(map) - amount);
    }
    
    /**
     * Remove a player from the votes
     */
    public void removePlayer(Player player) {
        VotePlayer vPlayer = getVoter(player);
        if (vPlayer == null) {
            return;
        }
        
        playerVotes.remove(vPlayer);
        for (Vote vote : vPlayer.getVotes()) {
            removeVote(vote);
        }
    }

    /**
     * Register a vote to a map for a player in this arena
     * 
     * @param player
     * @param map
     * @param reverse
     */
    public void registerVote(Player player, String map, boolean reverse) {
        VotePlayer vPlayer = getVoter(player);
        int voteAmount = reverse ? -1 : 1;
        
        if (vPlayer == null) {
            vPlayer = new VotePlayer(player);
            playerVotes.add(vPlayer);
        }
        
        // Remove previous vote if exists
        Vote previous = vPlayer.addVote(map, reverse);
        if (previous != null) {
            removeVote(previous);
            if (previous.getAmount() < 0 ^ reverse) {
                 voteAmount = 0;
            }
        }
        
        // Update votes
        allVotes.put(map, allVotes.get(map) + voteAmount);   
    }
    
    /**
     * Gets the most voted for map, or random if tie
     * 
     * @return
     */
    public String getVotedMap() {
        String mapName = "default-map";
        List<String> mapsWithTopVotes = new LinkedList<>();
        for (String map : allVotes.keySet()) {
            if (mapsWithTopVotes.isEmpty()) {
                mapsWithTopVotes.add(map);
            } else {
                String previousTop = mapsWithTopVotes.get(0);
                if (allVotes.get(previousTop) == allVotes.get(map)) {
                    mapsWithTopVotes.add(map);
                } else if (allVotes.get(previousTop) < allVotes.get(map)) {
                    mapsWithTopVotes.clear();
                    mapsWithTopVotes.add(map);
                }
            }
        }
        if (!mapsWithTopVotes.isEmpty()) {
            // Set map to random map with top votes
            mapName = mapsWithTopVotes.get(new Random().nextInt(mapsWithTopVotes.size()));
        }
        return mapName;
    }
    
    public SortedMap<String, Integer> getVotes() {
        return allVotes;
    }
}
