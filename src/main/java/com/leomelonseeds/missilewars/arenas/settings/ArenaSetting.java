package com.leomelonseeds.missilewars.arenas.settings;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Difficulty;
import org.bukkit.entity.Player;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.decks.DeckStorage;

public enum ArenaSetting {
    
    // VISIBILITY SETTINGS
    CAPACITY(20, "capacity", IntSettingModifier.create(2, 20, 1)),
    IS_PRIVATE(false, "is-private"),
    IS_ALWAYS_ONLINE(true, "always-online", true),
    
    // ITEM SETTINGS
    ITEM_MULTIPLIER(100, "item-multiplier", IntSettingModifier.create(50, 300, 10)), // As a percentage
    RANDOM_ITEM_DISTRIBUTION_TIMER(12, "random-item-distribution-timer", IntSettingModifier.create(5, 30, 1)),
    ENABLE_DECREASING_ITEM_TIMERS(true, "enable-decreasing-item-timers"),
    ENABLE_RANDOM_ITEM_DISTRIBUTION(false, "enable-random-item-distribution"),
    ENABLE_TEAM_BALANCING(true, "enable-team-balancing"),
    
    // MISC SETTINGS:
    // Game management
    ENABLE_AUTO_START(true, "enable-auto-start"),
    START_TIMER(30, "start-timer", IntSettingModifier.create(10, 30, 1)),
    TIE_TIMER(5, "tie-timer", IntSettingModifier.create(0, 10, 1)),
    IS_INFINITE_TIME(false, "is-infinite-time"),
    END_IF_NO_PLAYERS(true, "end-if-no-players"),
    
    // Player/team management
    ENABLE_UNFAIR_TEAMS(false, "enable-unfair-teams"),
    ONLY_JOIN_QUEUED_PLAYERS(false, "only-join-queued-players"),
    ENABLE_AFK_KICK(true, "enable-afk-kick"),
    
    // Game rules and modifiers
    ENABLE_SIDEWAYS_MISSILES(false, "enable-sideways-missiles"),
    ENABLE_AIR_PLACE(false, "enable-air-place"),
    ENABLE_MISSILE_COOLDOWN(true, "enable-missile-cooldown"),
    ENABLE_ALTITUDE_SICKNESS(true, "enable-altitude-sickness"),
    ENABLE_MULTIPLE_PORTALS(true, "enable-multiple-portals"),
    WORLD_DIFFICULTY(Difficulty.EASY, "world-difficulty", false, true),
    ENABLE_FORCE_DECK(false, "force-deck"),
    FORCED_DECK(DeckStorage.SENTINEL, "force-deck", false, true),
    
    // INTERNAL SETTINGS (not editable by players)
    OWNER_NAME("", "owner-name"),
    OWNER_UUID(MissileWarsPlugin.zeroUUID, "owner-uuid", false, true),
    MAPS_EDITED(false, "maps-edited"), // If false, display every map available in the map selector
    PRIORITY(1, "priority"); // Sorting priority - higher numbers sorted first
    
    private Object defaultValue;
    private String id;
    private IntSettingModifier intModifier;
    private boolean storeAsString;
    private boolean needsPermission;
    
    /**
     * ArenaSetting enum for primitive setting types like boolean and String
     * 
     * @param defaultValue
     * @param id
     */
    private ArenaSetting(Object defaultValue, String id) {
        this(defaultValue, id, false, false, null);
    }

    private ArenaSetting(Object defaultValue, String id, IntSettingModifier intModifier) {
        this(defaultValue, id, false, false, intModifier);
    }
    
    private ArenaSetting(Object defaultValue, String id, boolean needsPermission) {
        this(defaultValue, id, needsPermission, false, null);
    }

    private ArenaSetting(Object defaultValue, String id, boolean needsPermission, boolean storeAsString) {
        this(defaultValue, id, needsPermission, storeAsString, null);
    }

    
    private ArenaSetting(Object defaultValue, String id, boolean needsPermission, boolean storeAsString, IntSettingModifier intModifier) {
        this.defaultValue = defaultValue;
        this.id = id;
        this.needsPermission = needsPermission;
        this.intModifier = intModifier;
        this.storeAsString = storeAsString;
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
        
        if (storeAsString) {
            value = value.toString();
        }
        
        settings.put(id, value);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void deserialize(Map<String, Object> storedSettings, Map<ArenaSetting, Object> settings) {
        if (!storedSettings.containsKey(id)) {
            return;
        }
        
        Object value = storedSettings.get(id);
        if (defaultValue instanceof UUID) {
            value = UUID.fromString((String) value);
        } else if (defaultValue.getClass().isEnum()) {
            value = Enum.valueOf((Class) defaultValue.getClass(), (String) value); // WTF
        }
        
        settings.put(this, value);
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
    
    public boolean hasPermission(Player player) {
        if (!needsPermission) {
            return true;
        }
        
        return player.hasPermission("umw.customarena.setting." + toString().toLowerCase());
    }
    
    public boolean needsPermission() {
        return needsPermission;
    }
}
