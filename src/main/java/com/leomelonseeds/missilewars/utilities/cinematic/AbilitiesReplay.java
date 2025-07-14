package com.leomelonseeds.missilewars.utilities.cinematic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.db.DBCallback;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import net.citizensnpcs.trait.EntityPoseTrait;
import net.citizensnpcs.trait.EntityPoseTrait.EntityPose;

public class AbilitiesReplay extends TutorialReplay {
    
    private NPC victim;
    private Entity victimPlayer;

    public AbilitiesReplay(World tutorialWorld) {
        super(tutorialWorld);
    }

    @Override
    protected int play() {
        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        CrossbowMeta meta = (CrossbowMeta) crossbow.getItemMeta();
        meta.addChargedProjectile(new ItemStack(Material.FIREWORK_ROCKET));
        crossbow.setItemMeta(meta);

        // First bot with loaded crossbow
        Equipment equip = bot.getOrAddTrait(Equipment.class);
        equip.set(EquipmentSlot.BOOTS, new ItemStack(Material.DIAMOND_BOOTS));
        equip.set(EquipmentSlot.HAND, crossbow);
        SchematicManager.spawnNBTStructure(null, "juggernaut-1", new Location(tutorialWorld, 135, 16, -3), true, true, false);
        bot.spawn(new Location(tutorialWorld, 135.5, 17, -52.5, 0f, -17f));
        refreshEntity();
        
        // Second bot
        victim = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Uh oh");
        victim.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
        EntityPoseTrait pose = victim.getOrAddTrait(EntityPoseTrait.class);
        pose.setPose(EntityPose.CROUCHING);
        Equipment victimEquip = victim.getOrAddTrait(Equipment.class);
        victimEquip.set(EquipmentSlot.CHESTPLATE, createLeatherArmor(Material.LEATHER_CHESTPLATE, false));
        victimEquip.set(EquipmentSlot.LEGGINGS, createLeatherArmor(Material.LEATHER_LEGGINGS, false));
        victimEquip.set(EquipmentSlot.BOOTS, new ItemStack(Material.IRON_BOOTS));
        victimEquip.set(EquipmentSlot.HAND, new ItemStack(Material.BOW));
        victim.spawn(new Location(tutorialWorld, 135.5, 14, -11, 180f, 0f));
        limitedRepeatingTask(() -> {
            victimPlayer = victim.getEntity();
            victimPlayer.setVelocity(new Vector(0, 0, -0.085));
        }, 74);

        ConfigUtils.schedule(30, () -> {
            // Shoot creeper and remove arrow
            Arrow arrow = botPlayer.launchProjectile(Arrow.class);
            Location spawnLoc = arrow.getLocation();
            Creeper creeper = (Creeper) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.CREEPER);
            creeper.customName(ConfigUtils.toComponent("&9" + NPCName + "'s &7Creeper"));
            creeper.setCustomNameVisible(true);
            creeper.setVelocity(arrow.getVelocity().multiply(1.2));
            arrow.remove();
            
            // Remove item from bot hand
            ConfigUtils.sendConfigSound("creepershot", spawnLoc);
            setHandItem(new ItemStack(Material.CROSSBOW));
            ConfigUtils.schedule(20, () -> creeper.setIgnited(true));
        });
        ConfigUtils.schedule(75, () -> victim.destroy());
        return 120;
    }

    @Override
    protected void reset(DBCallback onFinish) {
        SchematicManager.setAirAsync(136, 13, -4, 134, 11, -30, tutorialWorld, o -> onFinish.onQueryDone(null));
    }

}
