package com.leomelonseeds.missilewars.arenas.tracker;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;

public abstract class Tracked {
    
    protected List<BukkitTask> removalTasks;
    protected Player player;
    protected Location pos1;
    protected Location pos2;
    protected BlockFace direction;
    protected Tracker tracker;
    private boolean isRed;
    
    public Tracked(Player player, Location pos1, Location pos2, BlockFace direction, boolean isRed) {
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.direction = direction;
        this.player = player;
        this.isRed = isRed;
        this.removalTasks = new ArrayList<>();
        
        ArenaManager arenaManager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = arenaManager.getArena(pos1.getWorld());
        tracker = arena.getTracker();
        tracker.add(this);
        
        // Tracker removal task
        removalTasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                // Extra check just to make sure
                if (!arena.isRunning() || !MissileWarsPlugin.getPlugin().isEnabled()) {
                    this.cancel();
                }
                
                if (testForRemoval()) {
                    remove();
                }
            }
        }.runTaskTimerAsynchronously(MissileWarsPlugin.getPlugin(), 100, 100));
    }
    
    /**
     * Cancels the tracker removal task
     */
    public void cancelTasks() {
        for (BukkitTask task : removalTasks) {
            task.cancel();
        }
    }
    
    /**
     * Checks if the given location is contained within the tracked object
     * 
     * @param l
     * @return
     */
    public boolean contains(Location l) {
        return contains(l, 0);
    }

    
    /**
     * Checks if the given location is contained within the tracked object
     * 
     * @param l
     * @param bias the bias to apply
     * @return
     */
    public boolean contains(Location l, double bias) {
        double x = l.getX();
        double y = l.getY();
        double z = l.getZ();
        
        double x1 = Math.min(pos1.getX(), pos2.getX()) - bias;
        double x2 = Math.max(pos1.getX(), pos2.getX()) + bias;
        double y1 = Math.min(pos1.getY(), pos2.getY()) - bias;
        double y2 = Math.max(pos1.getY(), pos2.getY()) + bias;
        double z1 = Math.min(pos1.getZ(), pos2.getZ()) - bias;
        double z2 = Math.max(pos1.getZ(), pos2.getZ()) + bias;
        
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
        cancelTasks();
    }
    
    public boolean isRed() {
        return isRed;
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
