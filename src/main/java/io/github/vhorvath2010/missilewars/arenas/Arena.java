package io.github.vhorvath2010.missilewars.arenas;

import io.github.vhorvath2010.missilewars.teams.MissileWarsPlayer;
import io.github.vhorvath2010.missilewars.teams.MissileWarsTeam;

import java.util.List;
import java.util.Queue;

/** Represents a MissileWarsArena where the game will be played. */
public class Arena {

    /** The arena name. */
    private String name;
    /** The max number of players for this arena. */
    private int capacity;
    /** The current selected map for the arena. */
    private GameMap map;
    /** The list of all players currently in the arena. */
    private List<MissileWarsPlayer> players;
    /** The list of all spectators currently in the arena. */
    private List<MissileWarsPlayer> spectators;
    /** The queue to join the red team. Active before the game. */
    private Queue<MissileWarsPlayer> redQueue;
    /** The queue to join the blue team. Active before the game. */
    private Queue<MissileWarsPlayer> blueQueue;
    /** The red team. */
    private MissileWarsTeam redTeam;
    /** The blue team. */
    private MissileWarsTeam blueTeam;

    /**
     * Create a new Arena with a given name and max capacity.
     *
     * @param name the name
     * @param capacity the max capacity
     */
    public Arena(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
    }

    /**
     * Get the name of the Arena.
     *
     * @return the name of the Arena
     */
    public String getName() {
        return name;
    }
}
