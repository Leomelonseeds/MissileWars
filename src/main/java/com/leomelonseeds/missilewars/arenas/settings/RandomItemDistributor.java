package com.leomelonseeds.missilewars.arenas.settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.NotNull;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.arenas.teams.TeamName;
import com.leomelonseeds.missilewars.decks.DeckManager;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.InventoryUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

public class RandomItemDistributor implements ConfigurationSerializable {
    
    // Unfortunately we do need to reference these settings
    private ArenaSettings settings;
    
    private int index;
    private String name;
    private LinkedHashMap<UUID, RandomItem> itemMap;
    private Map<String, Integer> enabledAbilities;
    private Set<String> addedIds;
    private List<RandomItem> curItems;
    private int totalWeight;
    private int timerTicks; // IN TICKS!!! 0 means disabled
    private Random random;
    private Component enabledAbilitiesMessage;
    
    public RandomItemDistributor(ArenaSettings settings, int index) {
        this.settings = settings;
        this.curItems = new ArrayList<>();
        this.itemMap = new LinkedHashMap<>();
        this.addedIds = new HashSet<>();
        this.enabledAbilities = new HashMap<>();
        this.index = index;
        this.name = "Preset " + (index + 1);
    }
    
    public RandomItemDistributor(RandomItemDistributor other, ArenaSettings settings) {
        this.name = other.name;
        this.index = other.index;
        this.settings = settings;
        this.curItems = new ArrayList<>();
        this.itemMap = new LinkedHashMap<>();
        this.addedIds = new HashSet<>();
        this.enabledAbilities = new HashMap<>(other.enabledAbilities);
        other.itemMap.forEach((uuid, ri) -> {
            RandomItem randomItem = new RandomItem(ri);
            this.itemMap.put(randomItem.getUUID(), randomItem);
            this.addedIds.add(randomItem.getId());
        });
    }
 
    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> distributor = new HashMap<>();
        
        distributor.put("name", name);
        distributor.put("index", index);
        
        distributor.put("items", new ArrayList<>(itemMap.values()));
        
        // Serialize abilities into a list of "string,value"
        if (!enabledAbilities.isEmpty()) {
            List<String> abilities = new ArrayList<>();
            for (Entry<String, Integer> e : enabledAbilities.entrySet()) {
                abilities.add(e.getKey() + "," + e.getValue());
            }
            distributor.put("enabled-abilities", abilities);
        }
        
