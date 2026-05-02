package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.arenas.votes.VoteManager;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.invs.PaginatedInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class MapSelector extends PaginatedInventory {
    
    private boolean viewOnly;
    private VoteManager voteManager;
    private Arena arena;
    private String gamemode;
    private Map<String, Integer> selected; // Unmodifiable btw
    private ConfigurationSection itemsSection;
    private MWInventory fromInv;

    public MapSelector(Player player, boolean viewOnly, Arena arena, MWInventory fromInv) {
        super(player, 36, "Map Selector" + (viewOnly ? " (View Only)" : ""));
        this.viewOnly = viewOnly;
        this.arena = arena;
        this.voteManager = arena.getVoteManager();
        this.selected = voteManager.getVotes();
        this.gamemode = arena.getGamemode();
        this.fromInv = fromInv;
        this.itemsSection = ConfigUtils.getConfigFile("items.yml").getConfigurationSection("arena-settings.map-selector");
    }

    @Override
    protected List<ItemStack> getPaginatedItems() {
        List<ItemStack> items = new ArrayList<>();
        Set<String> maps = new TreeSet<>(voteManager.getVoteableMaps(true));
        for (String map : maps) {
            items.add(getItemForMap(map));
        }
        return items;
    }
    
    private ItemStack getItemForMap(String map) {
        ItemStack item = new ItemStack(Material.valueOf(itemsSection.getString("map.item")));
        ItemMeta meta = item.getItemMeta();
        String name = ConfigUtils.getMapText(gamemode, map, "name");
        meta.displayName(ConfigUtils.toComponent(name.replace("%umw_map%", name)));
        boolean enabled = selected.containsKey(map);
        List<String> lore = itemsSection.getStringList("map.lore-" + (enabled ? "enabled" : "disabled"));
        meta.lore(ConfigUtils.toComponent(lore));
        
        // Add glow if enabled
        if (enabled) {
            InventoryUtils.addGlow(meta);
        }

        // Format: if enabled args[0] == "mape", otherwise args[0] == "map"
        InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, (enabled ? "mape:" : "map:") + map);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    protected void updateNonPaginatedSlots() {
        for (int i = 27; i < 36; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
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
        
        inv.setItem(31, InventoryUtils.getBackItem());
    }

    @Override
    protected void registerPaginatedClick(int slot, ClickType type, ItemStack item) {
        if (item.equals(InventoryUtils.getBackItem())) {
            manager.registerInventory(player, fromInv);
            return;
        }
        
        String key = InventoryUtils.getGUIFromItem(item);
        if (key == null) {
            return;
        }
        
        // Process settings
        if (viewOnly) {
            ConfigUtils.sendConfigMessage("settings.view-only", player);
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            return;
        }
        
        if (key.equals("enable-all")) {
            voteManager.resetAvailableMaps();
            updateInventory();
        } else if (key.equals("disable-all")) {
            voteManager.removeAll();
            updateInventory();
        } else if (key.startsWith("map")) {
            String[] args = key.split(":");
            String map = args[1];
            if (args[0].equals("mape")) {
                voteManager.removeMap(map);
            } else {
                voteManager.addMap(map);
            }
            inv.setItem(slot, getItemForMap(map));
        } else {
            return;
        }
        
        if (arena.isCustom()) {
            arena.getArenaSettings().set(ArenaSetting.MAPS_EDITED, true);
        }
        
        ConfigUtils.sendConfigSound("use-skillpoint", player);
    }

}
