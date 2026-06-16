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
import com.leomelonseeds.missilewars.arenas.CustomArenaCreationSession;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSetting;
import com.leomelonseeds.missilewars.arenas.settings.ArenaSettings;
import com.leomelonseeds.missilewars.invs.arenasettings.ArenaSettingsMainMenu;
import com.leomelonseeds.missilewars.invs.pagination.PaginatedInventory;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;

public class ArenaSelector extends PaginatedInventory {
    
    private static final int SIZE = 36;

    private final String itemConfig;
    private boolean isCustom;
    private Player player;
    private UUID playerUUID;
    private ArenaManager arenaManager;
    private ArenaType type;
    private ItemStack ownedArena;
    private ConfigurationSection sec;

    public ArenaSelector(Player player, ArenaType type) {
        super(player, SIZE, ConfigUtils.getConfigText("inventories." + (type == ArenaType.CUSTOM ? "custom-" : "") + "game-selector.title"));
        this.player = player;
        this.playerUUID = player.getUniqueId();
        this.arenaManager = MissileWarsPlugin.getPlugin().getArenaManager();
        this.isCustom = type == ArenaType.CUSTOM;
        this.itemConfig = "inventories." + (isCustom ? "custom-" : "") + "game-selector.game-item.";
        this.type = type;
        this.sec = ConfigUtils.getConfigFile("messages.yml").getConfigurationSection(itemConfig);
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
            String display = ConfigUtils.getConfigText(itemConfig + (isOwner ? "name-own" : "name"), player, arena, null);
            arenaItemMeta.displayName(ConfigUtils.toComponent(display));
            
            // Lore, with the last line determined by whether the player is whitelisted + type of arena
            List<String> lore = new ArrayList<>();
            if (isCustom) {
                lore.addAll(sec.getStringList("lore-desc"));
                lore.addAll(ArenaUtils.getWrappedArenaDescription(arena));
            }
            
            lore.addAll(ConfigUtils.getConfigTextList(itemConfig + "lore", player, arena, null));
            if (isCustom) {
                if (isOwner || isJoinable(arena)) {
                    List<String> viewableString = sec.getStringList("joinable");
                    if (isOwner) {
                        viewableString.replaceAll(s -> s.replace("view", "edit"));
                    }
                    lore.addAll(viewableString);
                } else if (arenaSettings.isBlacklisted(playerUUID)) {
                    lore.add(sec.getString("join-blacklisted"));
                } else {
                    lore.add(sec.getString("join-not-whitelisted"));
                }
            }
            
            arenaItemMeta.lore(ConfigUtils.toComponent(lore));
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
    
    /**
     * DOES NOT CHECK FOR IS OWNER
     * 
     * @param arena
     * @return
     */
    private boolean isJoinable(Arena arena) {
        ArenaSettings settings = arena.getArenaSettings();
        if (settings.isBlacklisted(playerUUID)) {
            return false;
        }
        
        return settings.isWhitelisted(playerUUID) || !arena.getBooleanSetting(ArenaSetting.IS_PRIVATE);
    }

    @Override
    protected void updateNonPaginatedSlots() {
        if (!isCustom) {
            return;
        }
        
        FileConfiguration messagesConfig = ConfigUtils.getConfigFile("messages.yml");
        if (ownedArena != null) {
            inv.setItem(SIZE - 4, ownedArena);
            ConfigurationSection editSection = messagesConfig.getConfigurationSection("inventories.custom-game-selector.edit-item");
            ItemStack editItem = new ItemStack(Material.valueOf(editSection.getString("item")));
            ItemMeta editMeta = editItem.getItemMeta();
            editMeta.displayName(ConfigUtils.toComponent(editSection.getString("name")));
            editMeta.lore(ConfigUtils.toComponent(editSection.getStringList("lore")));
            InventoryUtils.setMetaString(editMeta, InventoryUtils.ITEM_GUI_KEY, "edit-arena");
            editItem.setItemMeta(editMeta);
            inv.setItem(SIZE - 6, editItem);
        } else {
            ConfigurationSection createSection = messagesConfig.getConfigurationSection("inventories.custom-game-selector.create-item");
            ItemStack createItem = new ItemStack(Material.valueOf(createSection.getString("item")));
            ItemMeta createMeta = createItem.getItemMeta();
            createMeta.displayName(ConfigUtils.toComponent(createSection.getString("name")));
            
            // Check if meet rank requirement (RankUtils function % 10 == 0), then find the rank requirement number in the lines (find " 5") etc
            List<String> createLore = createSection.getStringList("lore");
            int requirement = RankUtils.canCreateCustomArena(player);
            if (requirement % 10 == 0) {
                requirement /= 10;
                createLore.addAll(createSection.getStringList("can-create"));
                InventoryUtils.setMetaString(createMeta, InventoryUtils.ITEM_GUI_KEY, "can-create");
            } else {
                createLore.add(createSection.getString("cannot-create"));
                InventoryUtils.setMetaString(createMeta, InventoryUtils.ITEM_GUI_KEY, "cannot-create");
            }
            
            for (int i = 0; i < createLore.size(); i++) {
                String cur = createLore.get(i);
                if (cur.contains(" " + requirement)) {
                    createLore.set(i, cur + " &7(You)");
                    break;
                }
            }
            
            createMeta.lore(ConfigUtils.toComponent(createLore));
            createItem.setItemMeta(createMeta);
            inv.setItem(SIZE - 4, createItem);
        }
    }

    @Override
    protected void registerPaginatedClick(int slot, ClickType type, ItemStack item) {
        if (item.equals(InventoryUtils.getBackItem())) {
            String command = "bossshop open menu " + player.getName();
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
            return;
        }
        
        String id = InventoryUtils.getGUIFromItem(item);
        if (id == null) {
            return;
        }
        
        if (id.equals("can-create")) {
            new ConfirmAction("Create custom arena", player, this, t -> {
                if (!t) {
                    return;
                }
                
                new CustomArenaCreationSession(player);
                ConfigUtils.schedule(1, () -> player.closeInventory());
            });
            
            return;
        }
        
        if (id.equals("cannot-create")) {
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            ConfigUtils.sendConfigMessage("cannot-create-custom", player);
            return;
        }
        
        if (id.equals("edit-arena")) {
            Arena owned = arenaManager.getCustomArena(player);
            
            // This shouldn't even happen
            if (owned == null) {
                ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                ConfigUtils.sendConfigMessage("cannot-edit-custom", player);
                return;
            } 
            
            new ArenaSettingsMainMenu(player, owned, false, this);
            return;
        }
        
        Arena arena = arenaManager.getArena(id);
        if (arena == null) {
            ConfigUtils.sendConfigMessage("arena-not-exist", player);
            return;
        }
        
        boolean isOwner = arena.getArenaSettings().get(ArenaSetting.OWNER_UUID).equals(playerUUID);
        if (isCustom && type.isRightClick() && (isOwner || isJoinable(arena))) {
            new ArenaSettingsMainMenu(player, arena, !isOwner, this);
            return;
        }
        
        if (arena.joinPlayer(player)) {
            player.closeInventory();
            return;
        }
    }
}
