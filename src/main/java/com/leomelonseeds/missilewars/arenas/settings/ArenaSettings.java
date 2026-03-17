package com.leomelonseeds.missilewars.arenas.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

public class ArenaSettings implements ConfigurationSerializable {
    
    private Map<ArenaSetting, Object> currentSettings;
    private RandomItemDistributor randomItemDistributor;
    private List<UUID> playerBlacklist;
    private List<UUID> playerWhitelist;
    private List<String> selectedMaps;
    
    public ArenaSettings() {
        this.currentSettings = new HashMap<>();
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
            setting.deserialize(settings, currentSettings, setting);
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
    }
    
    public Object get(ArenaSetting setting) {
        return currentSettings.getOrDefault(setting, setting.getDefaultValue());
    }
    
    public void set(ArenaSetting setting, Object value) {
        if (setting.getDefaultValue().equals(value)) {
            currentSettings.remove(setting);
        } else {
            currentSettings.put(setting, value);
        }
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
