package com.leomelonseeds.missilewars.listener;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CrossbowMeta;

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
        
        // Stop crafting
        if (event.getClickedInventory() instanceof CraftingInventory) {
            event.setCancelled(true);
            return;
        }
        
        // Obtain player
        if (!(event.getClickedInventory() instanceof PlayerInventory)) {
            return;
        }
        
        // Stop from moving deck items
        Deck deck = arena.getPlayerInArena(player.getUniqueId()).getDeck();
        ClickType click = event.getClick();
        if (deck != null && deck.getDeckItem(item) != null) {
            if (click == ClickType.LEFT || click == ClickType.RIGHT || click.toString().contains("DROP")) {
                event.setCancelled(true);
                return;
            }
        }

        // Stop armor removals and first slot changes
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
            return;
        }
        
        // Cancel shift-clicking creeper heads
        if (event.getClick() == ClickType.SHIFT_LEFT) {
            if (item.getType() == Material.CREEPER_HEAD || item.getType() == Material.DRAGON_HEAD) {
                event.setCancelled(true);
            }
        }
    }

    /** Manage item dropping. */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        // Make sure this action can't deflect a fireball
        Player player = event.getPlayer();
        ArenaGameruleListener.notLeftClick.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> 
        ArenaGameruleListener.notLeftClick.remove(player.getUniqueId()), 1);
        
        // Cancel if dropping custom item
        if (InventoryUtils.isHeldItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }

        // Check if player is in Arena
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
       
        // Make sure we don't allow gear items to be dropped
        ItemStack dropped = event.getItemDrop().getItemStack();
        String item = dropped.getType().toString();
        if (item.contains("BOW") || item.contains("SWORD") || item.contains("PICKAXE")) {
            event.setCancelled(true);
            return;
        }

        // Handle dropping of deck items
        MissileWarsPlayer mwp = arena.getPlayerInArena(player.getUniqueId());
        DeckItem di = mwp.getDeck().getDeckItem(dropped);
        if (di == null) {
            return;
        }
        
        // Cancel if on cooldown
        if (player.hasCooldown(dropped.getType())) {
            event.setCancelled(true);
            return;
        }
        
        // Give dropped item back to player if hand empty
        ItemStack remaining = player.getInventory().getItem(EquipmentSlot.HAND);
        if (remaining.getType() == Material.AIR) {
            // Replace dropped item with a copy
            ItemStack toDrop = new ItemStack(dropped);
            event.getItemDrop().setItemStack(toDrop);
            dropped.setAmount(1);
            PlayerInventory pinv = player.getInventory();
            pinv.setItem(EquipmentSlot.HAND, dropped);
            di.initCooldown(di.getCurrentCooldown()); // Re-initialize cooldown, since item count set to 0
            player.updateInventory();
            
            // Update crossbow cooldown
            for (ItemStack i : pinv.getContents()) {
                if (i == null || i.getType() != Material.CROSSBOW) {
                    continue;
                }
                
                CrossbowMeta cmeta = (CrossbowMeta) i.getItemMeta();
                if (cmeta.getChargedProjectiles().isEmpty()) {
                    player.setCooldown(Material.CROSSBOW, Math.max(player.getCooldown(Material.ARROW), player.getCooldown(Material.TIPPED_ARROW)));
                }
            }
        } else {
            // Need to re-increase and manually decrease amount so consume doesn't screw over
            remaining.setAmount(remaining.getAmount() + 1);
            InventoryUtils.consumeItem(player, arena, remaining, -1);
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
        if (di == null && item.getType().toString().contains("ARROW") && (deck.getName().equals("Sentinel") || deck.getName().equals("Berserker"))) {
            for (DeckItem temp : deck.getItems()) {
                if (temp.getInstanceItem().getType().toString().contains("ARROW")) {
                    di = temp;
                    break;
                }
            }
        }
        
        if (di != null) {
            event.setCancelled(true);
            di.pickup(event.getItem());
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
        for (DeckItem di : deck.getItems()) {
            ItemStack i = di.getInstanceItem();
            if (!i.getType().toString().contains("ARROW")) {
                continue;
            }

            event.getItem().setItemStack(i);
            event.setCancelled(true);
            if (di.pickup(event.getItem())) {
                event.getArrow().remove();
            }
        }
    }

    /** Remove glass bottles after drinking potions */
    @EventHandler
    public void onDrink(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material type = event.getItem().getType();
        if (!(type == Material.POTION || type == Material.MILK_BUCKET)) {
            return;
        }
        
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> {
            PlayerInventory inv = player.getInventory();
            Material mat = inv.getItem(event.getHand()).getType();
            if (mat == Material.GLASS_BOTTLE || mat == Material.BUCKET) {
                player.getInventory().setItem(event.getHand(), null);
            }
        }, 1L);
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
