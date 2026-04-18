package com.leomelonseeds.missilewars.arenas.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import com.leomelonseeds.missilewars.utilities.ConfigUtils;

public class ArenaSettings implements ConfigurationSerializable {
    
    private Map<ArenaSetting, Object> currentSettings;
    private Map<ArenaSetting, Object> queue;
    private RandomItemDistributor randomItemDistributor;
    private List<UUID> playerBlacklist;
    private List<UUID> playerWhitelist;
    private List<String> selectedMaps;
    
    public ArenaSettings() {
        this.currentSettings = new HashMap<>();
        this.queue = new HashMap<>();
        this.randomItemDistributor = new RandomItemDistributor();
        this.playerBlacklist = new ArrayList<>();
        this.playerWhitelist = new ArrayList<>();
        this.selectedMaps = new ArrayList<>();
    }
    
    /**
     * Creates a full copy of the arena settings
     * 
     * @param other
     */
    public ArenaSettings(ArenaSettings other) {
        this.currentSettings = new HashMap<>(other.currentSettings);
        this.queue = new HashMap<>();
        this.playerBlacklist = new ArrayList<>(other.playerBlacklist);
        this.playerWhitelist = new ArrayList<>(other.playerWhitelist);
        this.selectedMaps = new ArrayList<>(other.selectedMaps);
        this.randomItemDistributor = other.randomItemDistributor.clone();
    }
    
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> settings = new HashMap<>();
        
        for (ArenaSetting setting : ArenaSetting.values()) {
            if (!currentSettings.containsKey(setting)) {
                continue;
            }
            
            setting.serialize(settings, currentSettings.get(setting));
        }
        
        // List settings
        if (!playerBlacklist.isEmpty()) {
            settings.put("player-blacklist", String.join(",", playerBlacklist.stream().map(uuid -> uuid.toString()).toList()));
        }
        
        if (!playerWhitelist.isEmpty()) {
            settings.put("player-whitelist", String.join(",", playerWhitelist.stream().map(uuid -> uuid.toString()).toList()));
        }
        
        if (!selectedMaps.isEmpty()) {
            settings.put("selected-maps", selectedMaps);
        }
        
        // Item distributor
        if ((boolean) get(ArenaSetting.ENABLE_RANDOM_ITEM_DISTRIBUTION)) {
            settings.put("random-item-distributor", randomItemDistributor);
        }
        
        return settings;
    }

    @SuppressWarnings("unchecked")
    public ArenaSettings(Map<String, Object> settings) {
        this.currentSettings = new HashMap<>();
        for (ArenaSetting setting : ArenaSetting.values()) {
            setting.deserialize(settings, currentSettings);
        }
        
        if (settings.containsKey("player-blacklist")) {
            String uuids = (String) settings.get("player-blacklist");
            List<UUID> uuidList = Arrays.asList(uuids.split(",")).stream().map(str -> UUID.fromString(str)).toList();
            playerWhitelist = new ArrayList<>(uuidList);
        } else {
            playerWhitelist = new ArrayList<>();
        }
        
        // Duplicated code is okay actually
        if (settings.containsKey("player-whitelist")) {
            String uuids = (String) settings.get("player-whitelist");
            List<UUID> uuidList = Arrays.asList(uuids.split(",")).stream().map(str -> UUID.fromString(str)).toList();
            playerBlacklist = new ArrayList<>(uuidList);
        } else {
            playerBlacklist = new ArrayList<>();
        }
        
        if (settings.containsKey("selected-maps")) {
            selectedMaps = (List<String>) settings.get("selected-maps");
        } else {
            selectedMaps = new ArrayList<>();
        }
        
        if (settings.containsKey("random-item-distributor")) {
            randomItemDistributor = (RandomItemDistributor) settings.get("random-item-distributor");
        }
        
        this.queue = new HashMap<>();
    }
    
    public Object get(ArenaSetting setting) {
        // What a funny statement
        return queue.getOrDefault(setting,
            currentSettings.getOrDefault(setting, setting.getDefaultValue()));
    }
    
    public boolean isQueued(ArenaSetting setting) {
        return queue.containsKey(setting);
    }
    
    /**
     * Queue a setting to be set.
     * 
     * @param setting
     * @param value
     * @return
     */
    public boolean set(ArenaSetting setting, String value) {
        FileConfiguration messageConfig = ConfigUtils.getConfigFile("messages.yml");
        String settingType = messageConfig.getString("settings.settings." + setting.toString() + ".type");
        return set(setting, value, settingType, true);
    }
    
    /**
     * Parse a string to set arena setting to value.
     * 
     * @param setting
     * @param value
     * @param type either boolean/int/enum (make sure it's right)
     * @boolean queue whether the setting should be queue and applied by using flush()
     * @return if the value is valid for the setting
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public boolean set(ArenaSetting setting, String value, String type, boolean queue) {
        Object valueObject = null;
        try {
            if (type.equals("int")) {
                valueObject = Integer.parseInt(value);
            } else if (type.equals("enum")) {
                valueObject = Enum.valueOf((Class) setting.getDefaultValue().getClass(), value);
            } else {
                valueObject = Boolean.valueOf(value);
            }
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().warning("Failed to set setting " + setting.toString() + " to: " + value);
            return false;
        }
        
        if (queue) {
            set(setting, valueObject);
        } else {
            queue(setting, valueObject);
        }
        return true;
    }
    
    /**
     * Set a setting immediately
     * 
     * @param setting
     * @param value
     */
    public void set(ArenaSetting setting, Object value) {
        if (setting.getDefaultValue().equals(value)) {
            currentSettings.remove(setting);
        } else {
            currentSettings.put(setting, value);
        }
    }
    
    /**
     * Queue a setting to be applied. Use flush() to apply
     * all queued settings
     * 
     * @param setting
     * @param value
     */
    public void queue(ArenaSetting setting, Object value) {
        if (get(setting).equals(value)) {
            queue.remove(setting);
            return;
        }
        
        queue.put(setting, value);
    }
    
    /**
     * Apply all queued settings
     * 
     * @return a map of applied settings, L = old value, R = new value
     */
    public Map<ArenaSetting, Pair<Object, Object>> flush() {
        Map<ArenaSetting, Pair<Object, Object>> res = new HashMap<>();
        for (ArenaSetting setting : queue.keySet()) {
            Object value = queue.get(setting);
            set(setting, value);
            res.put(setting, Pair.of(get(setting), value));
        }
        
        queue.clear();
        return res;
    }
    
    public boolean isWhitelisted(UUID uuid) {
        return playerWhitelist.contains(uuid);
    }
    
    public boolean isBlacklisted(UUID uuid) {
        return playerBlacklist.contains(uuid);
    }
    
    public void addToWhitelist(UUID uuid) {
        playerWhitelist.add(uuid);
    }
    
    public void removeFromWhitelist(UUID uuid) {
        playerWhitelist.remove(uuid);
    }
    
    public void clearWhitelist() {
        playerWhitelist.clear();
    }
    
    public void addToBlacklist(UUID uuid) {
        playerBlacklist.add(uuid);
    }
    
    public void removeFromBlacklist(UUID uuid) {
        playerBlacklist.remove(uuid);
    }
    
    public void clearBlacklist() {
        playerBlacklist.clear();
    }
    
    /**
     * @return a MUTABLE list of selected maps
     */
    public List<String> getSelectedMaps() {
        return selectedMaps;
    }
    
    public RandomItemDistributor getRandomItemDistributor() {
        return randomItemDistributor;
    }
}
