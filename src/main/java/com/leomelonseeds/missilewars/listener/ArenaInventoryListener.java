package com.leomelonseeds.missilewars.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.invs.InventoryManager;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;

/** Class to manage arena joining and pregame events. */
public class ArenaInventoryListener implements Listener {
    
    /** Handle clicking of custom GUIs */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        InventoryManager manager = MissileWarsPlugin.getPlugin().getInvs();
        
        if (!(manager.getInventory(player) instanceof MWInventory)) {
            return;
        }
        
        Inventory inv = event.getClickedInventory();
        if (inv == null || !inv.equals(event.getView().getTopInventory())){
            return; 
        }
        
        event.setCancelled(true);

        manager.getInventory(player).registerClick(event.getSlot(), event.getClick());
    }
    
    /** Unregister custom mwinventories when they are closed. */
    @EventHandler
    public void unregisterCustomInventories(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        InventoryManager manager = MissileWarsPlugin.getPlugin().getInvs();
        if (manager.getInventory(player) instanceof MWInventory) {
            manager.removePlayer(player);
        }
    }

    /** Stop players from changing their armor/bow items. */
    @EventHandler
    public void stopItemMoving(InventoryClickEvent event) {
        // Obtain player
        if (!(event.getClickedInventory() instanceof PlayerInventory)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();

        // Check if player is in an active arena
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena == null || !arena.isRunning()) {
            return;
        }

        // Stop armor removals and first slot changes
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
            return;
        }
    }

    /** Manage item dropping. */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        // Check if player is in Arena
        Player player = event.getPlayer();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena == null) {
            return;
        }

        // Stop drops entirely if player not on team
        if ((arena.getTeam(player.getUniqueId()).equals("no team"))) {
            event.setCancelled(true);
            return;
        }

        // Stop drops of gear items
        MissileWarsPlayer mwPlayer = arena.getPlayerInArena(player.getUniqueId());
        if (mwPlayer.getDeck().getGear().contains(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** Manage item pickups. */
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        // Check if player is in Arena
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena == null) {
            return;
        }
        MissileWarsPlayer mwPlayer = arena.getPlayerInArena(player.getUniqueId());
        
        Deck deck = mwPlayer.getDeck();
        
        if (deck == null) {
            return;
        }
       

        // Cancel event if player cannot pick up item based on their given deck
        if (!deck.hasInventorySpace(mwPlayer.getMCPlayer())) {
            event.setCancelled(true);
            return;
        }
        
        // Stop decks without bows to pick up arrows
        if (deck.getName().equals("Vanguard") || deck.getName().equals("Architect")) {
            if (event.getItem().getItemStack().getType() == Material.ARROW) {
                event.setCancelled(true);
            }
        }
    }
    
    /** Manage arrow pickups. */
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onArrowPickup(PlayerPickupArrowEvent event) {
        // Check if player is in Arena
        Player player = event.getPlayer();
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena == null) {
            return;
        }
        MissileWarsPlayer mwPlayer = arena.getPlayerInArena(player.getUniqueId());
        
        Deck deck = mwPlayer.getDeck();
        
        if (deck == null) {
            return;
        }
        
        // Stop decks without bows to pick up arrows
        if (deck.getName().equals("Vanguard") || deck.getName().equals("Architect")) {
            event.setCancelled(true);
        }
    }

    /** Remove glass bottles after drinking potions */
    @EventHandler
    public void onDrink(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (event.getItem().getType() == Material.POTION) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.getInventory().getItemInMainHand().getType() == Material.GLASS_BOTTLE) {
                        player.getInventory().setItemInMainHand(null);
                    } else if (player.getInventory().getItemInOffHand().getType() == Material.GLASS_BOTTLE) {
                        player.getInventory().setItemInOffHand(null);
                    }
                }
            }.runTaskLater(MissileWarsPlugin.getPlugin(), 1L);
        }
    }

}
