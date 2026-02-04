package com.leomelonseeds.missilewars.arenas.settings;

import java.util.Map;

public enum ArenaSetting {
    
    CAPACITY(20, "capacity");
    
    private Object defaultValue;
    private String id;
    
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
    
}
