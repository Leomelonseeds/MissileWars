package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.arenas.settings.RandomItem;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

public class RandomItemEditor extends MWInventory {
    
    private RandomItem randomItem;
    private MWInventory fromInv;
    private String weightStr;

    public RandomItemEditor(Player player, RandomItem randomItem, MWInventory fromInv) {
        super(player, 54, "Editing " + ConfigUtils.toPlain(randomItem.getModifiableItem().getItemMeta().displayName()));
        this.randomItem = randomItem;
        this.fromInv = fromInv;
        this.weightStr = ConfigUtils.getConfigFile("items.yml").getString("text.itemstats-random-weight");
    }

    @Override
    public void updateInventory() {
        // Main item
        ItemStack displayItem = randomItem.getItem();
        ItemMeta displayMeta = displayItem.getItemMeta();
        List<Component> lore = displayMeta.lore();
        lore.add(ConfigUtils.toComponent(weightStr.replace("%weight%", randomItem.getWeight() + "")));
        displayMeta.lore(lore);
        displayItem.setItemMeta(displayMeta);
        inv.setItem(13, displayItem);
        
        // Anciliary items
        // TODO
        
        // Green glass panes surrounding display item
        for (int i : new int[] {3, 4, 5, 12, 14, 21, 22, 23}) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.LIME_STAINED_GLASS_PANE));
        }
        
        // Blue glass panes
        for (int i : new int[] {1, 7, 9, 11, 15, 17, 19, 25, 27, 35, 37, 39, 41, 43}) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLUE_STAINED_GLASS_PANE));
        }

        // Last row as usual
        for (int i = 45; i < 54; i++) {
            if (i == 49) {
                inv.setItem(i, InventoryUtils.getBackItem());
            } else {
                inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
            }
        }
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        ItemStack item = inv.getItem(slot);
        if (item == null) {
            return;
        }
        
        if (item.equals(InventoryUtils.getBackItem())) {
            manager.registerInventory(player, fromInv);
            return;
        }
    }

}
