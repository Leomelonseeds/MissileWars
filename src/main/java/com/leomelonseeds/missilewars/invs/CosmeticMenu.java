package com.leomelonseeds.missilewars.invs;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.CosmeticUtils;

import net.kyori.adventure.text.Component;

public class CosmeticMenu implements MWInventory {
    
    private Inventory inv;
    private Player player;
    private String cosmetic;
    
    public CosmeticMenu(Player player, String cosmetic) {
        this.player = player;
        this.cosmetic = cosmetic;
        
        String title = ConfigUtils.getConfigText("inventories.cosmetics." + cosmetic + ".title", null, null, null);
        inv = Bukkit.createInventory(null, 36, Component.text(title));
        manager.registerInventory(player, this); 
    }

    @Override
    public void updateInventory() {
        inv.clear();
        for (ItemStack i : CosmeticUtils.getCosmeticItems(player, cosmetic)) {
            inv.addItem(i);
        }
        
        // Add bottom panes
        for (int i = 27; i < 36; i++) {
            ItemStack item;
            if (i != 31) {
                item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(""));
                item.setItemMeta(meta);
            } else {
                item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(ConfigUtils.toComponent("&cBack"));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        ItemStack item = inv.getItem(slot);
        
        if (type != ClickType.LEFT) {
            return;
        }
        
        if (item == null) {
            return;
        }
        
        if (slot == 31) {
            String command = "bossshop open cosmetics " + player.getName();
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
            return;
        }
        
        if (item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(MissileWarsPlugin.getPlugin(), "name"))) {
            String name = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(MissileWarsPlugin.getPlugin(), "name"),
                    PersistentDataType.STRING);
            JSONObject json = MissileWarsPlugin.getPlugin().getJSON().getPlayer(player.getUniqueId());
            String selected = json.getString(cosmetic);
            // Return if clicked already selected
            if (selected.equals(name)) {
                return;
            }
            // Select if player has permissions for it
            if (CosmeticUtils.hasPermission(player, cosmetic, name)) {
                json.put(cosmetic, name);
                ConfigUtils.sendConfigMessage("messages.cosmetic-selected", player, null, null);
                ConfigUtils.sendConfigSound("change-preset", player);
                updateInventory();
            } else {
                ConfigUtils.sendConfigMessage("messages.cosmetic-locked", player, null, null);
                ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                return;
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

}
