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
import com.leomelonseeds.missilewars.arenas.votes.VoteManager;
import com.leomelonseeds.missilewars.invs.PaginatedInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class MapSelector extends PaginatedInventory {
    
    private boolean viewOnly;
    private VoteManager voteManager;
    private String gamemode;
    private Map<String, Integer> selected; // Unmodifiable btw
    private ConfigurationSection itemsSection;

    public MapSelector(Player player, boolean viewOnly, Arena arena) {
        super(player, 36, "Map Selector" + (viewOnly ? " (View Only)" : ""));
        this.viewOnly = viewOnly;
        this.voteManager = arena.getVoteManager();
        this.selected = voteManager.getVotes();
        this.gamemode = arena.getGamemode();
        this.itemsSection = ConfigUtils.getConfigFile("items.yml").getConfigurationSection("arena-settings.map-selector");
    }

    @Override
    protected List<ItemStack> getPaginatedItems() {
        List<ItemStack> items = new ArrayList<>();
        for (String map : voteManager.getVoteableMaps(false)) {
            ItemStack item = new ItemStack(Material.valueOf(itemsSection.getString("map.item")));
            ItemMeta meta = item.getItemMeta();
            String name = ConfigUtils.getMapText(gamemode, map, "name");
            meta.displayName(ConfigUtils.toComponent(name.replace("%umw_map%", name)));
            boolean enabled = selected.containsKey(map);
            List<String> lore = itemsSection.getStringList("lore-" + (enabled ? "enabled" : "disabled"));
            meta.lore(ConfigUtils.toComponent(lore));
            
            // Add glow if enabled
            if (enabled) {
                InventoryUtils.addGlow(meta);
            }
            
            InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, map);
            item.setItemMeta(meta);
            items.add(item);
        }
        return items;
    }

    @Override
    protected void updateNonPaginatedSlots() {
        for (String key : itemsSection.getKeys(false)) {
            if (key.equals("map")) {
                continue;
            }
            
            ItemStack item = InventoryUtils.createItem("arena-settings.map-selector." + key);
            ItemMeta meta = item.getItemMeta();
            InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, key);
            item.setItemMeta(meta);
            inv.setItem(itemsSection.getInt(key + ".slot"), item);
        }
    }

    @Override
    protected void registerPaginatedClick(int slot, ClickType type, ItemStack item) {
        String key = InventoryUtils.getGUIFromItem(item);
        if (key == null) {
            return;
        }
        
        if (key.equals("enable-all")) {
            
        }
    }

}
