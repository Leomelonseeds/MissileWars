package io.github.vhorvath2010.missilewars.utilities;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;

public class JSONManager {

    private MissileWarsPlugin plugin;

    private Map<UUID, JSONObject> playerCache;
    
    private JSONObject defaultJson;

    public JSONManager(MissileWarsPlugin plugin) {
        this.plugin = plugin;
        playerCache = new HashMap<>();
        periodicSave();
        
        // Find and parse the default json object
        String dir = MissileWarsPlugin.getPlugin().getDataFolder().toString();
        File json = new File(dir, "default.json");
        JSONParser parser = new JSONParser();
        try {
            defaultJson = (JSONObject) parser.parse(new FileReader(json));
        } catch (IOException | ParseException e) {
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
            JSONObject json;
            // Updated json from file
            JSONObject newjson = new JSONObject(/*read file here*/);
            newjson.put("Deck", "Sentinel");
            if (jsonString == null) {
                // For new players, use the default json
                json = newjson;
            } else {
                // Otherwise, perform an update (WIP)
                json = new JSONObject(jsonString);
            }
            playerCache.put(uuid, json);
        });
    }
    
    public void updatePreset(JSONObject json) {
        
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

}
