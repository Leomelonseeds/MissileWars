package com.leomelonseeds.missilewars.utilities.tracker;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;

public abstract class Tracked {
    
    public static final int maxExplosions = 1;
    
    protected Player player;
    protected Location pos1;
    protected Location pos2;
    protected BlockFace direction;
    private Tracker tracker;
    private int explosions;
    
    public Tracked(Player player, Location pos1, Location pos2, BlockFace direction) {
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.direction = direction;
        this.explosions = 0;
        
        ArenaManager arenaManager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = arenaManager.getArena(player.getUniqueId());
        tracker = arena.getTracker();
        tracker.add(this);
        
        // Tracker removal task
        Bukkit.getScheduler().runTaskTimerAsynchronously(MissileWarsPlugin.getPlugin(), () -> {
            if (testForRemoval()) {
                remove();
            }
        }, 100, 100);
    }
    
    /**
     * Remove if more than 2 TNT explosions have been registered here
     */
    public void registerExplosion() {
        explosions++;
        MissileWarsPlugin.getPlugin().log("An explosion was logged for a tracked object spawned by " + player.getName());
        if (explosions > maxExplosions) {
            MissileWarsPlugin.getPlugin().log("A tracked object spawned by player " + player.getName() + " tested positive for removal by explosion.");
            remove();
        }
    }
    
    /**
     * Checks if the given location is contained within the tracked object
     * 
     * @param l
     * @return
     */
    public boolean contains(Location l) {
        double x = l.getX();
        double y = l.getY();
        double z = l.getZ();
        
        double x1 = Math.min(pos1.getX(), pos2.getX());
        double x2 = Math.max(pos1.getX(), pos2.getX());
        double y1 = Math.min(pos1.getY(), pos2.getY());
        double y2 = Math.max(pos1.getY(), pos2.getY());
        double z1 = Math.min(pos1.getZ(), pos2.getZ());
        double z2 = Math.max(pos1.getZ(), pos2.getZ());
        
        if (x1 <= x && x <= x2 && y1 <= y && y <= y2 && z1 <= z && z <= z2) {
            return true;
        }
        return false;
    }
    
    /**
     * Checks if the given missile/utility is contained within this one
     * 
     * @param t
     * @return the amount of blocks that overlap between the tracked objects
     */
    public boolean contains(Tracked t) {
        Location tpos1 = t.getPos1();
        Location tpos2 = t.getPos2();
        World world = tpos1.getWorld();

        int tx1 = Math.min(tpos1.getBlockX(), tpos2.getBlockX());
        int tx2 = Math.max(tpos1.getBlockX(), tpos2.getBlockX());
        int ty1 = Math.min(tpos1.getBlockY(), tpos2.getBlockY());
        int ty2 = Math.max(tpos1.getBlockY(), tpos2.getBlockY());
        int tz1 = Math.min(tpos1.getBlockZ(), tpos2.getBlockZ());
        int tz2 = Math.max(tpos1.getBlockZ(), tpos2.getBlockZ());
        
        for (int x = tx1; x <= tx2; x++) {
            for (int y = ty1; y <= ty2; y++) {
                for (int z = tz1; z <= tz2; z++) {
                    if (contains(new Location(world, x, y, z))) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    public abstract boolean testForRemoval();
    
    public void remove() {
        tracker.remove(this);
    }
    
    public Player getPlayer() {
        return player;
    }
    
    public Location getPos1() {
        return pos1;
    }
    
    public Location getPos2() {
        return pos2;
    }
    
    public BlockFace getDirection() {
        return direction;
    }
}
