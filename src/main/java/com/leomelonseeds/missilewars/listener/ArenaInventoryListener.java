package com.leomelonseeds.missilewars.listener;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.decks.Deck;
import com.leomelonseeds.missilewars.decks.DeckItem;
import com.leomelonseeds.missilewars.decks.DeckStorage;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CooldownUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

/** Class to manage arena joining and pregame events. */
public class ArenaInventoryListener implements Listener {

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
        Arena arena = ArenaUtils.getArena(player);
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
        MiscListener.notLeftClick.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(MissileWarsPlugin.getPlugin(), () -> 
        MiscListener.notLeftClick.remove(player.getUniqueId()), 1);
        
        // Cancel if dropping custom item
        if (InventoryUtils.isHeldItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }

        // Check if player is in Arena
        Arena arena = ArenaUtils.getArena(player);
        if (arena == null) {
            return;
        }

        // Stop drops entirely if player not on team
        if ((arena.getTeam(player.getUniqueId()) == TeamName.NONE)) {
            event.setCancelled(true);
            return;
        }
       
        // Make sure we don't allow gear items to be dropped
        ItemStack dropped = event.getItemDrop().getItemStack();
        String item = dropped.getType().toString();
        if (item.contains("BOW") || item.contains("SWORD") || item.contains("PICKAXE") || item.equals("TRIDENT")) {
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
            CooldownUtils.updateCrossbowCooldowns(player);
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
        Arena arena = ArenaUtils.getArena(player);
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
        if (di == null && item.getType().toString().contains("ARROW") && 
                (deck.getType() == DeckStorage.SENTINEL || deck.getType() == DeckStorage.BERSERKER)) {
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
        Arena arena = ArenaUtils.getArena(player);
        if (arena == null) {
            return;
        }
        
        MissileWarsPlayer mwPlayer = arena.getPlayerInArena(player.getUniqueId());
        Deck deck = mwPlayer.getDeck();
        
        if (deck == null) {
            return;
        }
        
        // Stop decks without bows to pick up arrows
        if (deck.getType() == DeckStorage.VANGUARD || deck.getType() == DeckStorage.ARCHITECT) {
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

            Item ei = event.getItem();
            ei.setItemStack(i);
            event.setCancelled(true);
            if (!di.pickup(ei)) {
                return;
            }

            player.playPickupItemAnimation(ei);
            event.getArrow().remove();
            ConfigUtils.sendConfigSound("pickup", player);
            return;
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
}
