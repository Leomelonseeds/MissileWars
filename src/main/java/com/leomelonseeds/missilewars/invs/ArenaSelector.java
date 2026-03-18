package com.leomelonseeds.missilewars.invs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.ArenaManager;
import com.leomelonseeds.missilewars.arenas.ArenaType;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSettings;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;

public class ArenaSelector extends PaginatedMWIInventory {
    
    private final static int SIZE = 36;

    private final String itemConfig;
    private boolean isCustom;
    private Player player;
    private UUID playerUUID;
    private ArenaManager arenaManager;
    private ArenaType type;
    private ItemStack ownedArena;

    public ArenaSelector(Player player, ArenaType type) {
        super(player, SIZE, ConfigUtils.getConfigText("inventories." + (type == ArenaType.CUSTOM ? "custom-" : "") + "game-selector.title"));
        this.player = player;
        this.playerUUID = player.getUniqueId();
        this.arenaManager = MissileWarsPlugin.getPlugin().getArenaManager();
        this.isCustom = type == ArenaType.CUSTOM;
        this.itemConfig = "inventories." + (isCustom ? "custom-" : "") + "game-selector.game-item.";
        autoRefresh(20);
    }

    @Override
    protected List<ItemStack> getPaginatedItems() {
        List<ItemStack> items = new ArrayList<>();
        for (Arena arena : arenaManager.getLoadedArenas(type)) {
            // Set item and amount
            ArenaSettings arenaSettings = arena.getArenaSettings();
            boolean isOwner = arenaSettings.get(ArenaSetting.OWNER_UUID).equals(playerUUID);
            int playerCount = arena.getNumPlayers();
            ItemStack arenaItem = new ItemStack(isOwner ? Material.EMERALD_BLOCK : Material.TNT, Math.max(1, playerCount));
            ItemMeta arenaItemMeta = arenaItem.getItemMeta();
            
            // Display name
            String display = ConfigUtils.getConfigText(itemConfig + (isOwner ? "name" : "name-own"), player, arena, null);
            arenaItemMeta.displayName(ConfigUtils.toComponent(display));
            
            // Lore, with the last line determined by whether the player is whitelisted + type of arena
            List<Component> lore = new ArrayList<>();
            for (String s : ConfigUtils.getConfigTextList(itemConfig + "lore", player, arena, null)) {
                lore.add(ConfigUtils.toComponent(s));
            }
            
            if (isCustom) {
                if (arena.getBooleanSetting(ArenaSetting.IS_PRIVATE)) {
                    if (isOwner) {
                        lore.add(ConfigUtils.toComponent(itemConfig + "join-public"));
                    } else if (arenaSettings.isWhitelisted(playerUUID)) {
                        lore.add(ConfigUtils.toComponent(itemConfig + "join-whitelisted"));
                    } else {
                        lore.add(ConfigUtils.toComponent(itemConfig + "join-not-whitelisted"));
                    }
                } else {
                    if (arenaSettings.isBlacklisted(playerUUID)) {
                        lore.add(ConfigUtils.toComponent(itemConfig + "join-blacklisted"));
                    } else {
                        lore.add(ConfigUtils.toComponent(itemConfig + "join-public"));
                    }
                }
            }
            
            arenaItemMeta.lore(lore);
            InventoryUtils.setMetaString(arenaItemMeta, InventoryUtils.ITEM_GUI_KEY, arena.getName());
            
            // Add glowing effect if people are inside
            if (playerCount >= 1) {
                arenaItemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                arenaItemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }

            arenaItem.setItemMeta(arenaItemMeta);
            items.add(arenaItem);
            
            if (isOwner) {
                ownedArena = arenaItem;
            }
        }
        
        return items;
    }

    @Override
    protected void updateNonPaginatedSlots() {
        for (int i = SIZE - 9; i < SIZE; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        ItemStack backItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(ConfigUtils.toComponent("&cBack"));
        backItem.setItemMeta(backMeta);
        inv.setItem(SIZE - 5, backItem);
        
        if (!isCustom) {
            return;
        }
        
        FileConfiguration messagesConfig = ConfigUtils.getConfigFile("messages.yml");
        if (ownedArena != null) {
            inv.setItem(SIZE - 4, ownedArena);
        } else {
            ConfigurationSection createSection = messagesConfig.getConfigurationSection("inventories.custom-game-selector.create-item");
            ItemStack createItem = new ItemStack(Material.valueOf(createSection.getString("item")));
            ItemMeta createMeta = createItem.getItemMeta();
            createMeta.displayName(ConfigUtils.toComponent(createSection.getString("name")));
            
            // TODO: Check if meet rank requirement (RankUtils function % 10 == 0), then find the rank requirement number in the lines (find " 5") etc
            List<String> createLore = createSection.getStringList("lore");
            for (String line : createLore) {
                
            }
            
        }
    }

    @Override
    protected void registerPaginatedClick(int slot, ClickType type, ItemStack item) {
        if (slot == SIZE - 5) {
            String command = "bossshop open menu " + player.getName();
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
            return;
        }
        
        String id = InventoryUtils.getGUIFromItem(item);
        if (id == null) {
            return;
        }
        
        Arena arena = arenaManager.getArena(id);
        if (arena == null) {
            ConfigUtils.sendConfigMessage("arena-not-exist", player);
            return;
        }
        
        arena.joinPlayer(player);
        player.closeInventory();
    }
}
