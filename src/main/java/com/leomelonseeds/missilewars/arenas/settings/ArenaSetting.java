package com.leomelonseeds.missilewars.arenas.settings;

import java.util.Map;

public enum ArenaSetting {
    
    CAPACITY(20, "capacity"),
    TIE_TIMER(5, "tie-timer"),
    ITEM_MULTIPLIER(1, "item-multiplier"),
    FORCED_MAP("", "forced-map"),
    IS_PRIVATE(false, "is-private"),
    DISABLE_MISSILE_COOLDOWN(false, "disable-missile-cooldown"),
    ENABLE_SIDEWAYS_MISSILES(false, "enable-sideways-missiles"),
    ENABLE_AIR_PLACE(false, "enable-air-place"),
    IS_INFINITE_TIME(false, "is-infinite-time"),
    DISABLE_DECREASING_ITEM_TIMERS(false, "disable-decreasing-item-timers"),
    ENABLE_RANDOM_ITEM_DISTRIBUTION(false, "enable-random-item-distribution"),
    DISABLE_MULTIPLE_PORTALS(false, "disable-multiple-portals"),
    DISABLE_TEAM_BALANCING(false, "disable-team-balancing");
    
    private Object defaultValue;
    private String id;
    
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
}
