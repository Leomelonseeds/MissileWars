package com.leomelonseeds.missilewars.decks;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;


public enum DeckStorage {
    VANGUARD(
        "Vanguard",
        Material.GOLDEN_BOOTS,
        Material.GOLDEN_SWORD,
        "§6§lVanguard §6⚡§f",
        "&6",
        Map.of(
            "sharpness", Enchantment.DAMAGE_ALL,
            "fire_aspect", Enchantment.FIRE_ASPECT
        ),
        Map.of("feather_falling", Enchantment.PROTECTION_FALL)
    ),
    SENTINEL(
        "Sentinel",
        Material.IRON_BOOTS,
        Material.BOW,
        "§b§lSentinel §b✟§f",
        "&b",
        Map.of(
            "sharpness", Enchantment.DAMAGE_ALL,
            "flame", Enchantment.ARROW_FIRE,
            "punch", Enchantment.ARROW_KNOCKBACK
        ),
        Map.of("fire_protection", Enchantment.PROTECTION_FIRE)
    ),
    BERSERKER(
        "Berserker",
        Material.DIAMOND_BOOTS,
        Material.CROSSBOW,
        "§c§lBerserker ☣§f",
        "&c",
        Map.of(
            "sharpness", Enchantment.DAMAGE_ALL,
            "multishot", Enchantment.MULTISHOT,
            "quick_charge", Enchantment.QUICK_CHARGE
        ),
        Map.of("blast_protection", Enchantment.PROTECTION_EXPLOSIONS)
    ),
    ARCHITECT(
        "Architect",
        Material.CHAINMAIL_BOOTS,
        Material.IRON_PICKAXE,
        "§2§lArchitect §2⚒§f",
        "&2",
        Map.of(
            "sharpness", Enchantment.DAMAGE_ALL,
            "efficiency", Enchantment.DIG_SPEED,
            "haste", Enchantment.LOOT_BONUS_BLOCKS    // Dummy enchant to represent haste!!
        ),
        Map.of("projectile_protection", Enchantment.PROTECTION_PROJECTILE)
    );
    
    private String name;
    private Material boots;
    private Material weapon;
    private String NPCName;
    private String color;
    private Map<String, Enchantment> weaponEnchants;
    private Map<String, Enchantment> bootEnchants;
    
    private DeckStorage(String name, Material boots, Material weapon, String NPCName, String color, Map<String, Enchantment> weaponEnchants, Map<String, Enchantment> bootEnchants) {
        this.name = name;
        this.boots = boots;
        this.weapon = weapon;
        this.NPCName = NPCName;
        this.color = color;
        this.weaponEnchants = weaponEnchants;
        this.bootEnchants = bootEnchants;
    }
    
    @Override
    public String toString() {
        return name;
    }

    /**
     * @return a copy of the deck's boots (unenchanted)
     */
    public ItemStack getBoots() {
        return new ItemStack(boots);
    }

    /**
     * @return a copy of the deck's weapon (unenchanted)
     */
    public ItemStack getWeapon() {
        return new ItemStack(weapon);
    }

    public String getNPCName() {
        return NPCName;
    }

    public String getColor() {
        return color;
    }

    public Map<String, Enchantment> getWeaponEnchants() {
        return weaponEnchants;
    }


    public Map<String, Enchantment> getBootEnchants() {
        return bootEnchants;
    }
    
    public static DeckStorage fromString(String type) {
        for (DeckStorage dn : DeckStorage.values()) {
            if (type.equalsIgnoreCase(dn.toString())) {
                return dn;
            }
        }
        
        return null;
    }
}
