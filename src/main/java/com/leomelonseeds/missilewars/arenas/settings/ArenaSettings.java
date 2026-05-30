package com.leomelonseeds.missilewars.arenas.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;

public class ArenaSettings implements ConfigurationSerializable {
    
    private static Comparator<UUID> comp = Comparator.comparing(uuid -> 
        Objects.requireNonNullElse(Bukkit.getOfflinePlayer(uuid).getName(), ""));
    
    private Map<ArenaSetting, Object> currentSettings;
    private Map<ArenaSetting, Object> queue;
    private Map<Integer, RandomItemDistributor> randomItemDistributors;
    private Set<UUID> playerBlacklist;
    private Set<UUID> playerWhitelist;
    private Set<String> selectedMaps;
    
    public ArenaSettings() {
        this.currentSettings = new HashMap<>();
        this.queue = new HashMap<>();
        this.randomItemDistributors = new HashMap<>();
        this.selectedMaps = new HashSet<>();
        this.playerBlacklist = new TreeSet<>(comp);
        this.playerWhitelist = new TreeSet<>(comp);
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
        this.randomItemDistributors = new HashMap<>();
        for (RandomItemDistributor rd : other.randomItemDistributors.values()) {
            this.randomItemDistributors.put(rd.getIndex(), new RandomItemDistributor(rd, this));
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
        
        if (!randomItemDistributors.isEmpty()) {
            settings.put("random-item-distributors", new ArrayList<>(randomItemDistributors.values()));
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
        
        this.randomItemDistributors = new HashMap<>();
        
        if (settings.containsKey("random-item-distributors")) {
            for (RandomItemDistributor rd : (List<RandomItemDistributor>) settings.get("random-item-distributors")) {
                rd.setArenaSettings(this);
                this.randomItemDistributors.put(rd.getIndex(), rd);
            }
        }
        
        this.queue = new HashMap<>();
    }
    
    public Object get(ArenaSetting setting) {
        return currentSettings.getOrDefault(setting, setting.getDefaultValue());
    }
    
    public Object getWithQueue(ArenaSetting setting) {
        return queue.getOrDefault(setting, get(setting));
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
            res.put(setting, Pair.of(get(setting), value));
            set(setting, value);
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
     * @return an IMMUTABLE list of selected maps
     */
    public Set<String> getSelectedMaps() {
        return Collections.unmodifiableSet(selectedMaps);
    }
    
    /**
     * Set the selected maps for this arena to the ones
     * given by the list
     * 
     * @param maps
     */
    public void setSelectedMaps(Set<String> maps) {
        selectedMaps.clear();
        selectedMaps.addAll(maps);
    }
    
    /**
     * @return the currently selected random item distributor. If there is
     * none, the default one is created for that index. So make sure to check
     * the rank requirement first using {@link #getMaximumRandomItemDistributors(Player)}
     */
    public RandomItemDistributor getOrCreateRandomItemDistributor() {
        return getOrCreateRandomItemDistributor((int) get(ArenaSetting.DISTRIBUTOR_PRESET));
    }
    
    /**
     * @return the currently selected random item distributor. If there is
     * none, the default one is created for that index. So make sure to check
     * the rank requirement first using {@link #getMaximumRandomItemDistributors(Player)}
     */
    public RandomItemDistributor getOrCreateRandomItemDistributor(int index) {
        RandomItemDistributor distributor = getRandomItemDistributor(index);
        if (distributor == null) {
            distributor = getDefaultRandomItemDistributor(index);
            randomItemDistributors.put(index, distributor);
        }
        
        return distributor;
    }
    
    /**
     * @return the distributor at the specified index
     */
    public RandomItemDistributor getRandomItemDistributor(int index) {
        return randomItemDistributors.get(index);
    }
    
    /**
     * Gets the amount of random item distributors a player can have based
     * on the permission "umw.customarena.maxdistributors.[amount]"
     * 
     * @param player
     * @return
     */
    public int getMaximumRandomItemDistributors(Player player) {
        for (int i = 5; i >= 1; i--) {
            if (player.hasPermission("umw.customarena.maxdistributors." + i)) {
                return i;
            }
        }
        
        return 1;
    }
    
    /**
     * Get a random item distributor that correponds to the
     * classic Missile Wars items
     * 
     * @param settings
     * @return
     */
    private RandomItemDistributor getDefaultRandomItemDistributor(int index) {
        RandomItemDistributor dist = new RandomItemDistributor(this, index);
        dist.addItem(new RandomItem("tomahawk-1"));
        dist.addItem(new RandomItem("shieldbuster-1"));
        dist.addItem(new RandomItem("guardian-1"));
        dist.addItem(new RandomItem("juggernaut-1"));
        dist.addItem(new RandomItem("lightning-1"));
        dist.addItem(new RandomItem("fireball-1"));
        dist.addItem(new RandomItem("shield-2"));
        RandomItem arrows = new RandomItem("arrows-1");
        arrows.setAmount(3);
        dist.addItem(arrows);
        return dist;
    }
}
