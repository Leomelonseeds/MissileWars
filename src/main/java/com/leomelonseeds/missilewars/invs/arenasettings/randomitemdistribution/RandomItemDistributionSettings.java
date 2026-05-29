package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemDistributor;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.invs.arenasettings.ArenaSettingsInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

public class RandomItemDistributionSettings extends ArenaSettingsInventory {
    
    private final static String sec = "arena-settings.random-item-distribution.main-menu";
    
    private ConfigurationSection itemsSection;
    private RandomItemDistributor distributor;

    public RandomItemDistributionSettings(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 54, "Random Item Settings", viewOnly, arena, fromInv);
        this.itemsSection = ConfigUtils.getConfigFile("items.yml").getConfigurationSection(sec);
        this.distributor = arenaSettings.getOrCreateRandomItemDistributor();
    }

    @Override
    public Map<Integer, ArenaSetting> getSettingSlots() {
        return Map.of(
            36, ArenaSetting.RANDOM_ITEM_DISTRIBUTION_TIMER,
            37, ArenaSetting.RANDOM_ITEM_BAG_DISTRIBUTION,
            38, ArenaSetting.RANDOM_ITEM_XP_TIMER,
            39, ArenaSetting.RANDOM_ITEM_INVENTORY_LIMIT
        );
    }

    @Override
    public void updateSettingsInventory() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 1 && j >= 1 && j <= 4) {
                    continue;
                }

                inv.setItem(i * 9 + j, InventoryUtils.createBlankItem(Material.MAGENTA_STAINED_GLASS_PANE));
            }
            
            for (int j = 6; j < 9; j++) {
                if (i == 1 && j == 7) {
                    continue;
                }

                inv.setItem(i * 9 + j, InventoryUtils.createBlankItem(Material.PURPLE_STAINED_GLASS_PANE));
            }
        }
        
        for (int i = 27; i < 36; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        for (String key : itemsSection.getKeys(false)) {
            ItemStack item = InventoryUtils.createItem(sec + "." + key);
            if (key.equals("choose-preset")) {
                ItemMeta meta = item.getItemMeta();
                List<Component> lore = new ArrayList<>();
                for (String s : itemsSection.getStringList(key + ".lore")) {
                    s = s.replace("%using-preset%", arenaSettings.getOrCreateRandomItemDistributor().getName())
                         .replace("%editing-preset%", distributor.getName());
                    lore.add(ConfigUtils.toComponent(s));
                }
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            InventoryUtils.setMetaString(item, InventoryUtils.ITEM_GUI_KEY, key);
            inv.setItem(itemsSection.getInt(key + ".slot"), item);
        }
    }

    @Override
    public void registerClick(int slot, ClickType type, ItemStack item) {
        String key = InventoryUtils.getGUIFromItem(item);
        if (key == null) {
            return;
        }
        
        if (key.equals("edit-items")) {
            new RandomItemsList(player, distributor, viewOnly, this);
            return;
        }
        
        if (key.equals("edit-abilities")) {
            new EnabledAbilities(player, distributor, viewOnly, this);
            return;
        }
        
        if (key.equals("choose-preset")) {
            new DistributorPresetSelector(player, arenaSettings, distributor, this);
            return;
        }
    }
    
    public void setDistributor(RandomItemDistributor distributor) {
        this.distributor = distributor;
    }
}
