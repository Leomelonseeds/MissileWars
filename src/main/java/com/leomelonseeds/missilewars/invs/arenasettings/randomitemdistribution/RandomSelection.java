package com.leomelonseeds.missilewars.invs.arenasettings.randomitemdistribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.settings.IntSettingModifier;
import com.leomelonseeds.missilewars.arenas.settings.RandomItem;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemDistributor;
import com.leomelonseeds.missilewars.arenas.settings.RandomItemSetting.RandomSelectionSetting;
import com.leomelonseeds.missilewars.invs.ConfirmAction;
import com.leomelonseeds.missilewars.invs.MWInventory;
import com.leomelonseeds.missilewars.invs.pagination.PaginatedInventory;
import com.leomelonseeds.missilewars.utilities.ArenaUtils;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public class RandomSelection extends PaginatedInventory {
    
    private static final String sec = "arena-settings.random-item-distribution.random-selection";
    private static final Map<Integer, RandomSelectionSetting> settingSlots = Map.of(
        46, RandomSelectionSetting.SET_MISSILE_COUNT,
        47, RandomSelectionSetting.SET_UTILITY_COUNT
    );
    
    private RandomItemDistributor distributor;
    private MWInventory fromInv;
    private boolean set;
    private int addedMissileCount;
    private int addedUtilityCount;
    private int missileCount;
    private int utilityCount;
    private HashMap<String, Integer> allMissiles; 
    private HashMap<String, Integer> allUtility;
    private Set<String> curItems;
    private FileConfiguration itemsConfig;
    private ConfigurationSection settingConfig;
    private Random random;

    public RandomSelection(Player player, RandomItemDistributor distributor, MWInventory fromInv) {
        super(player, 54, "Get Random Selection");
        this.distributor = distributor;
        this.fromInv = fromInv;
        this.missileCount = 5;
        this.utilityCount = 3;
        this.allMissiles = new HashMap<>();
        this.allUtility = new HashMap<>();
        this.curItems = new HashSet<>();
        this.itemsConfig = ConfigUtils.getConfigFile("items.yml");
        this.settingConfig = ConfigUtils.getConfigFile("messages.yml").getConfigurationSection("settings");
        this.random = new Random();
        this.async = true;
    }
    
    private void setInitialValues() {
        // Set current missile and utility type counts
        Set<String> missiles = new HashSet<>();
        Set<String> utility = new HashSet<>();
        for (RandomItem ri : distributor.getRandomItems()) {
            String name = ri.getId().split("-")[0];
            if (InventoryUtils.isMissile(ri.getModifiableItem())) {
                missiles.add(name);
            } else {
                utility.add(name);
            }
        }

        addedMissileCount = missiles.size();
        addedUtilityCount = utility.size();
        
        // Generate map with all missiles and utility max levels
        for (String id : itemsConfig.getStringList("random-items")) {
            String[] args = id.split("-");
            String name = args[0];
            int level = Integer.parseInt(args[1]);
            HashMap<String, Integer> toAdd = ConfigUtils.getItemValue(name, 1, "speed") != null ? allMissiles : allUtility;
            toAdd.put(name, Math.max(toAdd.getOrDefault(name, 0), level));
        }
        
        set = true;
    }

    @Override
    protected List<ItemStack> getPaginatedItems() {
        if (!set) {
            setInitialValues();
            set = true;
        }
        
        List<String> missiles = new ArrayList<>(allMissiles.keySet());
        List<String> utility = new ArrayList<>(allUtility.keySet());
        Collections.shuffle(missiles);
        Collections.shuffle(utility);
        for (int i = missiles.size() - 1; i >= missileCount; i--) {
            missiles.remove(i);
        }
        
        for (int i = utility.size() - 1; i >= utilityCount; i--) {
            utility.remove(i);
        }
        
        if (!utility.contains("arrows")) {
            utility.set(utility.size() - 1, "arrows");
        }
        
        List<ItemStack> res = new ArrayList<>();
        curItems.clear();
        missiles.addAll(utility);
        for (String name : missiles) {
            int level = random.nextInt(allMissiles.containsKey(name) ? allMissiles.get(name) : allUtility.get(name)) + 1;
            res.add(MissileWarsPlugin.getPlugin().getDeckManager().createRandomItem(name, level));
            curItems.add(name + "-" + level);
        }
        return res;
    }

    @Override
    protected void updateNonPaginatedSlots() {
        for (Entry<Integer, RandomSelectionSetting> e : settingSlots.entrySet()) {
            inv.setItem(e.getKey(), createSettingsItem(e.getValue()));
        }
        
        for (String key : itemsConfig.getConfigurationSection(sec).getKeys(false)) {
            ItemStack item = InventoryUtils.createItem(sec + "." + key);
            InventoryUtils.setMetaString(item, InventoryUtils.ITEM_GUI_KEY, key);
            inv.setItem(itemsConfig.getInt(sec + "." + key + ".slot"), item);
        }
    }

    @Override
    protected void registerPaginatedClick(int slot, ClickType type, ItemStack item) {
        if (item.equals(InventoryUtils.getBackItem())) {
            manager.registerInventory(player, fromInv);
            return;
        }
        
        if (settingSlots.containsKey(slot)) {
            RandomSelectionSetting setting = settingSlots.get(slot);
            if (type.isShiftClick()) {
                ArenaUtils.manualIntSetting(setting, getModifier(setting), player, this, val -> applySetting(setting, val, slot));
                return;
            }
            
            String valueString = InventoryUtils.getStringFromItemKey(item, InventoryUtils.SETTING_VALUE_KEY);
            String value = ArenaUtils.parseIntSetting(valueString, type, player);
            if (value == null) {
                return;
            }
            
            applySetting(setting, Integer.parseInt(value), slot);
            return;
        }
        
        String key = InventoryUtils.getGUIFromItem(item);
        if (key == null) {
            return;
        }
        
        if (key.equals("set-items")) {
            new ConfirmAction("Override Items", player, this, res -> {
                if (!res) {
                    return;
                }

                distributor.clearItems();
                curItems.forEach(id -> distributor.addItem(new RandomItem(id)));
                manager.registerInventory(player, fromInv);
                ConfigUtils.sendConfigSound("purchase-item", player);
            });
            return;
        }
        
        if (key.equals("reshuffle")) {
            updateInventoryAsync();
            ConfigUtils.sendConfigSound("shuffle", player);
            return;
        }
    }
    
    private void applySetting(RandomSelectionSetting setting, int value, int slot) {
        if (setting == RandomSelectionSetting.SET_MISSILE_COUNT) {
            missileCount = value;
        } else {
            utilityCount = value;
        }
        
        inv.setItem(slot, createSettingsItem(setting));
        ConfigUtils.sendConfigSound("use-skillpoint", player);
    }
    
    /**
     * Creates a clickable item to edit a random item setting
     * I don't care I'm going to copy code from arena settings
     * Here assume the setting is always INT
     * 
     * @param setting
     * @return
     */
    private ItemStack createSettingsItem(RandomSelectionSetting setting) {
        // Get section info
        String settingString = setting.toString();
        ConfigurationSection sec = settingConfig.getConfigurationSection("format.int");
        
        // Create item and set name
        String material = settingConfig.getString("random-selection-settings." + settingString + ".item");
        ItemStack item = new ItemStack(Material.valueOf(material));
        ItemMeta meta = item.getItemMeta();
        String name = sec.getString("color") + ConfigUtils.getEnumDisplayString(settingString);
        meta.displayName(ConfigUtils.toComponent(name));
        
        // Add int specific lore
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(settingConfig.getStringList("random-selection-settings." + settingString + ".description"));
        
        // Add specific lores and metadata for each item
        IntSettingModifier intModifier = getModifier(setting);
        int cur = setting == RandomSelectionSetting.SET_MISSILE_COUNT ? missileCount : utilityCount;
        int actualCur = setting == RandomSelectionSetting.SET_MISSILE_COUNT ? addedMissileCount : addedUtilityCount;
        Integer left = cur <= intModifier.getMin() ? null : Math.max(cur - intModifier.getChange(), intModifier.getMin());
        Integer right = cur >= intModifier.getMax() ? null : Math.min(cur + intModifier.getChange(), intModifier.getMax());
        String unit = settingConfig.getString("random-selection-settings." + settingString + ".unit").replace("(s)", cur == 1 ? "" : "s");
        if (cur == 1 && unit.endsWith("utilities")) {
            unit = " utility";
        }
        for (String line : sec.getStringList("lore")) {
            if (line.isEmpty()) {
                lore.add(line);
                continue;
            }
            
            lore.add(line.replace("%left-value%", left == null ? "" : left + "")
                         .replace("%left-arrow%", left == null ? "" : sec.getString("left-arrow"))
                         .replace("%right-value%", right == null ? "" : right + "")
                         .replace("%right-arrow%", right == null ? "" : sec.getString("right-arrow"))
                         .replace("%value%", cur + "")
                         .replace("%unit%", unit)
                         .replace("%default%", actualCur + "")
                         .replace("Default", "Added types"));
        }
        
        if (right != null) {
            lore.add(sec.getString("lore-increasable"));
        }
        
        if (left != null) {
            lore.add(sec.getString("lore-decreasable"));
        }
        
        lore.add(sec.getString("lore-manual"));
        
        meta.lore(ConfigUtils.toComponent(lore));
        
        InventoryUtils.setMetaString(meta, InventoryUtils.SETTING_VALUE_KEY, left + "," + right);

        meta.setMaxStackSize(99);
        
        item.setItemMeta(meta);
        
        item.setAmount(cur == 0 ? 64 : cur);
        
        return item;
    }
    
    private IntSettingModifier getModifier(RandomSelectionSetting setting) {
        return setting == RandomSelectionSetting.SET_MISSILE_COUNT ?
            IntSettingModifier.create(1, allMissiles.size(), 1) : 
            IntSettingModifier.create(1, allUtility.size(), 1);
    }
}
