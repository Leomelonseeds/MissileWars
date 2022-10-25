package com.leomelonseeds.missilewars.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.invs.InventoryManager;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;

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
        
        if (inv == null) {
            return;
        }
        
        if (inv.equals(event.getView().getBottomInventory()) && event.getClick().isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        
        if (!inv.equals(event.getView().getTopInventory())){
            return; 
        }
        
        event.setCancelled(true);

        manager.getInventory(player).registerClick(event.getSlot(), event.getClick());
    }
    
    /** Unregister custom mwinventories when they are closed. */
    @EventHandler
    public void unregisterCustomInventories(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        
        // Stop stupid people from exiting the tutorial GUI
        if (ConfigUtils.toPlain(event.getView().title()).contains("Have you played Missile Wars")) {
            Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
                if (!player.hasPermission("umw.tutorial")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bossshop open tutorial " + player.getName());
                }
            }, 5);
            return;
        }
        
        // Unregister
        InventoryManager manager = MissileWarsPlugin.getPlugin().getInvs();
        if (manager.getInventory(player) instanceof MWInventory) {
            manager.removePlayer(player);
        }
    }

    /** Stop players from changing their armor/bow items. */
    @EventHandler
    public void stopItemMoving(InventoryClickEvent event) {
        
        Player player = (Player) event.getWhoClicked();
        if (event.getCurrentItem() == null) {
            return;
        }

        // Check if player is in an active arena
        ArenaManager manager = MissileWarsPlugin.getPlugin().getArenaManager();
        Arena arena = manager.getArena(player.getUniqueId());
        if (arena == null) {
            // Stop guests from using elytra
            if (player.hasPermission("umw.elytra")) {
                return;
            }
            
            if (event.getCurrentItem().getType() == Material.ELYTRA) {
                event.setCancelled(true);
            }
            
            return;
        }
        
        // Stop crafting
        if (event.getClickedInventory() instanceof CraftingInventory) {
            event.setCancelled(true);
            return;
        }
        
        // Obtain player
        if (!(event.getClickedInventory() instanceof PlayerInventory)) {
            return;
        }

        // Stop armor removals and first slot changes
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
            return;
        }
        
        // Cancel shift-clicking creeper heads
        if (event.getClick() == ClickType.SHIFT_LEFT) {
            if (event.getCurrentItem().getType() == Material.CREEPER_HEAD) {
                event.setCancelled(true);
            }
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
        
        String item = event.getItemDrop().getItemStack().toString();
        
        // Make sure we allow gear items to be used
        if (item.contains("BOW") || item.contains("SWORD") || item.contains("PICKAXE")) {
            event.setCancelled(true);
        }
    }

    /** Manage item pickups. */
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        // Check if player is in Arena
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        ArenaManager manager = plugin.getArenaManager();
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
        if (!deck.hasInventorySpace(mwPlayer.getMCPlayer(), false)) {
            event.setCancelled(true);
            return;
        }
        
        if (plugin.getJSON().getAbility(player.getUniqueId(), "missilesmith") > 0) {
            ItemStack item = event.getItem().getItemStack();
            // Can't be same player
            if (event.getItem().getThrower() == player.getUniqueId()) {
                return;
            }
            // Must be a missile
            if (!item.getType().toString().contains("SPAWN_EGG")) {
                return;
            }
            // Must have item structure data
            if ((item.getItemMeta() == null) || !item.getItemMeta().getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "item-structure"), PersistentDataType.STRING)) {
                return;
            }
            // Must not be a Berserker missile
            for (ItemStack i : mwPlayer.getDeck().getMissiles()) {
                if (i.isSimilar(item)) {
                    return;
                }
            }
            
            String[] args = item.getItemMeta().getPersistentDataContainer().get( new NamespacedKey(plugin,
                    "item-structure"), PersistentDataType.STRING).split("-");
            String name = args[0];
            int level = Integer.parseInt(args[1]);
            int maxlevel = plugin.getDeckManager().getMaxLevel(name);
            
            if (level < plugin.getDeckManager().getMaxLevel(name)) {
                event.getItem().setItemStack(plugin.getDeckManager().createItem(name, maxlevel, true));
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
            return;
        }
        
        // Check slowness arrow pickups
        ItemStack pickedUp = event.getItem().getItemStack();
        if (MissileWarsPlugin.getPlugin().getJSON().getAbility(player.getUniqueId(), "slownessarrows") > 0 &&
                pickedUp.getType() == Material.TIPPED_ARROW) {
            int index = ConfigUtils.getConfigFile("items.yml").getInt("arrows.index");
            ItemStack tippedArrow = new ItemStack(deck.getUtility().get(index));
            tippedArrow.setAmount(pickedUp.getAmount());
            event.getItem().setItemStack(tippedArrow);
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
