package com.leomelonseeds.missilewars.utilities.cinematic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.db.DBCallback;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

public class PortalReplay extends TutorialReplay {

    public PortalReplay(World tutorialWorld) {
        super(tutorialWorld);
    }

    @Override
    protected int play() {
        Location missileLoc = new Location(tutorialWorld, 165, 15, 51);
        spawnMissile("gemini-1", new Location(tutorialWorld, 166, 16, 32));
        setHandItem(new ItemStack(Material.BEE_SPAWN_EGG));
        bot.spawn(new Location(tutorialWorld, 165.5, 14, 40.5, 0f, 3f));
        refreshEntity();
        
        Vector dir = new Vector(0, 0, 1);
        setCrouched(true);
        move(dir, 39, CROUCH_SPEED);
        ConfigUtils.schedule(40, () -> {
            setCrouched(false);
            run(dir, 30);
        });
        ConfigUtils.schedule(43, () -> jump());
        ConfigUtils.schedule(60, () -> botPlayer.swingMainHand());
        ConfigUtils.schedule(63, () -> spawnMissile("supporter-2", missileLoc));
        ConfigUtils.schedule(67, () -> setHandItem(new ItemStack(Material.GUARDIAN_SPAWN_EGG)));
        ConfigUtils.schedule(70, () -> botPlayer.swingMainHand());
        ConfigUtils.schedule(73, () -> spawnMissile("guardian-1", missileLoc));
        ConfigUtils.schedule(150, () -> bot.despawn());
        return 270;
    }

    @Override
    protected void reset(DBCallback onFinish) {
        SchematicManager.spawnFAWESchematic("tutorial-replay", tutorialWorld, null, o -> {
            SchematicManager.setAirAsync(168, 13, 50, 165, 12, 28, tutorialWorld, o1 -> onFinish.onQueryDone(null));
        });
    }

}
