package io.github.vhorvath2010.missilewars.teams;

import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Represents a team of Missile Wars Players. */
public class MissileWarsTeam {

    /** The name of the team */
    private String name;
    /** The arena this team is linked to. */
    private Arena arena;
    /** The members of the team. */
    private Set<MissileWarsPlayer> members;
    /** The spawn location for the team. */
    private Location spawn;

    /**
     * Create a {@link MissileWarsTeam} with a given name
     *
     * @param name the name of the team
     * @param spawn the spawn for the team
     * @param arena the arena the team is linked to
     */
    public MissileWarsTeam(String name, Arena arena, Location spawn) {
        this.name = name;
        this.members = new HashSet<>();
        this.spawn = spawn;
        this.arena = arena;
    }

    /**
     * Get the size of the team.
     *
     * @return the number of players on the team.
     */
    public int getSize() {
        return members.size();
    }

    /**
     * Check if a team contains a specific player based on their MC UUID.
     *
     * @param uuid the uuid of player to check for
     * @return true if the player is on this team
     */
    public boolean containsPlayer(UUID uuid) {
        for (MissileWarsPlayer player : members) {
            if (player.getMCPlayerId().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a player to the team.
     *
     * @param player the player to add
     */
    public void addPlayer(MissileWarsPlayer player) {
        broadcastConfigMsg("messages.queue-join-others", player);
        members.add(player);
        ConfigUtils.sendConfigMessage("messages.queue-join", player.getMCPlayer(), arena, null);
        player.getMCPlayer().teleport(spawn);
    }

    /**
     * Broadcast a config message to all members of the team.
     *
     * @param path the path to the message in the message.yml file
     * @param focus the key player that is the focus of the message
     */
    public void broadcastConfigMsg(String path, MissileWarsPlayer focus) {
        for (MissileWarsPlayer player : members) {
            ConfigUtils.sendConfigMessage(path, player.getMCPlayer(), arena, focus.getMCPlayer());
        }
    }

}
