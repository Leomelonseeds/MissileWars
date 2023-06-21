package com.leomelonseeds.missilewars.arenas.votes;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

public class VotePlayer {
    
    private Player player;
    List<Vote> votes;
    
    public VotePlayer(Player player) {
        this.player = player;
        votes = new ArrayList<>();
    }
    
    public Player getPlayer() {
        return player;
    }
    
    /**
     * Adds a vote, returning a vote object if one was removed
     * from the list
     * 
     * @param map
     * @param negative
     * @return Vote or null if no previous vote was removed
     */
    public Vote addVote(String map, boolean negative) {
        int maxVotes = player.hasPermission("umw.extravote") ? 2 : 1;
        int voteAmount = negative ? -1 : 1;
        
        // Check for a vote that resets a previous vote
        for (Vote vote : new ArrayList<>(votes)) {
            if (vote.getAmount() * -1 == voteAmount) {
                votes.remove(vote);
                return vote;
            }
        }

        
        votes.add(new Vote(map, voteAmount));
        if (votes.size() > maxVotes) {
            return votes.remove(0);
        }
        
        return null;
    }
    
    /**
     * Get all votes for this player
     * 
     * @return
     */
    public List<Vote> getVotes() {
        return votes;
    }
}
