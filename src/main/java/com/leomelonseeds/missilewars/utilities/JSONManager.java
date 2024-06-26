package com.leomelonseeds.missilewars.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONException;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.decks.DeckStorage;
import com.leomelonseeds.missilewars.decks.Passive;

public class JSONManager {

    private MissileWarsPlugin plugin;

    private Map<UUID, JSONObject> playerCache;
    
    private JSONObject defaultJson;
    private Map<DeckStorage, JSONObject> defaultPresets;
    private String[] allPresets;

    public JSONManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        playerCache = new HashMap<>();
        defaultPresets = new HashMap<>();
        allPresets = new String[] {"A", "B", "C"};
        periodicSave();
        
        
        // Find and parse the default json object
        String dir = MissileWarsPlugin.getPlugin().getDataFolder().toString();
        File json = new File(dir, "default.json");
        try {
            InputStream is = new FileInputStream(json);
            String jsonString = IOUtils.toString(is, "UTF-8");
            defaultJson = new JSONObject(jsonString);
            // Initialize default presets
            for (DeckStorage ds : DeckStorage.values()) {
                String deck = ds.toString();
                defaultPresets.put(ds, defaultJson.getJSONObject(deck).getJSONObject("defaultpreset"));
                defaultJson.getJSONObject(deck).remove("defaultpreset");
            }
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.SEVERE, "Something went wrong parsing the default JSON file!");
        }
    }

    /**
     * Call when a previously joined player joins.
     * Updates and saves their deck data to the cache.
     *
     * @param uuid
     */
    public void loadPlayer(UUID uuid) {
        plugin.getSQL().getPlayerDeck(uuid, result -> {
            String jsonString = (String) result;
            JSONObject newJson = new JSONObject();
            if (jsonString != null) {
                newJson = new JSONObject(jsonString);
            }
            
            try {
                if (Bukkit.getPlayer(uuid) == null) {
                    return;
                }
                Player player = Bukkit.getPlayer(uuid);
                
                FileConfiguration itemConfig = ConfigUtils.getConfigFile("items.yml");
                // Recursively update json file
                updateJson(newJson, defaultJson);
                // Unlock everything for noble rank
                if (Bukkit.getPlayer(uuid).hasPermission("umw.unlockall")) {
                    for (String key : newJson.keySet()) {
                        if (newJson.get(key) instanceof Boolean) {
                            newJson.put(key, true);
                        }
                    }
                }
                for (DeckStorage ds : DeckStorage.values()) {
                    String deck = ds.toString();
                    JSONObject deckjson = newJson.getJSONObject(deck);
                    updateJson(deckjson, defaultJson.getJSONObject(deck));
                    if (player.hasPermission("umw.unlockall")) {
                        for (String key : deckjson.keySet()) {
                            if (deckjson.get(key) instanceof Boolean) {
                                deckjson.put(key, true);
                            }
                        }
                    }
                    JSONObject defaultpreset = getDefaultPreset(ds);
                    for (String preset : plugin.getDeckManager().getPresets()) {
                        // Only load presets player has permissions for to save on storage
                        boolean hasPreset = Bukkit.getPlayer(uuid).hasPermission("umw.preset." + preset.toLowerCase());
                        if (!deckjson.has(preset) && !hasPreset) {
                            continue;
                        }
                        
                        // Remove preset if it exists
                        if (!hasPreset) {
                            if (deckjson.has(preset)) {
                                deckjson.remove(preset);
                            }
                            if (newJson.getString("Preset").equals(preset)) {
                                newJson.put("Preset", "A");
                            }
                            continue;
                        }
                        
                        // Paste preset if deck doesn't contain it
                        if (!deckjson.has(preset)) {
                            newJson.getJSONObject(deck).put(preset, defaultpreset);
                        }
                        
                        // Update json and calculate amount of skillpoints player should have remaining
                        JSONObject currentpreset = deckjson.getJSONObject(preset);
                        updateJson(currentpreset, defaultpreset);
                        int finalsp = getMaxSkillpoints(uuid);
                        // Anything not in this array is an enchantment
                        List<String> all = List.of(new String[] {"missiles", "utility", "gpassive", "passive", "skillpoints", "layout"});
                        
                        // Calculate sp spent on missiles and utility
                        // Also updates their jsons
                        for (String s : new String[] {"missiles", "utility"}) {
                            JSONObject json = currentpreset.getJSONObject(s);
                            updateJson(json, defaultpreset.getJSONObject(s));
                            for (String k : json.keySet()) {
                                int maxLevel = plugin.getDeckManager().getMaxLevel(k);
                                int level = json.getInt(k);
                                if (level > maxLevel) {
                                    json.put(k, maxLevel);
                                    level = maxLevel;
                                }
                                while (level > 1) {
                                    int cost = itemConfig.getInt(k + "." + level + ".spcost");
                                    finalsp -= cost;
                                    level--;
                                }
                            }
                        }
                        
                        // Calculate sp spent on gpassives/passives, and delete if gpassive not exist
                        for (String ptype : new String[] {"gpassive", "passive"}) {
                            String path = ptype.equals("gpassive") ? ptype : deck + "." + ptype;
                            String passive = currentpreset.getJSONObject(ptype).getString("selected");
                            int passivelevel = currentpreset.getJSONObject(ptype).getInt("level");
                            Set<String> passives = itemConfig.getConfigurationSection(path).getKeys(false);
                            if (!passives.contains(passive)) {
                                currentpreset.getJSONObject(ptype).put("selected", "None");
                                currentpreset.getJSONObject(ptype).put("level", 0);
                            } else {
                                int maxLevel = plugin.getDeckManager().getMaxLevel(path + "." + passive);
                                if (passivelevel > maxLevel) {
                                    currentpreset.getJSONObject(ptype).put("level", maxLevel);
                                    passivelevel = maxLevel;
                                }
                                while (passivelevel > 0) {
                                    int cost = itemConfig.getInt(path + "." + passive + "." + passivelevel + ".spcost");
                                    finalsp -= cost;
                                    passivelevel--;
                                }
                            }
                        }
                        
                        // Calculate sp spent on enchantments
                        for (String k : currentpreset.keySet()) {
                            if (all.contains(k)) {
                                continue;
                            }
                            
                            int level = currentpreset.getInt(k);
                            int maxLevel = plugin.getDeckManager().getMaxLevel(deck + ".enchants." + k);
                            if (level > maxLevel) {
                                currentpreset.put(k, maxLevel);
                                level = maxLevel;
                            }
                            while (level > 0) {
                                int cost = itemConfig.getInt(deck + ".enchants." + k + "." + level + ".spcost");
                                finalsp -= cost;
                                level--;
                            }
                        }
                        
                        // Account for edge cases where finalsp may be below 0
                        if (finalsp < 0) {
                            String msg = ConfigUtils.getConfigText("messages.sp-negative", null, null, null);
                            msg = msg.replace("%deck%", deck);
                            msg = msg.replace("%preset%", preset);
                            player.sendMessage(ConfigUtils.toComponent(msg));
                            newJson.getJSONObject(deck).put(preset, defaultpreset);
                        } else {
                            currentpreset.put("skillpoints", finalsp);
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Bukkit.getLogger().log(Level.SEVERE, "Couldn't update the JSON for a player");
                newJson = defaultJson;
            }
            
            playerCache.put(uuid, newJson);
        });
    }
    
    /**
     * Updates a specified preset
     * 
     * @param original the original preset
     * @param updated an object within the defaultJson
     * @param preset
     */
    private void updateJson(JSONObject original, JSONObject updated) {
        // Add new keys if not exist
        for (String key : updated.keySet()) {
            if (!original.has(key)) {
                original.put(key, updated.get(key));
            }
        }
        // Remove keys not existing in default
        for (String key : JSONObject.getNames(original)) {
            if (!(updated.has(key) || Arrays.asList(allPresets).contains(key))) {
                original.remove(key);
            }
        }
    }

    /**
     * Call when a player leaves. Saves their deck
     * to the database and removes them from the cache
     *
     * @param uuid
     */
    public void savePlayer(UUID uuid) {
        if (playerCache.get(uuid) == null) {
            return;
        }
        plugin.getSQL().savePlayerDeck(uuid, playerCache.get(uuid).toString(), true);
        playerCache.remove(uuid);
    }

    /**
     * Gets the json representation of the current
     * players loadout. This should never return null.
     *
     * @param uuid
     */
    public JSONObject getPlayer(UUID uuid) {
        return playerCache.get(uuid);
    }
    
    /**
     * Gets the json representation of the currently selected preset
     *
     * @param uuid
     */
    public JSONObject getPlayerPreset(UUID uuid) {
        JSONObject basejson = getPlayer(uuid);
        String deck = basejson.getString("Deck");
        String preset = basejson.getString("Preset");
        return basejson.getJSONObject(deck).getJSONObject(preset);
    }

    /**
     * Saves the player deck back to the cache after
     * player finishes editing it.
     *
     * @param uuid
     * @param json
     */
    public void setPlayer(UUID uuid, JSONObject json) {
        playerCache.put(uuid, json);
    }

    /**
     * Saves the player cache to the database once every 10 minutes
     */
    private void periodicSave() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveAll(true);
            }
        }.runTaskTimer(plugin, 12000, 12000);
    }

    /**
     * Saves all currenly loaded jsons to the
     * database.
     *
     * @param async
     */
    public void saveAll(Boolean async) {
        for (Entry<UUID, JSONObject> entry : playerCache.entrySet()) {
            plugin.getSQL().savePlayerDeck(entry.getKey(), entry.getValue().toString(), async);
        }
    }
    
    /**
     * Returns a copy of the default preset for a deck
     * 
     * @param deck
     */
    public JSONObject getDefaultPreset(DeckStorage deck) {
        String json = defaultPresets.get(deck).toString();
        return new JSONObject(json);
    }
    
    /**
     * Returns a copy of the default preset for a deck
     * 
     * @param deck
     */
    public JSONObject getDefaultPreset(String deck) {
        DeckStorage ds = DeckStorage.fromString(deck);
        String json = defaultPresets.get(ds).toString();
        return new JSONObject(json);
    }
    
    /**
     * Get a passive and level from a player json.
     * 
     * @param json must be a preset json
     * @param type
     * @return right is 0 if passive does not exist or no passive selected
     */
    public Pair<Passive, Integer> getPassive(JSONObject json, Passive.Type type) {
        JSONObject typeJSON = json.getJSONObject(type.toString());
        Passive passive = Passive.fromString(typeJSON.getString("selected"));
        return Pair.of(passive, passive == null ? 0 : typeJSON.getInt("level"));
    }
    
    /**
     * Check if player has some ability selected. 
     * If so, return the level. If not, return 0.
     * 
     * @param player
     * @param ability
     * @return
     */
    public int getLevel(UUID uuid, Passive ability) {
        JSONObject json = getPlayerPreset(uuid);
        return getLevel(json, ability);
    }
    
    /**
     * Check if a json has some ability selected
     * 
     * @param json
     * @param ability
     * @return
     */
    public int getLevel(JSONObject json, Passive ability) {
        Pair<Passive, Integer> res = getPassive(json, ability.getType());
        return res.getLeft() == ability ? res.getRight() : 0;
    }
    
    /**
     * Get max skillpoints a player may have
     * 
     * @param uuid
     * @return
     */
    public int getMaxSkillpoints(UUID uuid) {
        return ConfigUtils.getConfigFile("items.yml").getInt("default-skillpoints");
    }
}
