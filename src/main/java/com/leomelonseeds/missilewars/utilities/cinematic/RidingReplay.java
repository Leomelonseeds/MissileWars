package com.leomelonseeds.missilewars.utilities.cinematic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.db.DBCallback;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

public class RidingReplay extends TutorialReplay {

    public RidingReplay(World tutorialWorld) {
        super(tutorialWorld);
    }

    @Override
    protected int play() {
        setHandItem(new ItemStack(Material.SLIME_SPAWN_EGG));
        bot.spawn(new Location(tutorialWorld, 166.5, 17, -64.5, 0f, 25f));
        refreshEntity();
        
        Vector direction = new Vector(0, 0, 1);
        run(direction, 69);
        ConfigUtils.schedule(50, () -> jump());
        ConfigUtils.schedule(36, () -> botPlayer.swingMainHand());
        ConfigUtils.schedule(38, () -> spawnMissile("gemini-1", new Location(tutorialWorld, 166, 16, -53)));
        ConfigUtils.schedule(70, () -> {
           setCrouched(true);
           move(direction, 9, CROUCH_SPEED);
        });
        ConfigUtils.schedule(80, () -> {
            setCrouched(false);
            run(direction, 12);
        });
        ConfigUtils.schedule(83, () -> jump());
        ConfigUtils.schedule(98, () -> {
            setCrouched(true);
            move(direction, 22, CROUCH_SPEED);
        });
        ConfigUtils.schedule(90, () -> setHandItem(new ItemStack(Material.BOW)));
        ConfigUtils.schedule(95, () -> botPlayer.swingMainHand());
        return 120;
    }

    @Override
    protected void reset(DBCallback onFinish) {
        SchematicManager.setAirAsync(165, 12, -49, 167, 13, -24, tutorialWorld, o -> onFinish.onQueryDone(null));
    }

}
