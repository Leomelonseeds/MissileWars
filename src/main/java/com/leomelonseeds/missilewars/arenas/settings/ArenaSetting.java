package com.leomelonseeds.missilewars.arenas.settings;

import java.util.Map;

import org.bukkit.Difficulty;

import com.leomelonseeds.missilewars.MissileWarsPlugin;

public enum ArenaSetting {
    
    CAPACITY(20, "capacity", IntSettingModifier.create(2, 20, 2)),
    TIE_TIMER(5, "tie-timer", IntSettingModifier.create(0, 10, 1)),
    ITEM_MULTIPLIER(100, "item-multiplier", IntSettingModifier.create(50, 300, 10)), // As a percentage
    RANDOM_ITEM_DISTRIBUTION_TIMER(12, "random-item-distribution-timer", IntSettingModifier.create(5, 30, 1)),
    START_TIMER(30, "start-timer", IntSettingModifier.create(10, 30, 1)),
    ENABLE_MISSILE_COOLDOWN(true, "enable-missile-cooldown"),
    ENABLE_SIDEWAYS_MISSILES(false, "enable-sideways-missiles"),
    ENABLE_AIR_PLACE(false, "enable-air-place"),
    IS_INFINITE_TIME(false, "is-infinite-time"),
    ENABLE_DECREASING_ITEM_TIMERS(true, "enable-decreasing-item-timers"),
    ENABLE_RANDOM_ITEM_DISTRIBUTION(false, "enable-random-item-distribution"),
    ENABLE_MULTIPLE_PORTALS(true, "enable-multiple-portals"),
    ENABLE_TEAM_BALANCING(true, "enable-team-balancing"),
    ENABLE_UNFAIR_TEAMS(false, "enable-unfair-teams"),
    END_IF_NO_PLAYERS(true, "end-if-no-players"),
    ENABLE_AUTO_START(true, "enable-auto-start"),
    ONLY_JOIN_QUEUED_PLAYERS(false, "only-join-queued-players"),
    IS_PRIVATE(false, "is-private"),
    IS_ALWAYS_ONLINE(true, "always-online"),
    OWNER_NAME("", "owner-name"),
    OWNER_UUID(MissileWarsPlugin.zeroUUID, "owner-uuid"),
    WORLD_DIFFICULTY(Difficulty.EASY, "world-difficulty"),
    PRIORITY(1, "priority"); // Sorting priority - higher numbers sorted first
    
    private Object defaultValue;
    private String id;
    private IntSettingModifier intModifier;
    
    /**
     * ArenaSetting enum for primitive setting types like boolean and String
     * 
     * @param defaultValue
     * @param id
     */
    private ArenaSetting(Object defaultValue, String id) {
        this.defaultValue = defaultValue;
        this.id = id;
    }

    private ArenaSetting(Object defaultValue, String id, IntSettingModifier intModifier) {
        this.defaultValue = defaultValue;
        this.id = id;
        this.intModifier = intModifier;
    }
    
    /**
     * Adds a setting to the given map if it's not the default value
     * 
     * @param settings
     */
    public void serialize(Map<String, Object> settings, Object value) {
        if (defaultValue.equals(value)) {
            return;
        }
        
        settings.put(id, value);
    }
    
    public void deserialize(Map<String, Object> storedSettings, Map<ArenaSetting, Object> settings, ArenaSetting setting) {
        if (!storedSettings.containsKey(id)) {
            return;
        }
        
        settings.put(setting, storedSettings.get(id));
    }
    
    public Object getDefaultValue() {
        return defaultValue;
    }
    
    public String getId() {
        return id;
    }
    
    public IntSettingModifier getIntModifier() {
        return intModifier;
    }
}
