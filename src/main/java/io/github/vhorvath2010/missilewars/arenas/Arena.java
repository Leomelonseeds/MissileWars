package io.github.vhorvath2010.missilewars.arenas;

import io.github.vhorvath2010.missilewars.teams.MissileWarsPlayer;
import io.github.vhorvath2010.missilewars.teams.MissileWarsTeam;

import java.util.List;
import java.util.Queue;

/** Represents a MissileWarsArena where the game will be played. */
public class Arena {

    /** The arena ID number. */
    private int id;
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

}
