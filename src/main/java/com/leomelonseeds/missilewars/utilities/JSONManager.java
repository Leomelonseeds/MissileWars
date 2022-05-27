package com.leomelonseeds.missilewars.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONException;
import org.json.JSONObject;

import com.leomelonseeds.missilewars.MissileWarsPlugin;

import github.scarsz.discordsrv.dependencies.commons.io.IOUtils;

public class JSONManager {

    private MissileWarsPlugin plugin;

    private Map<UUID, JSONObject> playerCache;
    
    private JSONObject defaultJson;
    private Map<String, JSONObject> defaultPresets;

    public JSONManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        playerCache = new HashMap<>();
        defaultPresets = new HashMap<>();
        periodicSave();
        
        // Find and parse the default json object
        String dir = MissileWarsPlugin.getPlugin().getDataFolder().toString();
        File json = new File(dir, "default.json");
        try {
            InputStream is = new FileInputStream(json);
            String jsonString = IOUtils.toString(is, "UTF-8");
            defaultJson = new JSONObject(jsonString);
            // Initialize default presets
            for (String deck : plugin.getDeckManager().getDecks()) {
                defaultPresets.put(deck, defaultJson.getJSONObject(deck).getJSONObject("defaultpreset"));
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
                // Recursively update json file
                updateJson(newJson, defaultJson);
                // Unlock everything for royal rank
                if (Bukkit.getPlayer(uuid).hasPermission("group.royal")) {
                    for (String key : newJson.keySet()) {
                        if (newJson.get(key) instanceof Boolean) {
                            newJson.put(key, true);
                        }
                    }
                }
                for (String deck : plugin.getDeckManager().getDecks()) {
                    JSONObject deckjson = newJson.getJSONObject(deck);
                    updateJson(deckjson, defaultJson.getJSONObject(deck));
                    if (Bukkit.getPlayer(uuid).hasPermission("group.royal")) {
                        for (String key : deckjson.keySet()) {
                            if (deckjson.get(key) instanceof Boolean) {
                                deckjson.put(key, true);
                            }
                        }
                    }
                    JSONObject defaultpreset = defaultPresets.get(deck);
                    for (String preset : plugin.getDeckManager().getPresets()) {
                        if (deckjson.has(preset)) {
                            JSONObject currentpreset = deckjson.getJSONObject(preset);
                            updateJson(currentpreset, defaultpreset);
                            updateJson(currentpreset.getJSONObject("missiles"), defaultpreset.getJSONObject("missiles"));
                            updateJson(currentpreset.getJSONObject("utility"), defaultpreset.getJSONObject("utility"));
                        } else {
                            newJson.getJSONObject(deck).put(preset, defaultpreset);
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
        for (String key : original.keySet()) {
            if (!(updated.has(key) || plugin.getDeckManager().getPresets().contains(key))) {
                original.remove(key);
            }
        }
    }
    
    /**
     * @return the default json file
     */
    public JSONObject getDefault() {
        return defaultJson;
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
     * Get default preset for a deck
     * 
     * @param deck
     */
    public JSONObject getDefaultPreset(String deck) {
        return defaultPresets.get(deck);
    }
}
