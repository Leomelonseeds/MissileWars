package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSettings;
import com.leomelonseeds.missilewars.invs.ConfirmAction;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.invs.PaginatedInventory;
import com.leomelonseeds.missilewars.listener.handler.ChatPrompt;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class PlayerlistInventory extends PaginatedInventory {
    
    private Set<UUID> players;
    private String listType;
    private Map<String, String> placeholders;
    private ConfigurationSection itemSection;
    private MWInventory from;
    private Arena arena;

    public PlayerlistInventory(Player player, Arena arena, boolean isBlack, MWInventory from) {
        super(player, 36, isBlack ? "&cBlacklist" : "&aWhitelist");
        this.listType = isBlack ? "black" : "white";
        this.placeholders = Map.of(
            "%listtype%", listType,
            "%listtype-capital%", isBlack ? "Black" : "White",
            "%listtype-color%", isBlack ? "&c" : "&a"
        );
                
        ArenaSettings settings = arena.getArenaSettings();
        this.players = isBlack ? settings.getBlacklist() : settings.getWhitelist();
        this.itemSection = ConfigUtils.getConfigFile("items.yml").getConfigurationSection("arena-settings.playerlist");
        this.from = from;
        this.arena = arena;
    }

    @Override
    protected List<ItemStack> getPaginatedItems() {
        List<ItemStack> items = new ArrayList<>();
        for (UUID uuid : players) {
            OfflinePlayer listPlayer = Bukkit.getOfflinePlayer(uuid);
            String listPlayerName = listPlayer.getName();
            if (listPlayerName == null) {
                Bukkit.getLogger().warning("Could not find name for uuid " + uuid);
                continue;
            }
            
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwningPlayer(listPlayer);
            
            // Name
            String name = setPlaceholders(itemSection.getString("player.name"), listPlayerName);
            meta.displayName(ConfigUtils.toComponent(name));
            
            // Lore
            List<String> lore = new ArrayList<>();
            for (String line : itemSection.getStringList("player.lore")) {
                lore.add(setPlaceholders(line));
            }
            meta.lore(ConfigUtils.toComponent(lore));
            
            // Container format: "player#name#uuid"
            InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, "player#" + listPlayerName + "#" + uuid);
            item.setItemMeta(meta);
            items.add(item);
        }
        return items;
    }

    @Override
    protected void updateNonPaginatedSlots() {
        for (int i = 27; i < 36; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        inv.setItem(31, InventoryUtils.getBackItem());
        
        for (String key : itemSection.getKeys(false)) {
            if (key.equals("player")) {
                continue;
            }

            ItemStack item = new ItemStack(Material.valueOf(itemSection.getString(key + ".item")));
            ItemMeta meta = item.getItemMeta();
            
            // Name
            String name = setPlaceholders(itemSection.getString(key + ".name"));
            meta.displayName(ConfigUtils.toComponent(name));
            
            // Lore
            List<String> lore = new ArrayList<>();
            for (String line : itemSection.getStringList(key + ".lore")) {
                lore.add(setPlaceholders(line));
            }
            meta.lore(ConfigUtils.toComponent(lore));
            
            InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, key);
            item.setItemMeta(meta);
            inv.setItem(itemSection.getInt(key + ".slot"), item);
        }
    }

    @Override
    protected void registerPaginatedClick(int slot, ClickType type, ItemStack item) {
        if (item.equals(InventoryUtils.getBackItem())) {
            manager.registerInventory(player, from);
            return;
        }
        
        String id = InventoryUtils.getGUIFromItem(item);
        if (id == null) {
            return;
        }
        
        if (id.startsWith("player")) {
            if (!type.isShiftClick()) {
                return;
            }

            String[] args = id.split("#");
            String name = args[1];
            new ConfirmAction("Un" + listType + "list " + args[1], player, this, yes -> {
                if (!yes) {
                    return;
                }

                UUID uuid = UUID.fromString(args[2]);
                if (players.remove(uuid)) {
                    ConfigUtils.sendConfigMessage("player-unlisted", player, placeholdersWithName(name));
                    ConfigUtils.sendConfigSound("playerlist-modify", player);
                } else {
                    ConfigUtils.sendConfigMessage("player-already-unlisted", player, placeholdersWithName(name));
                    ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                }
                
                updateInventory();
            });
            
            return;
        }
        
        if (id.equals("clear")) {
            if (players.isEmpty()) {
                ConfigUtils.sendConfigMessage("list-already-empty", player, placeholders);
                ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                return;
            }
            
            new ConfirmAction("Clear " + listType + "list", player, this, yes -> {
                if (!yes) {
                    return;
                }

                players.clear();
                ConfigUtils.sendConfigMessage("list-cleared", player, placeholders);
                ConfigUtils.sendConfigSound("playerlist-modify", player);
                updateInventory();
            });
            
            return;
        }
        
        if (id.equals("add")) {
            player.closeInventory();
            ConfigUtils.sendConfigMessage("list-add-prompt", player, placeholders);
            new ChatPrompt(player, 60, res -> {
                manager.registerInventory(player, this);
                if (res == null || res.equals("")) {
                    player.sendMessage(ConfigUtils.toComponent("&cYou took too long to enter a name!"));
                    ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                    return;
                }
                 
                String name = res.toString();
                OfflinePlayer listPlayer = Bukkit.getOfflinePlayerIfCached(name);
                if (listPlayer == null) {
                    ConfigUtils.sendConfigMessage("list-unknown-player", player);
                    ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                    return;
                }
                
                UUID uuid = listPlayer.getUniqueId();
                if (uuid.equals(player.getUniqueId()) || uuid.equals(arena.getArenaSettings().get(ArenaSetting.OWNER_UUID))) {
                    ConfigUtils.sendConfigMessage("list-cannot-add-owner", player, placeholders);
                    ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                    return;
                }
                
                name = listPlayer.getName();
                if (players.add(uuid)) {
                    ConfigUtils.sendConfigMessage("player-listed", player, placeholdersWithName(name));
                    ConfigUtils.sendConfigSound("playerlist-modify", player);
                    
                    // Kick player if they are added to the blacklist and in the arena
                    if (listType.equals("black") && arena.getPlayerInArena(uuid) != null) {
                        Player kicked = Bukkit.getPlayer(uuid);
                        arena.removePlayer(uuid, true);
                        ConfigUtils.sendConfigMessage("messages.arena-blacklisted", kicked, arena, null);
                    }
                } else {
                    ConfigUtils.sendConfigMessage("player-already-listed", player, placeholdersWithName(name));
                    ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                }
                
                updateInventory();
            });
            
            return;
        }
    }
    
    private String setPlaceholders(String s) {
        return setPlaceholders(s, null);
    }
    
    private String setPlaceholders(String s, String playerName) {
        Map<String, String> pmap = playerName == null ? placeholders : placeholdersWithName(playerName);
        for (Entry<String, String> e : pmap.entrySet()) {
            s = s.replace(e.getKey(), e.getValue());
        }
        return s;
    }
    
    private Map<String, String> placeholdersWithName(String name) {
        Map<String, String> pmap = new HashMap<>(placeholders);
        pmap.put("%name%", name);
        return pmap;
    }

}
