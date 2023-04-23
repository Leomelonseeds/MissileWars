package com.leomelonseeds.missilewars.decks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.leomelonseeds.missilewars.MissileWarsPlugin;
import com.leomelonseeds.missilewars.arenas.Arena;
import com.leomelonseeds.missilewars.teams.MissileWarsPlayer;
import com.leomelonseeds.missilewars.utilities.ConfigUtils;
import com.leomelonseeds.missilewars.utilities.JSONManager;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** A class representing a generic Deck. */
public class Deck {

    /** The Random object to control random pool item distribution for all Decks. */
    private static Random rand = new Random();
    /** The name of the Deck. */
    private String name;
    /** Items given at the start of the game. */
    private List<ItemStack> gear;
    /** Missiles given by this deck during gameplay. */
    private List<ItemStack> missiles;
    /** Utility items given by this deck during gameplay. */
    private List<ItemStack> utility;
    /** Make game more skill dependent */
    private List<ItemStack> missilePool;
    private List<ItemStack> utilityPool;
    
    private JSONManager jsonmanager;

    /**
     * Generate a deck from a given set of gear, utils, and missiles.
     *
     * @param name the name of the Deck
     * @param gear the gear for the Deck
     * @param pool the utilities, missiles and items given throughout the game
     */
    public Deck(String name, List<ItemStack> gear, List<ItemStack> missiles, List<ItemStack> utility) {
        this.name = name;
        this.gear = gear;
        this.missiles = missiles;
        this.utility = utility;
        missilePool = new ArrayList<>(missiles);
        utilityPool = new ArrayList<>(utility);
        
        jsonmanager = MissileWarsPlugin.getPlugin().getJSON();
    }
    
    public String getName() {
        return name;
    }

    /**
     * Get the gear for this {@link Deck}.
     *
     * @return the gear for this {@link Deck}.
     */
    public List<ItemStack> getGear() {
        return gear;
    }
    
    public List<ItemStack> getMissiles() {
        return missiles;
    }
    
    public List<ItemStack> getUtility() {
        return utility;
    }

    /**
     * Give this Deck's gear to a given player.
     *
     * @param player the player to give the gear to
     */
    public void giveGear(Player player) {
        for (ItemStack gearItem : gear) {
            if (gearItem.getType().toString().contains("BOOTS")) {
                player.getInventory().setBoots(gearItem);
            } else {
                player.getInventory().addItem(gearItem);
            }
        }
    }
    
    /**
     * Check if a given player with this deck has inventory space for more items.
     *
     * @param player the player to check space for
     * @param item the item that would be received
     * @return the amount of the item the player can receive. 0 means no more space
     */
    public int hasInventorySpace(Player player, ItemStack item) {
        int mlimit = MissileWarsPlugin.getPlugin().getConfig().getInt("inventory-limit");
        int ulimit = mlimit;
        if (jsonmanager.getAbility(player.getUniqueId(), "missilespec") >= 2) {
            mlimit++;
            ulimit--;
        } else if (jsonmanager.getAbility(player.getUniqueId(), "utilityspec") >= 2) {
            ulimit++;
            mlimit--;
        }

        // Check for missile limit. Since missiles always given out in 1s no need for complicated calcs
        List<ItemStack> inv = summarizedContents(player.getInventory());
        if (countPool(inv, "missile", item) >= mlimit) {
            return 0;
        }
        
        if (countPool(inv, "utility", item) >= ulimit) {
            // It is guaranteed by countPool that item is a utility (mult > 0)
            int mult = 0;
            for (ItemStack u : utility) {
                if (!u.isSimilar(item)) {
                    continue;
                }

                mult = u.getAmount();
                if (mult == 1) {
                    return 0; // If multiplier is 1 then we can't give extras of item
                }
                break;
            }
            
            for (ItemStack i : inv) {
                if (!i.isSimilar(item)) {
                    continue;
                } 
                
                // If remainder > 0, that means there is still some space for the item
                return i.getAmount() % mult;
            }
            return 0;
        }

        // If its not missile or utility being picked up, allow
        return item.getAmount();
    }

