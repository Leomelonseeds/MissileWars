package com.leomelonseeds.missilewars.utilities.cinematic;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.db.DBCallback;
import com.leomelonseeds.missilewars.utilities.schem.SchematicManager;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import net.citizensnpcs.trait.EntityPoseTrait;
import net.citizensnpcs.trait.EntityPoseTrait.EntityPose;

public abstract class TutorialReplay {
    
    public enum Type {
        RIDING,
        PORTAL_BREAKING,
        DEFENDING,
        ABILITIES
    }
    
    // Funny names
    private static final String[] names = {
        "Ghandi",
        "TKaczynski",
        "Obama",
        "Teresa",
        "Mandela",
        "JFK",
        "SunTzu",
        "Hamilton",
        "MLK",
        "Winnie",
        "Akbar",
        "Earhart",
        "Maverick",
        "Wright",
        "Keller",
        "Einstein",
        "Churchill",
        "Chen"
    };
    protected static final String NPCName = names[new Random().nextInt(names.length)];
    protected static final double RUN_SPEED = 0.286;
    protected static final double CROUCH_SPEED = 0.065;
    protected static final double WALK_SPEED = 0.216;

    protected NPC bot;
    protected Player botPlayer;
    protected World tutorialWorld;
    private boolean shouldReplay;
    private boolean playing;
    
    public TutorialReplay(World tutorialWorld) {
        this.tutorialWorld = tutorialWorld;
    }
    
    /**
     * Spawn the NPC and simulate various tasks with it.
     * Make sure tasks are all added to the tasks variable
     * 
     * @return how long the task should take
     */
    protected abstract int play();
    
    /**
     * Reset the world when the tasks have been completed
     * 
     * @param onFinish call when reset is done
     */
    protected abstract void reset(DBCallback onFinish);
    
    /**
     * Start the replay in the world
     */
    public void startReplay() {
        // If the replay is already playing, play again so player catches first part
        if (playing) {
            shouldReplay = true;
            return;
        }
        
        // NPC setup
        playing = true;
        bot = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, NPCName);
        bot.data().setPersistent(NPC.Metadata.DEFAULT_PROTECTED, false);
        bot.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
        bot.data().setPersistent(NPC.Metadata.KEEP_CHUNK_LOADED, true);
        
        // Give NPC blue Sentinel armor
        Equipment equip = bot.getOrAddTrait(Equipment.class);
        equip.set(EquipmentSlot.CHESTPLATE, createLeatherArmor(Material.LEATHER_CHESTPLATE, true));
        equip.set(EquipmentSlot.LEGGINGS, createLeatherArmor(Material.LEATHER_LEGGINGS, true));
        equip.set(EquipmentSlot.BOOTS, new ItemStack(Material.IRON_BOOTS));
        
        // Assign entity to the bot continuously
        BukkitTask assign = Bukkit.getScheduler().runTaskTimer(
                MissileWarsPlugin.getPlugin(), () -> refreshEntity(), 0, 1);
        
        // Play the actions, then reset after things are done
        ConfigUtils.schedule(play(), () -> reset(o -> {
            assign.cancel();
            bot.destroy();
            playing = false;
            if (shouldReplay) {
                shouldReplay = false;
                startReplay();
            }
        }));
    }
    
    protected ItemStack createLeatherArmor(Material material, boolean blue) {
        ItemStack armor = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
        meta.setColor(blue ? DyeColor.BLUE.getColor() : DyeColor.RED.getColor());
        armor.setItemMeta(meta);
        return armor;
    }
    
    /**
     * Reassign the entity to the bot entity
     */
    protected void refreshEntity() {
        botPlayer = (Player) bot.getEntity();
    }
    
    /**
     * Spawn a missile at a location, playing the sound effect
     * 
     * @param name
     * @param loc
     */
    protected void spawnMissile(String name, Location loc) {
        SchematicManager.spawnNBTStructure(null, name, loc, false, true, false);
        ConfigUtils.sendConfigSound("spawn-missile", loc);
    }
    
    /**
     * Change the item the bot is holding to the specified item
     * 
     * @param item
     */
    protected void setHandItem(ItemStack item) {
        Equipment equip = bot.getOrAddTrait(Equipment.class);
        equip.set(EquipmentSlot.HAND, item);
    }
    
    /**
     * Make the NPC crouch
     * 
     * @param crouched
     */
    protected void setCrouched(boolean crouched) {
        EntityPoseTrait pose = bot.getOrAddTrait(EntityPoseTrait.class);
        pose.setPose(crouched ? EntityPose.CROUCHING : EntityPose.STANDING);
    }
    
    /**
     * Make the NPC run
     * 
     * @param direction A direction vector, the Y value will be ignored
     * @param ticks
     */
    protected void run(Vector direction, int ticks) {
        move(direction, ticks, RUN_SPEED);
    }
    
    /**
     * Make the NPC move
     * 
     * @param direction A direction vector, the Y value will be ignored
     * @param ticks
     * @param speed in blocks per tick
     */
    protected void move(Vector direction, int ticks, double speed) {
        Vector xyVelocity = direction.setY(0).normalize().multiply(speed);
        limitedRepeatingTask(() -> {
            Vector velocity = new Vector(xyVelocity.getX(), botPlayer.getVelocity().getY(), xyVelocity.getZ());
            botPlayer.setVelocity(velocity);
        }, ticks);
    }
    
    /**
     * Make the NPC jump
     */
    protected void jump() {
        Vector curVelocity = botPlayer.getVelocity();
        Vector normalized = curVelocity.clone().setY(0).multiply(0.2);
        botPlayer.setVelocity(curVelocity.add(new Vector(normalized.getX(), 0.42, normalized.getZ())));
    }
    
    /**
     * Create a task that starts immediately and repeats itself for 
     * the specified amount of ticks
     * 
     * @param runnable
     */
    protected void limitedRepeatingTask(Runnable runnable, int ticks) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(MissileWarsPlugin.getPlugin(), runnable, 0, 1);
        ConfigUtils.schedule(ticks, () -> task.cancel());
    }
}
