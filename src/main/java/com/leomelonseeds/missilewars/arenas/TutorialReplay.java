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
    // Replay 1:
    // - Spawning and riding missiles
    // - View location: 161.5 17 -50.5 y=(-125 to -25) p=(0 to 20)
    // Replay 2:
    // - Blowing up portals
    // - View location 1: 173.5 14 49.5 y=(130 to 30) p=(9 to 25) 
    // - Can stop at yaw 70 to view missile placement
    // - After the missile placement view, smoothly transition to 173 6 86 y165 p6 to see portal being broken
    // Replay 3:
    // - Using utility items
    // - NPC should be left handed
    // - View location: 136.5 17 -52.5 y12 p0 
    // - After egg throw, to 144 18 -13 y140 p6
    

}
