package com.leomelonseeds.missilewars.invs.arenasettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.invs.ConfirmAction;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class ArenaSettingsMainMenu extends MWInventory {
    
    private final static String mainSec = "arena-settings.main-menu";
    private static Map<String, Consumer<MWInventory>> actions; // argument for consumer is THIS
    
    private boolean viewOnly;
    private Player player;
    private Arena arena;
    private FileConfiguration itemConfig;
    private MWInventory fromInv;

    public ArenaSettingsMainMenu(Player player, Arena arena, boolean viewOnly, MWInventory fromInv) {
        super(
            player,
            viewOnly ? 36 : 45,
            "Arena Settings" + (viewOnly ? " (View only)" : "")
        );
        
        this.player = player;
        this.arena = arena;
        this.viewOnly = viewOnly;
        this.itemConfig = ConfigUtils.getConfigFile("items.yml");
        this.fromInv = fromInv;
        loadActions();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void updateInventory() {
        for (String key : itemConfig.getConfigurationSection(mainSec).getKeys(false)) {
            String sec = mainSec + "." + key;
            int slot = itemConfig.getInt(sec + ".slot");
            if (slot >= inv.getSize()) {
                continue;
            }
            
            ItemStack item = InventoryUtils.createItem(sec);
            ItemMeta meta = item.getItemMeta();
            InventoryUtils.setMetaString(meta, InventoryUtils.ITEM_GUI_KEY, key);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }
        
        for (int i = 27; i <= 35; i++) {
            inv.setItem(i, InventoryUtils.createBlankItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        inv.setItem(viewOnly ? 31 : 40, InventoryUtils.getBackItem());
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        ItemStack item = inv.getItem(slot);
        if (item == null) {
            return;
        }
        
        if (item.equals(InventoryUtils.getBackItem())) {
            if (fromInv != null) {
                manager.registerInventory(player, fromInv);
            } else {
                player.closeInventory();
            }
            
            return;
        }
        
        String key = InventoryUtils.getGUIFromItem(item);
        if (key == null || !actions.containsKey(key)) {
            return;
        }
        
        if (!arena.isOnline() && Set.of("start-game", "end-game", "creative-mode", "kick-all").contains(key)) {
            ConfigUtils.sendConfigMessage("arena-action-offline", player);
            ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            return;
        }
        
        
        actions.get(key).accept(this);
    }
    
    private void loadActions() {
        if (actions != null) {
            return;
        }
        
        actions = new HashMap<>();
        
        actions.put("visibility-settings", t -> {
            new VisibilitySettings(player, viewOnly, arena, this);
        });
        
        actions.put("map-selector", t -> {
            new MapSelector(player, viewOnly, arena, this);
        });
        
        actions.put("other-settings", t -> {
            new OtherSettings(player, viewOnly, arena, this);
        });
        
        actions.put("start-game", t -> {
            if (arena.isStarted()) {
                ConfigUtils.sendConfigMessage("arena-already-started", player);
                ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            } else {
                arena.scheduleStart();
            }
        });
        
        actions.put("end-game", t -> {
            if (!arena.isStarted()) {
                ConfigUtils.sendConfigMessage("arena-no-game-to-end", player);
                ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
            } else if (arena.isRunning()) {
                arena.endGame(null);
            } else {
                arena.cancelStart();
            }
        });
        
        actions.put("kick-all", t -> {
            new ConfirmAction("Kick All", player, t, res -> {
                if (!res) {
                    return;
                }

                ConfigUtils.sendConfigMessage("arena-kick-all", player);
                for (UUID uuid : new ArrayList<>(arena.getPlayers())) {
                    if (!uuid.equals(player.getUniqueId())) {
                        arena.removePlayer(uuid, true);
                    }
                }
            });
        });
    }
}
