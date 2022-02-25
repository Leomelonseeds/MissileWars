package io.github.vhorvath2010.missilewars.teams;

import java.util.UUID;

/** Represents a Missile Wars Player. */
public class MissileWarsPlayer {

    /** The UUID of the Spigot player this player represents. */
    private UUID playerId;

    /**
     * Obtain the UUID of the Minecraft player associated with the MissileWarsPlayer.
     *
     * @return the UUID of the Minecraft player associated with the MissileWarsPlayer
     */
    public UUID getMCPlayerId() {
        return playerId;
    }
}
