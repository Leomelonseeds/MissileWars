package io.github.vhorvath2010.missilewars.teams;

import io.github.vhorvath2010.missilewars.decks.Deck;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/** Represents a Missile Wars Player. */
public class MissileWarsPlayer {

    /** The UUID of the Spigot player this player represents. */
    private UUID playerId;
    /** The current deck the player has selected. */
    private Deck deck;
    /** The number of kills the player has. */
    private int kills;
    /** The number of deaths the player has. */
    private int deaths;

    /**
     * Create a MissileWarsPlayer from a Minecraft player.
     *
     * @player the Minecraft player
     */
    public MissileWarsPlayer(UUID playerID) {
        this.playerId = playerID;
    }

    /**
     * Set the user's current {@link Deck}.
     *
     * @param deck the deck to let this MissileWarsPlayer use
     */
    public void setDeck(Deck deck) {
        this.deck = deck;
    }

    /**
     * Get the user's currently selected {@link Deck}.
     *
     * @return the user's currently selected {@link Deck}
     */
    public Deck getDeck() {
        return deck;
    }

    /**
     * Obtain the UUID of the Minecraft player associated with the MissileWarsPlayer.
     *
     * @return the UUID of the Minecraft player associated with the MissileWarsPlayer
     */
    public UUID getMCPlayerId() {
        return playerId;
    }

    /**
     * Return the MC player this MissileWarsPlayer represents.
     *
     * @return the MC player this MissileWarsPlayer represents
     */
    public Player getMCPlayer() {
        return Bukkit.getPlayer(playerId);
    }

    /**
     * Get the number of kills this {@link MissileWarsPlayer} has.
     *
     * @return the number of kills this {@link MissileWarsPlayer} has
     */
    public int getKills() {
        return kills;
    }

    /** Increment the kill count for this {@link MissileWarsPlayer}. */
    public void incrementKills() {
        kills++;
    }

    /**
     * Get the number of deaths this {@link MissileWarsPlayer} has.
     *
     * @return the number of deaths this {@link MissileWarsPlayer} has
     */
    public int getDeaths() {
        return deaths;
    }

    /** Increment the kill count for this {@link MissileWarsPlayer}. */
    public void incrementDeaths() {
        deaths++;
    }

    /**
     * Checks to see if this MissileWarsPlayer is equal to another Object.
     *
     * @param o the object
     * @return true if o is a MissileWarsPlayer with the same playerId
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MissileWarsPlayer player = (MissileWarsPlayer) o;
        return Objects.equals(playerId, player.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }

    /** Give the MC player an item from their Deck. */
    public void givePoolItem() {
        if (deck != null && getMCPlayer() != null) {
            deck.givePoolItem(getMCPlayer());
        }
    }

    /** Give the MC player their Deck gear. */
    public void giveDeckGear() {
        if (deck != null && getMCPlayer() != null) {
            deck.giveGear(getMCPlayer());
        }
    }

}
