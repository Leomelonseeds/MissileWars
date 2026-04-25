package com.leomelonseeds.missilewars.arenas.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

public class ArenaSettings implements ConfigurationSerializable {
    
    private Map<ArenaSetting, Object> currentSettings;
    private Map<ArenaSetting, Object> queue;
    private RandomItemDistributor randomItemDistributor;
    private Set<UUID> playerBlacklist;
    private Set<UUID> playerWhitelist;
    private Set<String> selectedMaps;
    
    public ArenaSettings() {
        this.currentSettings = new HashMap<>();
        this.queue = new HashMap<>();
        this.randomItemDistributor = new RandomItemDistributor();
        this.playerBlacklist = new TreeSet<>();
        this.playerWhitelist = new TreeSet<>();
        this.selectedMaps = new HashSet<>();
    }
    
    /**
     * Creates a full copy of the arena settings
     * 
     * @param other
     */
    public ArenaSettings(ArenaSettings other) {
        this.currentSettings = new HashMap<>(other.currentSettings);
        this.queue = new HashMap<>();
        this.playerBlacklist = new TreeSet<>(other.playerBlacklist);
        this.playerWhitelist = new TreeSet<>(other.playerWhitelist);
        this.selectedMaps = new HashSet<>(other.selectedMaps);
        if (other.randomItemDistributor != null) {
            this.randomItemDistributor = other.randomItemDistributor.clone();
        }
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
            settings.put("player-blacklist", playerBlacklist.stream().map(uuid -> uuid.toString()).toList());
        }
        
        if (!playerWhitelist.isEmpty()) {
            settings.put("player-whitelist", playerWhitelist.stream().map(uuid -> uuid.toString()).toList());
        }
        
        if (!selectedMaps.isEmpty()) {
            settings.put("selected-maps", new ArrayList<>(selectedMaps));
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
        
        if (settings.containsKey("player-whitelist")) {
            List<String> uuids = (List<String>) settings.get("player-whitelist");
            playerWhitelist = new TreeSet<>(uuids.stream().map(str -> UUID.fromString(str)).toList());
        } else {
            playerWhitelist = new TreeSet<>();
        }
        
        // Duplicated code is okay actually
        if (settings.containsKey("player-blacklist")) {
            List<String> uuids = (List<String>) settings.get("player-blacklist");
            playerBlacklist = new TreeSet<>(uuids.stream().map(str -> UUID.fromString(str)).toList());
        } else {
            playerBlacklist = new TreeSet<>();
        }
        
        if (settings.containsKey("selected-maps")) {
            selectedMaps = new HashSet<>((List<String>) settings.get("selected-maps"));
        } else {
            selectedMaps = new HashSet<>();
        }
        
        if (settings.containsKey("random-item-distributor")) {
            randomItemDistributor = (RandomItemDistributor) settings.get("random-item-distributor");
        }
        
        this.queue = new HashMap<>();
    }
    
    public Object get(ArenaSetting setting) {
        return queue.getOrDefault(setting, getUnqueued(setting));
    }
    
    private Object getUnqueued(ArenaSetting setting) {
        return currentSettings.getOrDefault(setting, setting.getDefaultValue());
    }
    
    public boolean isQueued(ArenaSetting setting) {
        return queue.containsKey(setting);
    }
    
    /**
     * Parse a string to set arena setting to value.
     * 
     * @param setting
     * @param value
     * @param type either boolean/int/enum (make sure it's right)
     * @param queue whether the setting should be queue and applied by using flush()
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
            queue(setting, valueObject);
        } else {
            set(setting, valueObject);
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
        if (getUnqueued(setting).equals(value)) {
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
    
    // Whitelist operations
    /**
     * @return a MUTABLE set of whitelist players
     */
    public Set<UUID> getWhitelist() {
        return playerWhitelist;
    }
    
    public boolean isWhitelisted(UUID uuid) {
        return playerWhitelist.contains(uuid);
    }
    
    // Blacklist operations
    /**
     * @return a MUTABLE set of blacklist players
     */
    public Set<UUID> getBlacklist() {
        return playerBlacklist;
    }
    
    public boolean isBlacklisted(UUID uuid) {
        return playerBlacklist.contains(uuid);
    }
    
    /**
     * @return a MUTABLE list of selected maps
     */
    public Set<String> getSelectedMaps() {
        return selectedMaps;
    }
    
    public RandomItemDistributor getRandomItemDistributor() {
        return randomItemDistributor;
    }
}
