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
    // Global
    ENABLE_RANDOM_ITEM_DISTRIBUTION(false, "enable-random-item-distribution"),
    ENABLE_DECREASING_COOLDOWNS(true, "enable-decreasing-cooldowns"),
    ENABLE_TEAM_BALANCING(true, "enable-team-balancing"),
    ENABLE_DROPPING_ITEMS(true, "enable-dropping-items"), // TODO
    FIREBALLS_NEED_TO_BE_PLACED(false, "fireballs-need-to-be-placed"),
    FIREBALL_EXPLOSION_DAMAGES_PLAYERS(false, "fireball-explosion-damages-players"), // TODO
    ENABLE_SIDEWAYS_MISSILES(false, "enable-sideways-missiles"), // TODO
    ENABLE_AIR_PLACE(false, "enable-air-place"), // TODO
    MISSILE_OFFSET_MODIFIER_Y(0, "missile-offset-modifier-y"), // TODO
    MISSILE_OFFSET_MODIFIER_Z(0, "missile-offset-modifier-z"), // TODO
    
    // Deck
    DECK_ITEM_MULTIPLIER(100, "deck-item-multiplier", IntSettingModifier.create(20, 200, 10)), // As a percentage
    ENABLE_FORCE_DECK(false, "force-deck"), // TODO
    FORCED_DECK(DeckStorage.SENTINEL, "force-deck", false, true), // TODO
    
    // Random
    RANDOM_ITEM_DISTRIBUTION_TIMER(12, "random-item-distribution-timer", IntSettingModifier.create(5, 30, 1)),
    RANDOM_ITEM_BAG_DISTRIBUTION(false, "random-item-bag-distribution"),
    RANDOM_ITEM_XP_TIMER(true, "random-item-xp-timer"),
    RANDOM_ITEM_INVENTORY_LIMIT(0, "random-item-inventory-limit", IntSettingModifier.create(0, 32, 1)),
    START_WITH_MISSILE(true, "start-with-missile"), // TODO
    
    // MISC SETTINGS:
    // Game management
    ENABLE_AUTO_START(true, "enable-auto-start"),
    START_TIMER(30, "start-timer", IntSettingModifier.create(5, 30, 5)),
    TIE_TIMER(5, "tie-timer", IntSettingModifier.create(0, 10, 1)),
    IS_INFINITE_TIME(false, "is-infinite-time"),
    END_IF_NO_PLAYERS(true, "end-if-no-players"),
    
    // Player/team management
    ENABLE_UNFAIR_TEAMS(false, "enable-unfair-teams"),
    ONLY_JOIN_QUEUED_PLAYERS(false, "only-join-queued-players"),
    ENABLE_AFK_KICK(true, "enable-afk-kick"),
    ALLOW_JOINING_ONGOING_GAMES(true, "allow-joining-ongoing-games"), // TODO
    
    // Game rules and modifiers
    ENABLE_MISSILE_COOLDOWN(true, "enable-missile-cooldown"),
    ENABLE_ALTITUDE_SICKNESS(true, "enable-altitude-sickness"),
    ENABLE_MULTIPLE_PORTALS(true, "enable-multiple-portals"), // TODO
    ENABLE_TEAMGRIEF_PREVENTION(true, "enable-teamgrief-prevention"),
    WORLD_DIFFICULTY(Difficulty.EASY, "world-difficulty", false, true),
    
    // INTERNAL SETTINGS (not editable by players)
    OWNER_NAME("", "owner-name"),
    OWNER_UUID(MissileWarsPlugin.zeroUUID, "owner-uuid", false, true),
    DISTRIBUTOR_PRESET(0, "distributor-preset"),
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
