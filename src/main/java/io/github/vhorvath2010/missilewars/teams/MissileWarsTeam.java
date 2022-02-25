package io.github.vhorvath2010.missilewars.teams;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Represents a team of Missile Wars Players. */
public class MissileWarsTeam {

    /** The members of the team. */
    private List<MissileWarsPlayer> members;

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

}
