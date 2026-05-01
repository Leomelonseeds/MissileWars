package com.leomelonseeds.missilewars.arenas.votes;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class VoteManager {
    
    private SortedMap<String, Integer> allVotes;
    private Set<VotePlayer> playerVotes;
    private Set<String> selectedMaps; // The same set as in arena settings!
    private String gamemode;
    private boolean respectRotation;
    
    public VoteManager(String gamemode, Set<String> selectedMaps, boolean respectRotation, boolean treatEmptyListAsFull) {
        this.allVotes = new TreeMap<>();
        this.playerVotes = new HashSet<>();
        this.gamemode = gamemode;
        this.respectRotation = respectRotation;
        
        // Only add a map if it's selected in the settings
        for (String m : getVoteableMaps(respectRotation)) {
            if ((treatEmptyListAsFull && selectedMaps.isEmpty()) || selectedMaps.contains(m)) {
                allVotes.put(m, 0);
            }
        }
    }
    
    /**
     * Get a list of maps available for voting, which doesn't take into account
     * maps available from arena settings.
     * 
     * @param respectRotation can be used to override this votemanagers setting
     * @return
     */
    public Collection<String> getVoteableMaps(boolean respectRotation) {
        FileConfiguration mapConfig = ConfigUtils.getConfigFile("maps.yml");
        if (respectRotation) {
            return mapConfig.getStringList(gamemode + ".rotation");
        }
        
        Set<String> allMaps = mapConfig.getConfigurationSection(gamemode).getKeys(false);
        allMaps.remove("rotation");
        return allMaps;
    }
    
    /**
     * Add a map to the rotation for this arena. If no
     * maps are specifically selected in the arena settings,
     * and the map rotation isn't respected (i.e. the arena
     * is custom), then this will become the only map available
     * for voting. Otherwise, this map will be added to the
     * selected maps for voting.
     * 
     * @param map
     */
    public void addMap(String map) {
        int amt = 0;
        if (selectedMaps.isEmpty() && !respectRotation) {
            amt = allVotes.getOrDefault(map, 0);
            allVotes.clear();
        }
        
        if (selectedMaps.add(map)) {
            allVotes.putIfAbsent(map, amt);
        }
    }
    
    /**
     * Remove a map from the rotation for this arena.
     * If there are no more maps, the rotation will be
     * reset to the default one.
     * 
     * @param map
     */
    public void removeMap(String map) {
        selectedMaps.remove(map);
        allVotes.remove(map);
    }
    
    /**
     * Resets the available map list back to the default one determined
     * by respectRotation, while retaining previous votes. This operation
     * also refreshes the map list
     * 
     * @param map
     */
    public void resetAvailableMaps() {
        Set<String> allMaps = new HashSet<>(getVoteableMaps(respectRotation));
        Set<String> currentMaps = allVotes.keySet();
        
        // Remove all maps from current map selection that are not contained within the default selection
        Set<String> toRemove = new HashSet<>(currentMaps);
        toRemove.removeAll(allMaps);
        currentMaps.removeAll(toRemove);
        
        // Add all maps from voteable maps that are not contained within the current selection
        for (String map : allMaps) {
            if (!currentMaps.contains(map)) {
                allVotes.put(map, 0);
            }
        }
    }
    
    /**
     * Removes all votes from all maps
     */
    public void resetVotes() {
        allVotes.replaceAll((s, i) -> i = 0);
        playerVotes.clear();
    }
     
    /**
     * Manually adds admin votes to a specific map,
     * if that map exists.
     * 
     * @param map
     * @param votes
     */
    public void addVote(String map, int votes) {
        Integer cur = allVotes.get(map);
        if (cur == null) {
            return;
        }
        
        allVotes.put(map, cur + votes);
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
     * @param filter add an additional filter
     * @return
     */
    public String getVotedMap(Predicate<String> filter) {
        String mapName = "default-map";
        List<String> mapsWithTopVotes = new LinkedList<>();
        for (String map : allVotes.keySet()) {
            if (!filter.test(map)) {
                continue;
            }
            
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
        
        // Set map to random map with top votes
        if (!mapsWithTopVotes.isEmpty()) {
            mapName = mapsWithTopVotes.get(new Random().nextInt(mapsWithTopVotes.size()));
        }
        
        return mapName;
    }
    
    /**
     * @return an unmodifiable view of the current votes
     */
    public SortedMap<String, Integer> getVotes() {
        return Collections.unmodifiableSortedMap(allVotes);
    }
}
