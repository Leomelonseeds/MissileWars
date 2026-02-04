package com.leomelonseeds.missilewars.arenas.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

public class ArenaSettings implements ConfigurationSerializable {
    
    private static final int DEFAULT_CAPACITY = 20;
    private static final int DEFAULT_TIE_TIMER = 5;
    private static final String DEFAULT_MAP = "";
    
    private RandomItemDistributor randomItemDistributor;
    private int capacity;
    private int tieTimer;
    private boolean isPrivate;
    private List<UUID> playerBlacklist;
    private List<UUID> playerWhitelist;
    private boolean disableMissileCooldown;
    private boolean enableSidewaysMissiles;
    private boolean enableAirPlace;
    private String forcedMap;
    private boolean isInfiniteTime;
    private boolean disableDecreasingItemTimers;
    private boolean enableRandomItemDistribution;
    private boolean disableMultiplePortals;
    private boolean disableTeamBalancing;
    
    public ArenaSettings() {
        this.randomItemDistributor = new RandomItemDistributor();
        this.playerBlacklist = new ArrayList<>();
        this.playerWhitelist = new ArrayList<>();
        this.forcedMap = "";
        this.capacity = DEFAULT_CAPACITY;
        this.tieTimer = DEFAULT_TIE_TIMER;
    }
    
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> settings = new HashMap<>();
        
        // Int/String settings
        saveSetting(settings, "capacity", capacity, DEFAULT_CAPACITY);
        saveSetting(settings, "tie-timer", tieTimer, DEFAULT_TIE_TIMER);
        saveSetting(settings, "forced-map", forcedMap, DEFAULT_MAP);
        
        // Boolean settings
        saveBoolean(settings, "is-private", isPrivate);
        saveBoolean(settings, "disable-missile-cooldown", disableMissileCooldown);
        saveBoolean(settings, "enable-sideways-missiles", enableSidewaysMissiles);
        saveBoolean(settings, "enable-air-place", enableAirPlace);
        saveBoolean(settings, "is-infinite-time", isInfiniteTime);
        saveBoolean(settings, "disable-decreasing-item-timers", disableDecreasingItemTimers);
        saveBoolean(settings, "enable-random-item-distribution", enableRandomItemDistribution);
        saveBoolean(settings, "disable-multiple-portals", disableMultiplePortals);
        saveBoolean(settings, "disable-team-balancing", disableTeamBalancing);
        
        // List settings
        if (!playerBlacklist.isEmpty()) {
            settings.put("player-blacklist", String.join(",", playerBlacklist.stream().map(uuid -> uuid.toString()).toList()));
        }
        
        if (!playerWhitelist.isEmpty()) {
            settings.put("player-whitelist", String.join(",", playerWhitelist.stream().map(uuid -> uuid.toString()).toList()));
        }
        
        // Item distributor
        if (enableRandomItemDistribution) {
            settings.put("random-item-distributor", randomItemDistributor.serialize());
        }
        return settings;
    }

    public ArenaSettings(Map<String, Object> settings) {
    }
    
    private void loadSetting(String id, Object defaultValue, Map<String, Object> settings, Consumer<Object> setter) {
        if (!settings.containsKey(id)) {
            return;
        }
        
        Object value = settings.get(id);
        setter.accept(value);
    }

    // Only saves boolean setting if it's non-default
    private void saveBoolean(Map<String, Object> settings, String name, boolean setting) {
        saveSetting(settings, name, setting, false);
    }
    
    // Only saves setting if it's non default
    private void saveSetting(Map<String, Object> settings, String name, Object setting, Object defaultValue) {
        if (setting.equals(defaultValue)) {
            return;
        }
        
        settings.put(name, setting);
    }

    public boolean isEnableRandomItemDistribution() {
        return enableRandomItemDistribution;
    }

    public void setEnableRandomItemDistribution(boolean enableRandomItemDistribution) {
        this.enableRandomItemDistribution = enableRandomItemDistribution;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getTieTimer() {
        return tieTimer;
    }

    public void setTieTimer(int tieTimer) {
        this.tieTimer = tieTimer;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public boolean isDisableMissileCooldown() {
        return disableMissileCooldown;
    }

    public void setDisableMissileCooldown(boolean disableMissileCooldown) {
        this.disableMissileCooldown = disableMissileCooldown;
    }

    public boolean isEnableSidewaysMissiles() {
        return enableSidewaysMissiles;
    }

    public void setEnableSidewaysMissiles(boolean enableSidewaysMissiles) {
        this.enableSidewaysMissiles = enableSidewaysMissiles;
    }

    public boolean isEnableAirPlace() {
        return enableAirPlace;
    }

    public void setEnableAirPlace(boolean enableAirPlace) {
        this.enableAirPlace = enableAirPlace;
    }

    public String getForcedMap() {
        return forcedMap;
    }

    public void setForcedMap(String forcedMap) {
        this.forcedMap = forcedMap;
    }

    public boolean isInfiniteTime() {
        return isInfiniteTime;
    }

    public void setInfiniteTime(boolean isInfiniteTime) {
        this.isInfiniteTime = isInfiniteTime;
    }

    public boolean isDisableDecreasingItemTimers() {
        return disableDecreasingItemTimers;
    }

    public void setDisableDecreasingItemTimers(boolean disableDecreasingItemTimers) {
        this.disableDecreasingItemTimers = disableDecreasingItemTimers;
    }

    public boolean isDisableMultiplePortals() {
        return disableMultiplePortals;
    }

    public void setDisableMultiplePortals(boolean disableMultiplePortals) {
        this.disableMultiplePortals = disableMultiplePortals;
    }

    public boolean isDisableTeamBalancing() {
        return disableTeamBalancing;
    }

    public void setDisableTeamBalancing(boolean disableTeamBalancing) {
        this.disableTeamBalancing = disableTeamBalancing;
    }
    
    // TODO: for whitelist/blacklist, clear, add, check if player is in
 
}
