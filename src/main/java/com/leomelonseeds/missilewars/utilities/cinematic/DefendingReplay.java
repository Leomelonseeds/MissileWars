package com.leomelonseeds.missilewars.utilities.cinematic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Egg;
import org.bukkit.inventory.ItemStack;

import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.db.DBCallback;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

public class DefendingReplay extends TutorialReplay {

    public DefendingReplay(World tutorialWorld) {
        super(tutorialWorld);
    }

    @Override
    protected int play() {
        setHandItem(new ItemStack(Material.EGG));
        spawnMissile("guardian-1", new Location(tutorialWorld, 130, 16, 7));
        bot.spawn(new Location(tutorialWorld, 130.5, 17, 52.5, 180f, 3f));
        refreshEntity();

        ConfigUtils.schedule(30, () -> setCrouched(true));
        ConfigUtils.schedule(35, () -> {
            botPlayer.swingMainHand();
            ConfigUtils.sendConfigSound("throw-projectile", botPlayer.getLocation());
            Egg torp = botPlayer.launchProjectile(Egg.class);
            
            // Egg smoke particles cause why not
            ArenaUtils.doUntilDead(torp, () -> 
                botPlayer.getWorld().spawnParticle(Particle.SMOKE, torp.getLocation(), 1, 0, 0, 0, 0));
            
            ConfigUtils.schedule(10, () -> setCrouched(false));
            ConfigUtils.schedule(20, () -> {
                Location loc = torp.getLocation();
                torp.remove();
                SchematicManager.spawnNBTStructure(null, "torpedo-2", loc, true, false, false);
                ConfigUtils.sendConfigSound("spawn-torpedo", loc);
            });
        });
        return 240;
    }

    @Override
    protected void reset(DBCallback onFinish) {
        SchematicManager.setAirAsync(132, 3, 28, 129, 14, 7, tutorialWorld, o -> onFinish.onQueryDone(null));
    }

}
