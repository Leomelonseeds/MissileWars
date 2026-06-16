package com.leomelonseeds.missilewars.invs.arenasettings;

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
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.listener.handler.ChatPrompt;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class VisibilitySettings extends ArenaSettingsInventory {
    
    private final static String configSec = "arena-settings.visibility-settings";
    
    private ConfigurationSection itemSection;

    public VisibilitySettings(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 27, "Visibility Settings", viewOnly, arena, fromInv);
        this.itemSection = ConfigUtils.getConfigFile("items.yml").getConfigurationSection(configSec);
    }

    @Override
    public Map<Integer, ArenaSetting> getSettingSlots() {
        return Map.of(
            9, ArenaSetting.CAPACITY,
            10, ArenaSetting.IS_PRIVATE,
            11, ArenaSetting.IS_ALWAYS_ONLINE
        );
    }

    @Override
    public void updateSettingsInventory() {
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        for (String key : itemSection.getKeys(false)) {
            String sec = configSec + "." + key;
            ItemStack item = InventoryUtils.createItem(sec);
            if (key.equals("description")) {
                ItemMeta meta = item.getItemMeta();
                List<String> lore = new ArrayList<>();
                lore.addAll(itemSection.getStringList(key + ".lore-pre"));
                lore.addAll(ArenaUtils.getWrappedArenaDescription(arena));
                lore.addAll(itemSection.getStringList(key + ".lore-post"));
                meta.lore(ConfigUtils.toComponent(lore));
                item.setItemMeta(meta);
            }
            
            InventoryUtils.setMetaString(item, InventoryUtils.ITEM_GUI_KEY, key);
            inv.setItem(itemSection.getInt(key + ".slot"), item);
        }
    }

    @Override
    public void registerClick(int slot, ClickType type, ItemStack item) {
        String id = InventoryUtils.getGUIFromItem(item);
        if (id == null) {
            return;
        }
        
        if (viewOnly) {
            viewOnlyDeny();
            return;
        }
        
        if (id.equals("whitelist")) {
            new PlayerlistInventory(player, arena, false, this);
            return;
        }
        
        if (id.equals("blacklist")) {
            new PlayerlistInventory(player, arena, true, this);
            return;
        }
        
        if (id.equals("description")) {
            ConfigUtils.sendConfigMessage("settings.description-prompt", player);
            player.closeInventory();
            new ChatPrompt(player, 120, res -> {
                manager.registerInventory(player, this, false);
                if (res.equalsIgnoreCase("cancel")) {
                    return;
                }
                
                String parsedRes = ConfigUtils.removeColors(res);
                if (parsedRes.length() > 150) {
                    ConfigUtils.sendConfigMessage("settings.description-too-long", player);
                    ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                    return;
                }
                
                String[] words = parsedRes.split(" ");
                for (String word : words) {
                    if (word.length() > 36) {
                        ConfigUtils.sendConfigMessage("settings.description-word-too-long", player);
                        ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                        return;
                    }
                }

                ConfigUtils.sendConfigMessage("settings.description-set", player);
                arena.getArenaSettings().set(ArenaSetting.ARENA_DESCRIPTION, res);
                updateInventory();
            });
        }
    }

}
