package com.leomelonseeds.missilewars.arenas;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

public class TutorialReplay {

    private Location viewLocation;
    private Vector eyeDirection;
    private BukkitTask testTask;
    private int duration; // ticks
    private int cur;
    
    /**
     * Initializes a tutorial replay.
     * 
     * @param viewLocation
     */
    public TutorialReplay(Location viewLocation, Vector eyeDirection) {
        this.viewLocation = viewLocation;
        this.eyeDirection = eyeDirection;
        this.duration = 20 * 5;
        this.cur = 0;
    }
    
    public void testNPC() {
        // Spawn NPC
        NPC testNPC = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "REPLAYTEST");
        testNPC.data().setPersistent(NPC.Metadata.DEFAULT_PROTECTED, false);
        testNPC.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
        testNPC.spawn(viewLocation);
        Entity npcEntity = testNPC.getEntity();
        Vector xyVelocity = eyeDirection.normalize().multiply(0.28);
        
        // Set velocity forward
        testTask = Bukkit.getScheduler().runTaskTimer(MissileWarsPlugin.getPlugin(), () -> {
            Vector curVelocity = npcEntity.getVelocity();
            Vector velocity = new Vector(xyVelocity.getX(), curVelocity.getY(), xyVelocity.getZ());
            npcEntity.setVelocity(velocity);
            if (cur > duration) {
                testTask.cancel();
                testNPC.destroy();
            }
            cur++;
        }, 20, 1);
        
        // Test jumping
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            npcEntity.setVelocity(npcEntity.getVelocity().add(new Vector(0, 0.42, 0)));
        }, 40);
    }
    
    // TODO: One method each for each replay, will be controlled by Citizens NPCs
    // TODO: This class can be a parent (maybe abstract), and abstract method will be implemented by subclasses for each replay
    

}
