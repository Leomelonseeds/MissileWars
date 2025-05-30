package com.leomelonseeds.missilewars.invs;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.arenas.votes.VoteManager;
import com.leomelonseeds.missilewars.arenas.votes.VotePlayer;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.RankUtils;

import net.kyori.adventure.text.Component;

public class MapVoting implements MWInventory {
    
    private Inventory inv;
    private Player player;
    private NamespacedKey mapKey;
    private VoteManager voteManager;
    private Arena arena;
    
    public MapVoting(Player player, Arena arena) {
        this.player = player;
        this.mapKey = new NamespacedKey(MissileWarsPlugin.getPlugin(), "map");
        this.arena = arena;
        this.voteManager = arena.getVoteManager();
        
        String title = ConfigUtils.getConfigText("inventories.map-voting.title", null, null, null);
        title = title.replace("%amount%", VotePlayer.getMaxVotes(player) + "");
        int size = voteManager.getVotes().size();
        inv = Bukkit.createInventory(null, (int) Math.ceil((double) size / 9) * 9, ConfigUtils.toComponent(title));
        manager.registerInventory(player, this);
        
        // Refresh inventory once in a while
        new BukkitRunnable() {
            @Override
            public void run() {
                if (manager.getInventory(player) != null) {
                    updateInventory();
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(MissileWarsPlugin.getPlugin(), 20, 20);
    }
    
    // Register all maps and their votes to items
    @Override
    public void updateInventory() {
        inv.clear();
        ConfigurationSection sec = ConfigUtils.getConfigFile("messages.yml")
                .getConfigurationSection("inventories.map-voting");
        for (String mapName : voteManager.getVotes().keySet()) {
            boolean available = arena.isAvailable(mapName);
            String path = available ? "item" : "item-unavailable";
            int votes = voteManager.getVotes().get(mapName);
            
            // Set item, add name
            ItemStack mapItem = new ItemStack(Material.valueOf(sec.getString(path + ".item")));
            ItemMeta mapItemMeta = mapItem.getItemMeta();
            String display = ConfigUtils.getMapText(arena.getGamemode(), mapName, "name");
            mapItemMeta.displayName(ConfigUtils.toComponent(display));

            // Add lore
            List<Component> lore = new ArrayList<>();
            int req = ArenaUtils.getRankRequirement(arena.getGamemode(), mapName);
            if (req > 0) {
                String reqStr = sec.getString("req");
                reqStr = reqStr.replace("%rank%", RankUtils.getRankNameFromLevel(req));
                lore.add(ConfigUtils.toComponent(reqStr));
            }
            for (String s : sec.getStringList(path + ".lore")) {
                String sn = s.replaceAll("%umw_map_votes%", "" + votes);
                lore.add(ConfigUtils.toComponent(sn));
            }
            mapItemMeta.lore(lore);
            
            if (available) {
                mapItem.setAmount(Math.min(Math.max(votes, 1), 64));
                mapItemMeta.getPersistentDataContainer().set(mapKey, PersistentDataType.STRING, mapName);
                if (votes > 0) {
                    mapItemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    mapItemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                }
            }
            
            mapItem.setItemMeta(mapItemMeta);
            inv.addItem(mapItem);
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    @Override
    public void registerClick(int slot, ClickType type) {
        ItemStack clicked = inv.getItem(slot);
        if (clicked == null) {
            return;
        }
        
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        
        // Get meta
        ItemMeta meta = clicked.getItemMeta();
        if (!meta.getPersistentDataContainer().has(mapKey)) {
            return;
        }
        
        // Get map name
        String map = meta.getPersistentDataContainer().get(mapKey, PersistentDataType.STRING);
        voteManager.registerVote(player, map, type == ClickType.RIGHT);
        ConfigUtils.sendConfigSound("use-skillpoint", player);
        updateInventory();
    }
}