        return distributor;
    }
    
    @SuppressWarnings("unchecked")
    public RandomItemDistributor(Map<String, Object> distributor) {
        this.curItems = new ArrayList<>();
        this.itemMap = new LinkedHashMap<>();
        this.addedIds = new HashSet<>();
        this.enabledAbilities = new HashMap<>();
        
        this.name = (String) distributor.get("name");
        this.index = (int) distributor.get("index");
        
        List<RandomItem> itemList = (List<RandomItem>) distributor.get("items");
        itemList.forEach(ri -> {
            itemMap.put(ri.getUUID(), ri);
            addedIds.add(ri.getId());
        });
        
        List<String> abilities = (List<String>) distributor.get("enabled-abilities");
        if (abilities != null) {
            for (String ability : abilities) {
                String[] args = ability.split(",");
                enabledAbilities.put(args[0], Integer.parseInt(args[1]));
            }
        }
    }
    
    /**
     * Starts the random item distribution according to the given timer.
     * The first item is given immediately.
     * 
     * @param redTeam A live view of the red team players
     * @param blueTeam A live view of the blue team players
     */
    public void startDistribution(Collection<MissileWarsPlayer> redTeam, Collection<MissileWarsPlayer> blueTeam) {
        timerTicks = ((int) settings.get(ArenaSetting.RANDOM_ITEM_DISTRIBUTION_TIMER)) * 20;
        random = new Random();
        giveNextItem(redTeam, blueTeam);
    }
    
    /**
     * Stops item distribution, if any is currently ongoing
     */
    public void stopDistribution() {
        timerTicks = 0;
        curItems.clear();
    }
    
    /**
     * Give the next item to both teams. If team balancing is enabled, and
     * the ratio of one team to another is greater than or equal to 3:2, the
     * smaller team will receive floor(g / l) items per player, and the
     * remaining g % l items will be randomly distributed to g % l players.
     * 
     * @param redTeam
     * @param blueTeam
     */
    private void giveNextItem(Collection<MissileWarsPlayer> redTeam, Collection<MissileWarsPlayer> blueTeam) {
        // If timer is 0, stop the distributor
        if (timerTicks == 0) {
            return;
        }
        
        // Make sure we can actually give the next item
        RandomItem nextItem = getNextItem();
        if (nextItem == null) {
            Bukkit.getLogger().warning("Stopping random item distributor because "
                    + "couldn't find next item for arena owned by " + settings.get(ArenaSetting.OWNER_NAME));
            return;
        }
        
        // Figure out team balancing
        int globalLimit = (int) settings.get(ArenaSetting.RANDOM_ITEM_INVENTORY_LIMIT);
        boolean uneven = false;
        do {
            if (!((boolean) settings.get(ArenaSetting.ENABLE_TEAM_BALANCING))) {
                break;
            }
            
            if (redTeam.size() == 0 || blueTeam.size() == 0) {
                break;
            }

            // Figure out if larger team size / smaller team size >= 3/2
            boolean redLarger = redTeam.size() > blueTeam.size();
            Collection<MissileWarsPlayer> less = redLarger ? blueTeam : redTeam;
            Collection<MissileWarsPlayer> more = redLarger ? redTeam : blueTeam;
            if ((double) less.size() * 3 > more.size() * 2) {
                break;
            }

            // A shuffled list of players in the lesser team
            // Give extra items to the first `remainder` players
            uneven = true;
            List<MissileWarsPlayer> giveExtra = new ArrayList<>(less);
            int lessAmount = more.size() / less.size();
            int limitMultiplier = (int) Math.ceil(more.size() / (double) less.size());
            int remainder = more.size() % less.size();
            Collections.shuffle(giveExtra);
            for (int i = 0; i < giveExtra.size(); i++) {
                int amount = lessAmount + (i < remainder ? 1 : 0);
                giveItemToPlayer(giveExtra.get(i).getMCPlayer(), nextItem, amount, globalLimit, limitMultiplier);
            }
            
            // Give the team with more players their share too
            more.forEach(mwp -> giveItemToPlayer(mwp.getMCPlayer(), nextItem, 1, globalLimit));
        } while (false);
        
        if (!uneven) {
            redTeam.forEach(mwp -> giveItemToPlayer(mwp.getMCPlayer(), nextItem, 1, globalLimit));
            blueTeam.forEach(mwp -> giveItemToPlayer(mwp.getMCPlayer(), nextItem, 1, globalLimit));
        }
        
        // Set XP bars if available
        if ((boolean) settings.get(ArenaSetting.RANDOM_ITEM_XP_TIMER)) {
            int timer = timerTicks / 20;
            setXPBars(timer, timer, redTeam, blueTeam);
        }
        
        ConfigUtils.schedule(timerTicks, () -> giveNextItem(redTeam, blueTeam));
    }
    
    /**
     * Sets players XP bar according to the timer. The XP is set
     * until the timer reaches 0, at which point this function
     * will need to be called again.
     * 
     * @param timer the whole timer
     * @param cur the current value of the timer
     * @param redTeam
     * @param blueTeam
     */
    private void setXPBars(int timer, int cur, Collection<MissileWarsPlayer> redTeam, Collection<MissileWarsPlayer> blueTeam) {
        if (timerTicks == 0 || cur == 0) {
            return;
        }
        
        float exp = cur / (float) timer;
        
        for (MissileWarsPlayer mwp : redTeam) {
            mwp.getMCPlayer().setExp(exp);
            mwp.getMCPlayer().setLevel(cur);
        }
        
        for (MissileWarsPlayer mwp : blueTeam) {
            mwp.getMCPlayer().setExp(exp);
            mwp.getMCPlayer().setLevel(cur);
        }
        
        ConfigUtils.schedule(20, () -> setXPBars(timer, cur - 1, redTeam, blueTeam));
    }

    private void giveItemToPlayer(Player player, RandomItem randomItem, int amount, int globalLimit) {
        giveItemToPlayer(player, randomItem, amount, globalLimit, 1);
    }
    
    /**
     * Give an item to a player. If the amount in the player's
     * inventory exceeds the max amount for the random item, the
     * item will not be given. Additionally, if all the items added
     * up (with 1 item == 1 * item.amount) exceeds the global
     * limit, the item will also not be given. 
     * 
     * @param player
     * @param randomItem
     * @param amount
     * @param globalLimit
     * @param limitMultiplier multiplies both random item limit and global limit
     */
    private void giveItemToPlayer(Player player, RandomItem randomItem, int amount, int globalLimit, int limitMultiplier) {
        globalLimit *= limitMultiplier;
        int maxAmount = randomItem.getMax() * randomItem.getAmount() * limitMultiplier;
        int giveAmount = amount * randomItem.getAmount();
        if (maxAmount == 0 && globalLimit == 0) {
            ItemStack item = randomItem.getItem();
            item.setAmount(giveAmount);
            player.give(item);
            ConfigUtils.sendConfigSound("pickup", player);
            return;
        }

        // Compute amounts of each registered item
        Map<UUID, Integer> amounts = new HashMap<>();
        for (ItemStack item : player.getInventory().getContents()) {
            String uuidString = InventoryUtils.getUUIDFromItem(item);
            if (uuidString == null) {
                continue;
            }
            
            UUID uuid = UUID.fromString(uuidString);
            amounts.put(uuid, amounts.getOrDefault(uuid, 0) + item.getAmount());
        }
        
        // If the item amount is less than the max amount but the next give
        // would make it exceed either the global limit or the current item
        // limit, we only add amount to make it to the limit.
        // There are 2 ways this could happen: 
        // 1. Item amount is less than max amount but more than max - give amount
        // 2. Inventory amount >= max amount && item amount % give amount > 0
        int itemAmount = 0;
        if (amounts.containsKey(randomItem.getUUID())) {
            itemAmount = amounts.get(randomItem.getUUID());
        }
        
        // Check item limit first
        if (maxAmount > 0) {
            if (itemAmount >= maxAmount) {
                String msg = ConfigUtils.getConfigText("messages.random-item-limit")
                    .replace("%item%", ConfigUtils.toPlain(randomItem.getModifiableItem().getItemMeta().displayName()));
                player.sendActionBar(ConfigUtils.toComponent(msg));
                ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                return;
            }
            
            if (itemAmount + giveAmount > maxAmount) {
                giveAmount = maxAmount - itemAmount;
            }
        }
        
        // Check global limit next
        if (globalLimit > 0) {
            int curGroups = 0;
            boolean exceeds = false;
            for (Entry<UUID, Integer> e : amounts.entrySet()) {
                RandomItem ri = itemMap.get(e.getKey());
                if (ri == null) {
                    continue;
                }
                
                int curGroup = e.getValue() / ri.getAmount();
                int remainder = e.getValue() % ri.getAmount();
                if (remainder * 2 > ri.getAmount()) {
                    curGroup++;
                }
                
                curGroups += curGroup;
                if (curGroups >= globalLimit) {
                    exceeds = true;
                    break;
                }
            }
            
            if (exceeds) {
                int remainder = itemAmount % randomItem.getAmount();
                if (remainder > 0) {
                    giveAmount = Math.min(giveAmount, randomItem.getAmount() - remainder);
                } else {
                    String msg = ConfigUtils.getConfigText("messages.random-item-global-limit")
                        .replace("%item%", ConfigUtils.toPlain(randomItem.getModifiableItem().getItemMeta().displayName()));
                    player.sendActionBar(ConfigUtils.toComponent(msg));
                    ConfigUtils.sendConfigSound("purchase-unsuccessful", player);
                    return;
                }
            } else if (amount + curGroups > globalLimit) {
                int curMaxAmount = (globalLimit - curGroups) * randomItem.getAmount();
                giveAmount = Math.min(giveAmount, curMaxAmount - itemAmount);
            }
        }
        
        // Finally give the item if we're sure that we can actually give some
        if (giveAmount > 0) {
            ItemStack item = randomItem.getItem();
            item.setAmount(giveAmount);
            player.give(item);
            ConfigUtils.sendConfigSound("pickup", player);
        }
    }
    
    /**
     * Gets next item in the random item distributor
     * 
     * @return
     */
    private RandomItem getNextItem() {
        // Just in case
        if (itemMap.isEmpty()) {
            return null;
        }
        
        // Readd all items to curItems if its empty (e.g. bag distribution or first time distributing)
        if (curItems.isEmpty()) {
            int weightSum = 0;
            for (RandomItem ri : itemMap.values()) {
                curItems.add(ri);
                weightSum += ri.getWeight();
            }
            totalWeight = weightSum;
        }
        
        // Thanks https://stackoverflow.com/questions/6737283/weighted-randomness-in-java
        int i = 0;
        for (double r = random.nextDouble() * totalWeight; i < curItems.size() - 1; i++) {
            r -= curItems.get(i).getWeight();
            if (r <= 0.0) {
                break;
            }
        }
        
        if ((boolean) settings.get(ArenaSetting.RANDOM_ITEM_BAG_DISTRIBUTION)) {
            RandomItem toGive = curItems.remove(i);
            totalWeight -= toGive.getWeight();
            return toGive;
        }
            
        return curItems.get(i);
    }
    
    /**
     * Equips player with the gear determined by item distributor
     * 
     * @param player
     * @param team
     */
    public void equipGear(Player player, TeamName team) {
        PlayerInventory pinv = player.getInventory();
        pinv.setChestplate(createColoredArmor(Material.LEATHER_CHESTPLATE, team));
        pinv.setLeggings(createColoredArmor(Material.LEATHER_LEGGINGS, team));
        pinv.setBoots(createColoredArmor(Material.LEATHER_BOOTS, team));
        
        // Bow
        DeckManager dm = MissileWarsPlugin.getPlugin().getDeckManager();
        ItemStack weapon = dm.createItem("Sentinel.weapon", 0);
        dm.addEnch(weapon, Enchantment.SHARPNESS, 4);
        dm.addEnch(weapon, Enchantment.FLAME, 1);
        weapon.removeEnchantment(Enchantment.UNBREAKING);
        ItemMeta weaponMeta = weapon.getItemMeta();
        weaponMeta.setUnbreakable(true);
        weapon.setItemMeta(weaponMeta);
        pinv.setItem(0, weapon);
    }
    
    private ItemStack createColoredArmor(Material type, TeamName team) {
        ItemStack item = new ItemStack(type);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(DyeColor.valueOf(team.toString().toUpperCase()).getColor());
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * @return if this distributor has at least one missile selected
     */
    public boolean containsMissile() {
        return itemMap.values().stream().anyMatch(ri -> ri.getModifiableItem().getType().toString().endsWith("SPAWN_EGG"));
    }
    
    /**
     * 
     * @param id
     * @return whether an item with this ID has already been added
     */
    public boolean contains(String id) {
        return addedIds.contains(id);
    }
    
    /**
     * @return an unmodifiable view of the items
     */
    public Collection<RandomItem> getRandomItems() {
        return Collections.unmodifiableCollection(itemMap.values());
    }
    
    /**
     * @param uuid
     * @return return random item from UUID
     */
    public RandomItem getRandomItem(UUID uuid) {
        return itemMap.get(uuid);
    }
    
    /**
     * If random item distribution is currently in progress, multiplies
     * the CURRENT timer by the given multiplier
     * 
     * Genuinely does nothing if distributor isn't running
     * 
     * @param multiplier
     */
    public void setTimerMultiplier(double multiplier) {
        timerTicks = (int) Math.round(multiplier * timerTicks);
    }
    
    public void addItem(RandomItem item) {
        this.itemMap.put(item.getUUID(), item);
        this.addedIds.add(item.getId());
    }
    
    public void removeItem(RandomItem item) {
        this.itemMap.remove(item.getUUID());
        this.addedIds.remove(item.getId());
    }
    
    public void clearItems() {
        this.itemMap.clear();
        this.addedIds.clear();
    }
    
    public int getAbilityLevel(String ability) {
        return enabledAbilities.getOrDefault(ability, 0);
    }
    
    public void setAbilityLevel(String ability, int level) {
        if (level == 0) {
            enabledAbilities.remove(ability);
        } else {
            enabledAbilities.put(ability, level);
        }
    }
    
    /**
     * @return an UNMODIFIABLE map of the current abilities
     */
    public Map<String, Integer> getAbilities() {
        return Collections.unmodifiableMap(enabledAbilities);
    }
    
    /**
     * Generate a message using the currently enabled abilities.
     * Fetch the message using {@link #getAbilitiesMessage()}
     */
    public void generateAbilitiesMessage() {
        if (enabledAbilities.isEmpty()) {
            enabledAbilitiesMessage = null;
            return;
        }
        
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            TextComponent.Builder message = Component.text()
                .append(ConfigUtils.toComponent(ConfigUtils.getConfigText("messages.enabled-abilities")));
            FileConfiguration itemsConfig = ConfigUtils.getConfigFile("items.yml");
            boolean first = true;
            for (String abilityPath : itemsConfig.getStringList("random-item-abilities")) {
                int level = getAbilityLevel(abilityPath.split("\\.")[2]);
                if (level == 0) {
                    continue;
                }
                
                if (!first) {
                    message.append(ConfigUtils.toComponent("&7, "));
                }
                
                // Create item to extract lore from
                // Set randomItem param to false to not show stuff about being upgradable
                ItemStack item = plugin.getDeckManager().createItem(abilityPath, level, null, null, true, null, false);
                String name = itemsConfig.getString(abilityPath + ".name") + " " + plugin.getDeckManager().roman(level);
                TextComponent component = Component.text()
                    .append(ConfigUtils.toComponent(name))
                    .hoverEvent(item)
                    .build();
                message.append(component);
                first = false;
            }
            
            enabledAbilitiesMessage = message.build();
        });
    }
    
    /**
     * @return the abilities messaage generated using {@link #generateAbilitiesMessage()}
     */
    public Component getAbilitiesMessage() {
        return enabledAbilitiesMessage;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public int getIndex() {
        return index;
    }
    
    protected void setArenaSettings(ArenaSettings settings) {
        this.settings = settings;
    }
}
