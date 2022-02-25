package io.github.vhorvath2010.missilewars.events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/** Class to listen for events relating to Arena game rules. */
public class ArenaGameruleEvents implements Listener {

    /** Events to handle creature spawns. */
    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        // Cancel natural spawns in arena worlds
        if (event.getLocation().getWorld() != null && event.getLocation().getWorld().getName().contains("mwarena_")) {
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
                event.setCancelled(true);
            }
        }
    }

    /** Event to ignore hunger. */
    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity().getWorld().getName().contains("mwarena_")) {
            event.setCancelled(true);
        }
    }

}
