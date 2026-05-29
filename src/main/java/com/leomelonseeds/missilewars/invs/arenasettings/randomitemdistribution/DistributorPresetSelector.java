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

import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSettings;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemDistributor;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.listener.handler.ChatPrompt;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class DistributorPresetSelector extends MWInventory {
    
    private static final int INDEX_OFFSET = 20;
    private static final List<String> PRESET_REQS = List.of(
        "",
        "&b&lKNIGHT",
        "&d&lNOBLE",
        "&5&lROYAL",
        "&6&lDIVINE"
    );
    
    private ArenaSettings arenaSettings;
    private RandomItemDistributor editingDistributor;
    private RandomItemDistributionSettings fromInv;
    private ConfigurationSection presetItemSection;

    public DistributorPresetSelector(Player player, ArenaSettings arenaSettings, 
        RandomItemDistributor editingDistributor, RandomItemDistributionSettings fromInv) {
        super(player, 45, "Select Random Item Preset");
        this.arenaSettings = arenaSettings;
        this.fromInv = fromInv;
        this.editingDistributor = editingDistributor;
        this.presetItemSection = ConfigUtils.getConfigFile("items.yml")
            .getConfigurationSection("arena-settings.random-item-distribution.preset-selector-item");
    }

    @Override
    public void updateInventory() {
        // Panes
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 9; j++) {
                if (i >= 1 && i <= 3 && j >= 1 && j <= 7) {
                    continue;
                }
                
                inv.setItem(i * 9 + j, InventoryUtils.createBlankItem(Material.PURPLE_STAINED_GLASS_PANE));
            }
        }
        
        inv.setItem(40, InventoryUtils.getBackItem());
        
        // Preset items. Although it's also 5, do a different loop just in case lol
        for (int i = 0; i < 5; i++) {
            RandomItemDistributor rd = arenaSettings.getRandomItemDistributor(i);
            Material material;
            boolean selected = rd == null ? false : rd.getIndex() == arenaSettings.getOrCreateRandomItemDistributor().getIndex();
            boolean editing = rd == null ? false : rd.getIndex() == editingDistributor.getIndex();
            if (selected) {
                material = Material.valueOf(presetItemSection.getString("item-selected"));
            } else if (editing) {
                material = Material.valueOf(presetItemSection.getString("item-editing"));
            } else {
                material = Material.valueOf(presetItemSection.getString("item"));
            }
            
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            String presetReq = PRESET_REQS.get(i);
            String name = presetItemSection.getString("name")
                    .replace("%preset%", rd == null ? "Preset " + (i + 1) : rd.getName()) 
                    + (presetReq.isEmpty() ? "" : " &8(requires " + presetReq + "&8)");
            meta.displayName(ConfigUtils.toComponent(name));
            
            List<String> lore = new ArrayList<>();
            int itemSize = rd == null ? 0 : rd.getRandomItems().size();
            int abilitySize = rd == null ? 0 : rd.getAbilities().size();
            for (String s : presetItemSection.getStringList("lore")) {
                lore.add(s.replace("%items%", rd == null ? "&8Unset" : itemSize == 0 ? "&8None" : itemSize + "")
                          .replace("%abilities%", abilitySize == 0 ? "&8None" : abilitySize + ""));
            }
            lore.add(presetItemSection.getString(selected ? "selected" : "notselected"));
            lore.add(presetItemSection.getString(editing ? "editing" : "notediting"));
            lore.add(presetItemSection.getString("rename"));
            meta.lore(ConfigUtils.toComponent(lore));
            
            InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, i + "");
            
            if (selected) {
                InventoryUtils.addGlow(meta);
            }
            
            item.setItemMeta(meta);
            inv.setItem(INDEX_OFFSET + i, item);
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
        
        String indexString = InventoryUtils.getGUIFromItem(item);
        if (indexString == null) {
            return;
        }
        
        int index = Integer.parseInt(indexString);
        if (index >= arenaSettings.getMaximumRandomItemDistributors(player)) {
            ConfigUtils.sendConfigMessage("settings.distributor-preset-rank", player, Map.of("%rank%", PRESET_REQS.get(index)));
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            return;
        }
        
        RandomItemDistributor rd = arenaSettings.getRandomItemDistributor(index);
        if (type.isShiftClick()) {
            player.closeInventory();
            String presetName = rd == null ? "Preset " + (index + 1) : rd.getName();
            ConfigUtils.sendConfigMessage("settings.preset-name", player, Map.of("%preset%", presetName));
            new ChatPrompt(player, 60, res -> {
                manager.registerInventory(player, this, false);
                if (res.equalsIgnoreCase("cancel") || res.equals(presetName)) {
                    return;
                }
                
                if (rd == null) {
                    RandomItemDistributor newRd = arenaSettings.getOrCreateRandomItemDistributor(index);
                    newRd.setName(res);
                } else {
                    rd.setName(res);
                }
                
                ConfigUtils.sendConfigSound("purchase-item", player);
                updateInventory();
            });
            return;
        }

        if (!type.isRightClick() && !type.isLeftClick()) {
            return;
        }
        
        RandomItemDistributor realRd = arenaSettings.getOrCreateRandomItemDistributor(index);
        editingDistributor = realRd;
        fromInv.setDistributor(realRd);
        ConfigUtils.sendConfigSound("bulk-edit", player);
        
        if (type.isLeftClick()) {
            arenaSettings.set(ArenaSetting.DISTRIBUTOR_PRESET, index);
        }
        
        updateInventory();
    }

}
