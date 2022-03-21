package io.github.vhorvath2010.missilewars.events;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.arenas.Arena;
import io.github.vhorvath2010.missilewars.arenas.ArenaManager;
import io.github.vhorvath2010.missilewars.schematics.SchematicManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

/** Class to handle events for structure items. */
public class StructureItemEvents implements Listener {

    /**
     * Get a structure from a structure item.
     *
     * @param item the structure item
     * @return the name of the structure, or null if the item has none
     */
    private String getStructureFromItem(ItemStack item) {
        if (item.getItemMeta() == null) {
            return null;
        }
        if (!item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-structure"),
                PersistentDataType.STRING)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get( new NamespacedKey(MissileWarsPlugin.getPlugin(),
                "item-structure"), PersistentDataType.STRING);
    }

    /**
     * Check if a given player is on the red team.
     *
     * @param player the player
     * @return true if they are on the red team, false otherwise (including if they are not on any team at all)
     */
    private boolean isRedTeam(Player player) {
        // Find player's team (Default to blue)
        boolean redTeam = false;
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena != null) {
            redTeam = arena.getTeam(player.getUniqueId()).equalsIgnoreCase(ChatColor.RED +
                    "red" + ChatColor.RESET);
        }
        return redTeam;
    }

    /** Handle missile and other structure item spawning. */
    @EventHandler
    public void useStructureItem(PlayerInteractEvent event) {
        // Check if player is trying to place a structure item
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Block clicked = event.getClickedBlock();
        String structureName = getStructureFromItem(hand);
        if (structureName == null) {
            return;
        }

        // Switch to throwing logic if using shield
        if (structureName.contains("shield")) {
            return;
        }

        // Stop if not left-click on block
        if (!event.getAction().toString().contains("RIGHT") || clicked == null) {
            return;
        }
        event.setCancelled(true);

        // Place structure
        SchematicManager.spawnNBTStructure(structureName, clicked.getLocation(), isRedTeam(player));
        hand.setAmount(hand.getAmount() - 1);
    }

    /** Handle utilities utilization. */
    @EventHandler
    public void useUtility(PlayerInteractEvent event) {
        // Check if player is trying to place a utility item
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Block clicked = event.getClickedBlock();
        if (hand.getItemMeta() == null) {
            return;
        }
        if (!hand.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-utility"),
                PersistentDataType.STRING)) {
            return;
        }
        String utility = hand.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-utility"), PersistentDataType.STRING);
        assert utility != null;
        // Allow event if using bow
        if (utility.equalsIgnoreCase("sentinel_bow")) {
            return;
        }
        event.setCancelled(true);

        // Do proper action based on utility type
        if (utility.equalsIgnoreCase("fireball") && event.getAction().toString().contains("RIGHT_CLICK")) {
            Fireball fireball = (Fireball) player.getWorld().spawnEntity(player.getEyeLocation().clone().add(player
                    .getEyeLocation().getDirection()), EntityType.FIREBALL);
            fireball.setYield(1);
            fireball.setIsIncendiary(false);
            fireball.setDirection(player.getEyeLocation().getDirection());
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    /** Handle shield snowball creation */
    @EventHandler
    public void useShield(ProjectileLaunchEvent event) {
        // Ensure we are tracking a snowball thrown by a player
        if (event.getEntity().getType() != EntityType.SNOWBALL) {
            return;
        }
        Snowball thrown = (Snowball) event.getEntity();
        if (!(thrown.getShooter() instanceof Player)) {
            return;
        }
        Player thrower = (Player) thrown.getShooter();

        // Check if player is holding a structure item
        ItemStack hand = thrower.getInventory().getItemInMainHand();
        String structureName = getStructureFromItem(hand);
        if (structureName == null) {
            return;
        }

        // Add meta for structure identification
        thrown.setCustomName(structureName);

        // Schedule structure spawn after 1 second if snowball is still alive
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!thrown.isDead()) {
                    // Spawn shield at current location and remove snowball
                    Location spawnLoc = thrown.getLocation();
                    SchematicManager.spawnNBTStructure(structureName, spawnLoc, isRedTeam(thrower));
                    thrown.remove();
                }
            }
        }.runTaskLater(MissileWarsPlugin.getPlugin(), 20);
    }

    /** Handle shield generation on hit */
    @EventHandler
    public void shieldHit(ProjectileHitEvent event) {
        // Ensure we are tracking a shield thrown by a player
        if (event.getEntity().getType() != EntityType.SNOWBALL) {
            return;
        }
        Snowball thrown = (Snowball) event.getEntity();
        if (!(thrown.getShooter() instanceof Player) || thrown.getCustomName() == null) {
            return;
        }
        Player thrower = (Player) thrown.getShooter();

        // Attempt to spawn shield
        SchematicManager.spawnNBTStructure(thrown.getCustomName(), thrown.getLocation(), isRedTeam(thrower));
    }

}
