package com.leomelonseeds.missilewars.listener;

import java.util.List;
import java.util.UUID;

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
import org.bukkit.event.player.PlayerItemHeldEvent;
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
import com.leomelonseeds.missilewars.decks.DeckItem;
import com.leomelonseeds.missilewars.invs.InventoryManager;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

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
        
        ItemStack item = event.getCurrentItem();
        if (InventoryUtils.isHeldItem(item)) {
            event.setCancelled(true);
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
            
            if (item.getType() == Material.ELYTRA) {
                event.setCancelled(true);
            }
            
            return;
        }
        
        // Stop from moving deck items
        MissileWarsPlayer mwp = arena.getPlayerInArena(player.getUniqueId());
        ClickType click = event.getClick();
        if (mwp.getDeck().getDeckItem(item) != null) {
            if (click == ClickType.LEFT || click == ClickType.RIGHT || click.toString().contains("DROP")) {
                event.setCancelled(true);
                return;
            }
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
            if (item.getType() == Material.CREEPER_HEAD) {
                event.setCancelled(true);
            }
        }
    }

    /** Manage item dropping. */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        // Cancel if dropping custom item
        if (InventoryUtils.isHeldItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }

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

        // Handle dropping of deck items
        ItemStack dropped = event.getItemDrop().getItemStack();
        MissileWarsPlayer mwp = arena.getPlayerInArena(player.getUniqueId());
        DeckItem di = mwp.getDeck().getDeckItem(dropped);
        if (di != null) {
            di.consume();
        }
       
        // Make sure we don't allow gear items to be dropped
        String item = dropped.getType().toString();
        if (item.contains("BOW") || item.contains("SWORD") || item.contains("PICKAXE")) {
            event.setCancelled(true);
            return;
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
        
        // Allow pickup if item is a deconstructor block
        ItemStack item = event.getItem().getItemStack();
        List<String> accepted = plugin.getConfig().getStringList("deconstructor-blocks");
        String material = item.getType().toString();
        for (String s : accepted) {
            if (material.contains(s)) {
                return;
            }
        }

        // Cancel event if player cannot pick up item based on their given deck
        DeckItem di = deck.getDeckItem(item);
        if (di != null) {
            int amount = item.getAmount();
            int toPickup = di.pickup(amount);
            if (toPickup == 0) {
                event.setCancelled(true);
                return;
            }
            
            if (amount > toPickup) {
                ItemStack drop = new ItemStack(item);
                drop.setAmount(amount - toPickup);
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                item.setAmount(toPickup);
            }
        }
        
        if (plugin.getJSON().getAbility(player.getUniqueId(), "missilesmith") > 0) {
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
        
        // At this point, we know that it is either sentinel or berserker picking up the arrow
        // We then convert the itemstack directly into the corresponding type of the player deck
        ItemStack pickedUp = event.getItem().getItemStack();
        for (DeckItem di : deck.getItems()) {
            ItemStack i = di.getItem();
            if (!i.getType().toString().contains("ARROW")) {
                continue;
            }
            
            if (i.getAmount() >= di.getMax()) {
                event.setCancelled(true);
                return;
            }
            
            pickedUp = new ItemStack(i);
            pickedUp.setAmount(1);
            di.pickup(1);
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
    
    // Kill canopy cooldown if item switch
    @EventHandler
    public void onSwitch(PlayerItemHeldEvent e) {
        UUID player = e.getPlayer().getUniqueId();
        if (!CustomItemListener.canopy_cooldown.contains(player)) {
            return;
        }
        ConfigUtils.sendConfigMessage("messages.canopy-cancel", e.getPlayer(), null, null);
        CustomItemListener.canopy_cooldown.remove(e.getPlayer().getUniqueId());
    }

}