    /**
     * Give a random item from the pool to a given player if they have space.
     *
     * @param player the player to give the pool item too
     */
    public void givePoolItem(MissileWarsPlayer mwplayer, Boolean first) {
        Player player = mwplayer.getMCPlayer();
        MissileWarsPlugin plugin = MissileWarsPlugin.getPlugin();
        int missilespec = jsonmanager.getAbility(player.getUniqueId(), "missilespec");
        int utilityspec = jsonmanager.getAbility(player.getUniqueId(), "utilityspec");
        
        // Calculate chance based on ability
        double chance = 0.33;
        if (missilespec > 0) {
            chance += -1 * Double.valueOf(ConfigUtils.getItemValue("gpassive.missilespec", missilespec, "percentage") + "") / 100;
        } else if (utilityspec > 0) {
            chance += Double.valueOf(ConfigUtils.getItemValue("gpassive.utilityspec", utilityspec, "percentage") + "") / 100;
        }
        double rng = rand.nextDouble();
        List<ItemStack> toUse = rng < chance ? utilityPool : missilePool;
        
        // Check on first item, global passive
        if (first) {
            if (missilespec == 3) {
                toUse = missilePool;
            } else if (utilityspec == 3) {
                toUse = utilityPool;
            }
        }
        
        // Set poolitem, and restock if empty
        ItemStack poolItem = new ItemStack(toUse.remove(rand.nextInt(toUse.size())));
        if (toUse.isEmpty()) {
            if (toUse == missilePool) {
                missilePool = new ArrayList<>(missiles);
            } else {
                utilityPool = new ArrayList<>(utility);
            }
        }

        Arena arena = plugin.getArenaManager().getArena(player.getUniqueId());
        double toohigh = ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), "too-high");
        double toofar = ConfigUtils.getMapNumber(arena.getGamemode(), arena.getMapName(), "too-far");
        Location loc = player.getLocation();
        
        // Don't give item if they are out of bounds
        if (loc.getBlockY() > toohigh || loc.getBlockX() < toofar) {
            refuseItem(player, poolItem, "messages.out-of-bounds");
            return;
        }
        
        // Don't give item if their inventory space is full
        // If inventory full, we can safely assume its not the first item received
        int space = hasInventorySpace(player, poolItem);
        if (space == 0) {
            refuseItem(player, poolItem, "messages.inventory-limit-" + (rng < chance ? "utility" : "missile"));
            return;
        }
        poolItem.setAmount(space);
        
        // Check if can add to offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.isSimilar(poolItem)) {
            while (offhand.getAmount() < offhand.getMaxStackSize() && poolItem.getAmount() > 0) {
                offhand.add();
                poolItem.subtract();
            }
        }
        
        player.getInventory().addItem(poolItem);
    }
    
    /**
     * Refuse to give player an item, sending a message.
     * 
     * @param player
     * @param poolItem
     * @param messagePath
     */
    public void refuseItem(Player player, ItemStack poolItem, String messagePath) {
        String message = ConfigUtils.getConfigText(messagePath, player, null, null);
        String name;
        if (poolItem.getItemMeta().hasDisplayName()) {
            name = PlainTextComponentSerializer.plainText().serialize(poolItem.getItemMeta().displayName());
        } else {
            name = poolItem.getAmount() + "x " + StringUtils.capitalize(poolItem.getType().toString().toLowerCase());
        }
        player.sendMessage(message.replaceAll("%umw_item%", name));
    }
    
    // Returns 0 if toCompare isn't a type. Pass in summarizedContents please
    private int countPool(List<ItemStack> summed, String type, ItemStack toCompare) {
        List<ItemStack> pool = type == "missile" ? missiles : utility;
        int count = 0;
        boolean isType = false;
        for (ItemStack poolItem : pool) {
            if (!isType && toCompare.isSimilar(poolItem)) {
                isType = true;
            }
            
            for (ItemStack i : summed) {
                if (!poolItem.isSimilar(i)) {
                    continue;
                }
                
                count += Math.ceil((double) i.getAmount() / poolItem.getAmount());
                break;
            }
        }
        return isType ? count : 0;
    }
    
    // Returns a list of compiled items in the player inventory
    private List<ItemStack> summarizedContents(PlayerInventory inv) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack i : inv.getContents()) {
            if (i == null) {
                continue;
            }
            
            boolean added = false;
            for (ItemStack existing : result) {
                if (!existing.isSimilar(i)) {
                    continue;
                }
                
                existing.setAmount(existing.getAmount() + i.getAmount());
                added = true;
                break;
            }
            
            if (!added) {
                result.add(new ItemStack(i));
            }
        }
        return result;
    }
}
