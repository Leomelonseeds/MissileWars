package com.leomelonseeds.missilewars.arenas.settings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

public class ArenaSettings implements ConfigurationSerializable {
    
    private int capacity;
    private boolean isPrivate;
    private Set<UUID> playerBlacklist;
    private Set<UUID> playerWhitelist;
    private boolean disableMissileCooldown;
    private boolean enableSidewaysMissiles;
    private boolean enableAirPlace;
    private String forcedMap;
    private boolean isInfiniteTime;
    private boolean disableDecreasingItemTimers;
    private boolean enableRandomItemDistribution;
    
    public ArenaSettings() {
        this.playerBlacklist = new HashSet<>();
        this.playerWhitelist = new HashSet<>();
        this.forcedMap = "";
    }
    
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> settings = new HashMap<>();
        
        // Int/String settings
        saveSetting(settings, "capacity", capacity, 20);
        saveSetting(settings, "forced-map", forcedMap, "");
        
        // Boolean settings
        saveBoolean(settings, "is-private", isPrivate);
        saveBoolean(settings, "disable-missile-cooldown", disableMissileCooldown);
        saveBoolean(settings, "enable-sideways-missiles", enableSidewaysMissiles);
        saveBoolean(settings, "enable-air-place", enableAirPlace);
        saveBoolean(settings, "is-infinite-time", isInfiniteTime);
        saveBoolean(settings, "disable-decreasing-item-timers", disableDecreasingItemTimers);
        saveBoolean(settings, "enable-random-item-distribution", enableRandomItemDistribution);
        
        // List settings
        if (!playerBlacklist.isEmpty()) {
            settings.put("player-blacklist", String.join(",", playerBlacklist.stream().map(uuid -> uuid.toString()).toList()));
        }
        
        if (!playerWhitelist.isEmpty()) {
            settings.put("player-whitelist", String.join(",", playerWhitelist.stream().map(uuid -> uuid.toString()).toList()));
        }
        return settings;
    }

//  public ArenaSettings(Map<String, Object> settings) {
//      plugin = MissileWarsPlugin.getPlugin();
//      name = (String) serializedArena.get("name");
//      capacity = (int) serializedArena.get("capacity");
//      gamemode = (String) serializedArena.get("gamemode");
//      npcs = new ArrayList<>();
//      String npcIDs = (String) serializedArena.get("npc");
//      for (String s : npcIDs.split(",")) {
//          npcs.add(Integer.parseInt(s));
//      }
//      init();
//  }
    
    public Set<UUID> getPlayerBlacklist() {
        return playerBlacklist;
    }

    public void setPlayerBlacklist(Set<UUID> playerBlacklist) {
        this.playerBlacklist = playerBlacklist;
    }

    public Set<UUID> getPlayerWhitelist() {
        return playerWhitelist;
    }

    public void setPlayerWhitelist(Set<UUID> playerWhitelist) {
        this.playerWhitelist = playerWhitelist;
    }

    public boolean isEnableRandomItemDistribution() {
        return enableRandomItemDistribution;
    }

    public void setEnableRandomItemDistribution(boolean enableRandomItemDistribution) {
        this.enableRandomItemDistribution = enableRandomItemDistribution;
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

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
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
 
}
